package music.notation.play;

import music.notation.duration.Duration;
import music.notation.event.ChordEvent;
import music.notation.event.Instrument;
import music.notation.event.Ornament;
import music.notation.event.PercussionSound;
import music.notation.phrase.*;
import music.notation.pitch.Pitch;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class PhraseInterpreter {

    /** Small overlap for legato: previous note-off is delayed past next note-on. */
    private static final long LEGATO_OVERLAP_TICKS = MidiMapper.TICKS_PER_QUARTER / 48; // 10 ticks

    private long tick;
    private int velocity;
    private boolean inSlur;
    /** 0 = main line; 1..N = {@link VoiceOverlay} slots while rendering. */
    private int currentVoice;
    private boolean elisionPending;
    private long elisionTrailingPad;   // trailing padding of the just-finished phrase (ticks)
    private long elisionBarSize;       // size of prev phrase's last bar (ticks)
    private int currentBpm;
    private long transitionStartTick = -1;
    private int transitionStartBpm;
    private final List<PlayEvent> events = new ArrayList<>();

    public PhraseInterpreter(long startTick, int initialVelocity, int initialBpm) {
        this.tick = startTick;
        this.velocity = initialVelocity;
        this.currentBpm = initialBpm;
    }

    public void emitProgramChange(Instrument instrument) {
        events.add(new PlayEvent.ProgramChange(0, instrument.program()));
    }

    // Centralize note emission so every NoteOn/NoteOff carries `currentVoice`.
    private void addNoteOn(long t, int midi, int vel) {
        events.add(new PlayEvent.NoteOn(t, midi, vel, currentVoice));
    }

    private void addNoteOff(long t, int midi) {
        events.add(new PlayEvent.NoteOff(t, midi, currentVoice));
    }

    public void interpret(Phrase phrase) {
        // Elision: merge prev phrase's last bar with next phrase's first bar.
        //   - Rewind past prev's trailing padding (already done in applyBoundaryGap)
        //   - Insert a RestNode-equivalent gap if audible contents don't fill the bar
        //   - Skip next phrase's leading padding (rewind by it)
        //   - Throw if audible contents overlap
        if (elisionPending) {
            elisionPending = false;
            long leadingPad = computeLeadingPadding(phrase);
            long barSize = elisionBarSize;
            long filler = elisionTrailingPad + leadingPad - barSize;
            if (filler < 0) {
                throw new IllegalStateException(String.format(
                        "Elision overlap: prev trailing padding (%d ticks) + next leading padding (%d ticks) = %d, "
                                + "but bar size is %d — audible contents overlap by %d ticks. "
                                + "Shorten the ending bar's audible content or the pickup bar's audible content.",
                        elisionTrailingPad, leadingPad, elisionTrailingPad + leadingPad, barSize, -filler));
            }
            tick += filler;      // rest filler between ending audible and pickup audible
            tick -= leadingPad;  // skip pickup's leading padding (it will re-advance during interpret)
            elisionTrailingPad = 0;
            elisionBarSize = 0;
        }
        switch (phrase) {
            case MelodicPhrase mp -> {
                long startTick = tick;
                for (PhraseNode node : mp.nodes()) {
                    interpretNode(node);
                }
                long endTick = tick;
                // Render voice overlays in parallel: rewind to startTick, walk
                // overlay bars (absent slots fast-forward as silence), then
                // restore to endTick. Velocity/slur state is scoped per voice
                // so dynamics in one voice don't leak back into the main line.
                if (!mp.voices().isEmpty()) {
                    for (int vi = 0; vi < mp.voices().size(); vi++) {
                        VoiceOverlay voice = mp.voices().get(vi);
                        int savedVelocity = velocity;
                        boolean savedSlur = inSlur;
                        currentVoice = vi + 1; // main = 0, overlays 1..N
                        tick = startTick;
                        for (int i = 0; i < voice.size(); i++) {
                            Optional<Bar> slot = voice.at(i);
                            if (slot.isEmpty()) {
                                int barSize = mp.bars().get(i).expectedSixtyFourths();
                                tick += MidiMapper.toTicks(Duration.ofSixtyFourths(barSize));
                            } else {
                                for (PhraseNode node : slot.get().nodes()) {
                                    interpretNode(node);
                                }
                            }
                        }
                        velocity = savedVelocity;
                        inSlur = savedSlur;
                    }
                    currentVoice = 0;
                    tick = endTick;
                }
                applyBoundaryGap(mp.marking(), mp);
            }
            case RestPhrase rp -> {
                tick += MidiMapper.toTicks(rp.duration());
                applyBoundaryGap(rp.marking(), rp);
            }
            case VoidPhrase vp -> {
                tick += MidiMapper.toTicks(vp.duration());
                applyBoundaryGap(vp.marking(), vp);
            }
            case ChordPhrase cp -> {
                for (ChordEvent chord : cp.chords()) {
                    interpretChord(chord);
                }
                applyBoundaryGap(cp.marking(), cp);
            }
            case DrumPhrase dp -> {
                for (PhraseNode node : dp.nodes()) {
                    interpretNode(node);
                }
                applyBoundaryGap(dp.marking(), dp);
            }
            case LyricPhrase lp -> {
                for (LyricEvent e : lp.syllables()) {
                    tick += MidiMapper.toTicks(e.duration());
                }
                applyBoundaryGap(lp.marking(), lp);
            }
            case LayeredPhrase lp -> interpret(lp.resolve());
            case ShiftedPhrase sp -> {
                // Interpret the source phrase, then shift all MIDI notes emitted
                int before = events.size();
                interpret(sp.source());
                // Shift NoteOn/NoteOff pitches in the events just emitted
                for (int i = before; i < events.size(); i++) {
                    PlayEvent e = events.get(i);
                    switch (e) {
                        case PlayEvent.NoteOn on ->
                            events.set(i, new PlayEvent.NoteOn(
                                    on.tick(), sp.shiftMidiNote(on.midiNote()),
                                    on.velocity(), on.voice()));
                        case PlayEvent.NoteOff off ->
                            events.set(i, new PlayEvent.NoteOff(
                                    off.tick(), sp.shiftMidiNote(off.midiNote()), off.voice()));
                        default -> {} // ProgramChange etc. — leave as-is
                    }
                }
                // Boundary gap was already applied by interpret(source)
            }
        }
    }

    private void interpretNode(PhraseNode node) {
        switch (node) {
            case NoteNode n -> interpretNote(n);
            case RestNode r -> tick += MidiMapper.toTicks(r.duration());
            case DynamicNode d -> {
                int v = MidiMapper.toVelocity(d.dynamic());
                if (v >= 0) {
                    velocity = v;
                }
            }
            case SubPhrase sp -> interpret(sp.phrase());
            case PercussionNote pn -> {
                int midi = pn.sound().midiNote();
                long dur = MidiMapper.toTicks(pn.duration());
                emitNote(midi, dur);
            }
            case PaddingNode p -> tick += MidiMapper.toTicks(p.duration());
            case SlurStart s -> inSlur = true;
            case SlurEnd s -> inSlur = false;
            case TempoChangeNode t -> {
                currentBpm = t.bpm();
                events.add(new PlayEvent.TempoChange(tick, t.bpm()));
            }
            case TempoTransitionStartNode t -> {
                transitionStartTick = tick;
                transitionStartBpm = currentBpm;
            }
            case TempoTransitionEndNode t -> {
                if (transitionStartTick >= 0) {
                    emitTempoTransition(transitionStartTick, transitionStartBpm,
                            tick, t.targetBpm(), t.method());
                    transitionStartTick = -1;
                }
                currentBpm = t.targetBpm();
            }
        }
    }

    private void interpretNote(NoteNode n) {
        long dur = MidiMapper.toTicks(n.duration());

        // Grace notes precede the main note. In equal-division (tuplet) mode,
        // each grace and the main note each take dur / (graceCount + 1).
        // Otherwise each grace plays briefly and the main keeps the remainder.
        long mainDur = dur;
        if (!n.graceNotes().isEmpty()) {
            int slots = n.graceNotes().size() + 1;
            long graceDur = n.equalDivision() ? dur / slots : MidiMapper.GRACE_NOTE_TICK;
            long graceTotal = 0;
            for (GraceNote g : n.graceNotes()) {
                int gMidi = MidiMapper.toMidiNote(g.pitch());
                int gVel = g.accented() ? velocity : (int) (velocity * 0.7);
                addNoteOn(tick, gMidi, gVel);
                addNoteOff(tick + graceDur, gMidi);
                tick += graceDur;
                graceTotal += graceDur;
            }
            // Main note absorbs any leftover (division remainder)
            mainDur = Math.max(dur - graceTotal, MidiMapper.GRACE_NOTE_TICK);
        }

        if (n.isPolyphonic()) {
            // Poly: emit all pitches simultaneously (like a chord)
            for (Pitch p : n.pitches()) {
                int midi = MidiMapper.toMidiNote(p);
                addNoteOn(tick, midi, velocity);
                long offTick = tick + mainDur + (inSlur ? LEGATO_OVERLAP_TICKS : 0);
                addNoteOff(offTick, midi);
            }
            tick += mainDur;
        } else {
            int midi = MidiMapper.toMidiNote(n.pitch());
            if (n.ornament().isPresent()) {
                emitOrnament(n.ornament().get(), midi, mainDur);
            } else {
                emitNote(midi, mainDur);
            }
        }
    }

    private void emitNote(int midi, long dur) {
        addNoteOn(tick, midi, velocity);
        long offTick = tick + dur;
        if (inSlur) {
            // Delay note-off past the next note-on for smooth legato
            offTick += LEGATO_OVERLAP_TICKS;
        }
        addNoteOff(offTick, midi);
        tick += dur;
    }

    private void emitOrnament(Ornament ornament, int midi, long totalDur) {
        long sub = MidiMapper.ORNAMENT_TICK;
        int above = MidiMapper.stepAbove(midi);
        int below = MidiMapper.stepBelow(midi);

        switch (ornament) {
            case TRILL -> {
                // Alternate main note and note above for the full duration
                long remaining = totalDur;
                boolean onMain = true;
                while (remaining > 0) {
                    long d = Math.min(sub, remaining);
                    int note = onMain ? midi : above;
                    addNoteOn(tick, note, velocity);
                    addNoteOff(tick + d, note);
                    tick += d;
                    remaining -= d;
                    onMain = !onMain;
                }
            }
            case MORDENT -> {
                // note, note above, note — then sustain remainder
                long mordentTime = sub * 3;
                long sustain = totalDur - mordentTime;
                emitNote(midi, sub);
                emitNote(above, sub);
                emitNote(midi, sub + Math.max(0, sustain));
            }
            case LOWER_MORDENT -> {
                long mordentTime = sub * 3;
                long sustain = totalDur - mordentTime;
                emitNote(midi, sub);
                emitNote(below, sub);
                emitNote(midi, sub + Math.max(0, sustain));
            }
            case TURN -> {
                // above, main, below, main
                long turnTime = sub * 4;
                long sustain = totalDur - turnTime;
                emitNote(above, sub);
                emitNote(midi, sub);
                emitNote(below, sub);
                emitNote(midi, sub + Math.max(0, sustain));
            }
            case TREMOLO -> {
                // Rapid repetition of the same note
                long remaining = totalDur;
                while (remaining > 0) {
                    long d = Math.min(sub, remaining);
                    addNoteOn(tick, midi, velocity);
                    addNoteOff(tick + d, midi);
                    tick += d;
                    remaining -= d;
                }
            }
            case APPOGGIATURA -> {
                // Leaning note (note above) takes half the duration
                long lean = totalDur / 2;
                long main = totalDur - lean;
                emitNote(above, lean);
                emitNote(midi, main);
            }
            case ACCIACCATURA -> {
                // Very short crushed note before the main note
                long grace = MidiMapper.GRACE_NOTE_TICK;
                emitNote(above, grace);
                emitNote(midi, totalDur - grace);
            }
        }
    }

    private void interpretChord(ChordEvent chord) {
        long dur = MidiMapper.toTicks(chord.duration());
        for (Pitch p : chord.pitches()) {
            int midi = MidiMapper.toMidiNote(p);
            addNoteOn(tick, midi, velocity);
            addNoteOff(tick + dur, midi);
        }
        tick += dur;
    }

    private void applyBoundaryGap(PhraseMarking marking, Phrase justFinished) {
        switch (marking.connection()) {
            case BREATH  -> tick += MidiMapper.TICKS_PER_QUARTER / 4;
            case CAESURA -> tick += MidiMapper.TICKS_PER_QUARTER;
            case ATTACCA -> {} // no gap
            case ELISION -> {
                // Capture state needed by the next interpret() call:
                //  - trailing padding of this phrase's last bar (so we can rewind over it)
                //  - bar size (to detect overlap / compute rest filler)
                elisionPending = true;
                elisionTrailingPad = trailingPaddingOf(justFinished);
                elisionBarSize = barSizeOf(justFinished);
                tick = Math.max(0, tick - elisionTrailingPad);
            }
        }
    }

    // Thin ticks wrappers over PhraseMetrics (single source of truth).
    // 1 sixty-fourth = TICKS_PER_QUARTER / 16 ticks.

    private static long computeLeadingPadding(Phrase phrase) {
        return sfToTicks(PhraseMetrics.leadingPaddingSixtyFourths(phrase));
    }

    private static long trailingPaddingOf(Phrase phrase) {
        return sfToTicks(PhraseMetrics.trailingPaddingSixtyFourths(phrase));
    }

    private static long barSizeOf(Phrase phrase) {
        return sfToTicks(PhraseMetrics.lastBarSixtyFourths(phrase));
    }

    private static long sfToTicks(int sixtyFourths) {
        return (long) sixtyFourths * MidiMapper.TICKS_PER_QUARTER / 16;
    }

    private void emitTempoTransition(long startTick, int startBpm,
                                      long endTick, int endBpm,
                                      TransitionMethod method) {
        long range = endTick - startTick;
        if (range <= 0) return;

        // One step per quarter note
        long stepTicks = MidiMapper.TICKS_PER_QUARTER;
        int steps = Math.max(1, (int) (range / stepTicks));

        for (int i = 0; i <= steps; i++) {
            long t = startTick + (range * i / steps);
            int bpm = switch (method) {
                case LINEAR -> startBpm + (endBpm - startBpm) * i / steps;
            };
            events.add(new PlayEvent.TempoChange(t, bpm));
        }
    }

    public long currentTick() {
        return tick;
    }

    public List<PlayEvent> getEvents() {
        return List.copyOf(events);
    }
}
