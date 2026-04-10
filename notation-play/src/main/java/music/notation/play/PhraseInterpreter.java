package music.notation.play;

import music.notation.event.ChordEvent;
import music.notation.event.Instrument;
import music.notation.event.Ornament;
import music.notation.event.PercussionSound;
import music.notation.phrase.*;
import music.notation.pitch.Pitch;

import java.util.ArrayList;
import java.util.List;

public final class PhraseInterpreter {

    /** Small overlap for legato: previous note-off is delayed past next note-on. */
    private static final long LEGATO_OVERLAP_TICKS = MidiMapper.TICKS_PER_QUARTER / 48; // 10 ticks

    private long tick;
    private int velocity;
    private boolean inSlur;
    private final List<PlayEvent> events = new ArrayList<>();

    public PhraseInterpreter(long startTick, int initialVelocity) {
        this.tick = startTick;
        this.velocity = initialVelocity;
    }

    public void emitProgramChange(Instrument instrument) {
        events.add(new PlayEvent.ProgramChange(0, instrument.program()));
    }

    public void interpret(Phrase phrase) {
        switch (phrase) {
            case MelodicPhrase mp -> {
                for (PhraseNode node : mp.nodes()) {
                    interpretNode(node);
                }
                applyBoundaryGap(mp.marking());
            }
            case RestPhrase rp -> {
                tick += MidiMapper.toTicks(rp.duration());
                applyBoundaryGap(rp.marking());
            }
            case ChordPhrase cp -> {
                for (ChordEvent chord : cp.chords()) {
                    interpretChord(chord);
                }
                applyBoundaryGap(cp.marking());
            }
            case DrumPhrase dp -> {
                for (PhraseNode node : dp.nodes()) {
                    interpretNode(node);
                }
                applyBoundaryGap(dp.marking());
            }
            case LyricPhrase lp -> {
                for (LyricEvent e : lp.syllables()) {
                    tick += MidiMapper.toTicks(e.duration());
                }
                applyBoundaryGap(lp.marking());
            }
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
                                    on.tick(), sp.shiftMidiNote(on.midiNote()), on.velocity()));
                        case PlayEvent.NoteOff off ->
                            events.set(i, new PlayEvent.NoteOff(
                                    off.tick(), sp.shiftMidiNote(off.midiNote())));
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
            case GraceNote g -> interpretGraceNote(g);
            case PercussionNote pn -> {
                int midi = pn.sound().midiNote();
                long dur = MidiMapper.toTicks(pn.duration());
                emitNote(midi, dur);
            }
            case PaddingNode p -> tick += MidiMapper.toTicks(p.duration());
            case SlurStart s -> inSlur = true;
            case SlurEnd s -> inSlur = false;
        }
    }

    private void interpretNote(NoteNode n) {
        long dur = MidiMapper.toTicks(n.duration());

        if (n.isPolyphonic()) {
            // Poly: emit all pitches simultaneously (like a chord)
            for (Pitch p : n.pitches()) {
                int midi = MidiMapper.toMidiNote(p);
                events.add(new PlayEvent.NoteOn(tick, midi, velocity));
                long offTick = tick + dur + (inSlur ? LEGATO_OVERLAP_TICKS : 0);
                events.add(new PlayEvent.NoteOff(offTick, midi));
            }
            tick += dur;
        } else {
            int midi = MidiMapper.toMidiNote(n.pitch());
            if (n.ornament().isPresent()) {
                emitOrnament(n.ornament().get(), midi, dur);
            } else {
                emitNote(midi, dur);
            }
        }
    }

    private void emitNote(int midi, long dur) {
        events.add(new PlayEvent.NoteOn(tick, midi, velocity));
        long offTick = tick + dur;
        if (inSlur) {
            // Delay note-off past the next note-on for smooth legato
            offTick += LEGATO_OVERLAP_TICKS;
        }
        events.add(new PlayEvent.NoteOff(offTick, midi));
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
                    events.add(new PlayEvent.NoteOn(tick, note, velocity));
                    events.add(new PlayEvent.NoteOff(tick + d, note));
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
                    events.add(new PlayEvent.NoteOn(tick, midi, velocity));
                    events.add(new PlayEvent.NoteOff(tick + d, midi));
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

    private void interpretGraceNote(GraceNote g) {
        int midi = MidiMapper.toMidiNote(g.pitch());
        long dur = MidiMapper.GRACE_NOTE_TICK;
        int graceVelocity = g.accented() ? velocity : (int) (velocity * 0.7);
        events.add(new PlayEvent.NoteOn(tick, midi, graceVelocity));
        events.add(new PlayEvent.NoteOff(tick + dur, midi));
        tick += dur;
    }

    private void interpretChord(ChordEvent chord) {
        long dur = MidiMapper.toTicks(chord.duration());
        for (Pitch p : chord.pitches()) {
            int midi = MidiMapper.toMidiNote(p);
            events.add(new PlayEvent.NoteOn(tick, midi, velocity));
            events.add(new PlayEvent.NoteOff(tick + dur, midi));
        }
        tick += dur;
    }

    private void applyBoundaryGap(PhraseMarking marking) {
        long gap = switch (marking.connection()) {
            case BREATH -> MidiMapper.TICKS_PER_QUARTER / 4;
            case CAESURA -> MidiMapper.TICKS_PER_QUARTER;
            case ATTACCA -> 0;
            case ELISION -> 0;
        };
        tick += gap;
    }

    public long currentTick() {
        return tick;
    }

    public List<PlayEvent> getEvents() {
        return List.copyOf(events);
    }
}
