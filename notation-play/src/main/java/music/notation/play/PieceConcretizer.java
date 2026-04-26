package music.notation.play;

import music.notation.duration.Duration;
import music.notation.event.ChordEvent;
import music.notation.event.Instrument;
import music.notation.phrase.*;
import music.notation.pitch.Pitch;
import music.notation.performance.*;
import music.notation.structure.Piece;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Phase 2 bridge: walks a {@link Piece} into a structural {@link Performance}.
 *
 * <p>Two-pass walker: pass 1 collects events in MIDI ticks (PPQ=480) per track
 * and tempo events piece-wide; pass 2 converts every tick to ms via a segment-
 * based tempo map and assembles the {@link Performance}.</p>
 *
 * <p>This is a parallel path to {@link PhraseInterpreter} during Phase 2; the
 * existing playback engine still owns rendering. Phase 3 will reroute
 * {@link MidiPlayer} through this concretizer.</p>
 *
 * <p>Doctrinal: dynamics, slur extension, and ornament rendering are
 * deliberately dropped. See {@code .docs/microtiming.md}.</p>
 */
public final class PieceConcretizer {

    private static final int PPQ = MidiMapper.TICKS_PER_QUARTER;

    private PieceConcretizer() {}

    public static Performance concretize(Piece piece) {
        if (piece.tracks().isEmpty()) {
            return Performance.of(Score.empty());
        }

        // Pass 1: walk each top-level track (and its aux tracks) into a TrackEvents
        // record in MIDI ticks, accumulating tempo events piece-wide.
        TempoTimeline tempoTimeline = new TempoTimeline(piece.tempo().bpm());
        List<TrackEvents> trackEventsList = new ArrayList<>();

        for (music.notation.structure.Track t : piece.tracks()) {
            TrackEvents te = walkTrack(t, piece, tempoTimeline);
            trackEventsList.add(te);
            int auxIndex = 1;
            for (music.notation.structure.Track aux : t.auxTracks()) {
                String auxName = t.name() + " Aux " + auxIndex++;
                TrackEvents auxTe = walkTrackNamed(aux, auxName, piece, tempoTimeline);
                trackEventsList.add(auxTe);
            }
        }

        // Pass 2: convert ticks → ms using the assembled tempo timeline.
        TempoMap tempoMap = tempoTimeline.build();

        // TempoTrack: sorted unique (tickMs, bpm) entries.
        List<TempoChange> tempoChanges = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : tempoTimeline.entries()) {
            tempoChanges.add(new TempoChange(tempoMap.tickToMs(e.getKey()), e.getValue()));
        }
        TempoTrack tempoTrack = new TempoTrack(tempoChanges);

        // Build performance Tracks and Instrumentation.
        List<Track> outTracks = new ArrayList<>();
        Map<TrackId, InstrumentControl> instrMap = new LinkedHashMap<>();
        Set<String> seenIds = new HashSet<>();

        for (TrackEvents te : trackEventsList) {
            TrackId id = new TrackId(te.name);
            if (!seenIds.add(te.name)) {
                throw new IllegalArgumentException(
                        "Duplicate TrackId in concretized Piece: " + te.name);
            }
            List<ConcreteNote> notes = new ArrayList<>(te.notes.size());
            if (te.kind == TrackKind.PITCHED) {
                for (PitchedTickNote n : te.pitched) {
                    long onMs = tempoMap.tickToMs(n.tick);
                    long offMs = tempoMap.tickToMs(n.tick + n.dur);
                    long durMs = Math.max(1, offMs - onMs);
                    notes.add(new PitchedNote(onMs, durMs, n.midi, n.tiedToNext));
                }
            } else {
                for (DrumTickNote n : te.drums) {
                    long onMs = tempoMap.tickToMs(n.tick);
                    long offMs = tempoMap.tickToMs(n.tick + n.dur);
                    long durMs = Math.max(1, offMs - onMs);
                    notes.add(new DrumNote(onMs, durMs, n.piece));
                }
            }
            outTracks.add(new Track(id, te.kind, notes));

            // Instrument: single entry at tick 0 from the track's default instrument.
            instrMap.put(id, InstrumentControl.constant(te.program));
        }

        Score score = new Score(outTracks);
        return new Performance(score, tempoTrack, new Instrumentation(instrMap),
                Articulations.empty());
    }

    // ── Per-track walker ────────────────────────────────────────────────────

    private static TrackEvents walkTrack(music.notation.structure.Track t, Piece piece,
                                         TempoTimeline tempoTimeline) {
        return walkTrackNamed(t, t.name(), piece, tempoTimeline);
    }

    private static TrackEvents walkTrackNamed(music.notation.structure.Track t, String name,
                                               Piece piece, TempoTimeline tempoTimeline) {
        TrackKind kind = (t.defaultInstrument() == Instrument.DRUM_KIT)
                ? TrackKind.DRUM : TrackKind.PITCHED;
        TrackWalker walker = new TrackWalker(tempoTimeline, piece.tempo().bpm());
        for (Phrase phrase : t.phrases()) {
            walker.interpret(phrase);
        }
        return new TrackEvents(name, kind, walker.pitched, walker.drums,
                t.defaultInstrument().program(), List.of());
    }

    // ── Pass-1 collected data ──────────────────────────────────────────────

    private static final class TrackEvents {
        final String name;
        final TrackKind kind;
        final List<PitchedTickNote> pitched;
        final List<DrumTickNote> drums;
        final int program;
        // Combined notes view kept for symmetry; per-kind lists drive emission.
        final List<Object> notes;

        TrackEvents(String name, TrackKind kind, List<PitchedTickNote> pitched,
                    List<DrumTickNote> drums, int program, List<Object> notes) {
            this.name = name;
            this.kind = kind;
            this.pitched = pitched;
            this.drums = drums;
            this.program = program;
            this.notes = notes;
        }
    }

    private record PitchedTickNote(long tick, long dur, int midi, boolean tiedToNext) {}
    private record DrumTickNote(long tick, long dur, int piece) {}

    // ── Tempo timeline collected piece-wide ────────────────────────────────

    /**
     * Piece-wide tempo timeline accumulated during the walk. Entries are keyed
     * by tick; later writes at the same tick win (matches PhraseInterpreter
     * ordering — last-written tempo at a position takes effect).
     */
    static final class TempoTimeline {
        private final int initialBpm;
        private final TreeMap<Long, Integer> byTick = new TreeMap<>();

        TempoTimeline(int initialBpm) {
            this.initialBpm = initialBpm;
            byTick.put(0L, initialBpm);
        }

        void add(long tick, int bpm) {
            byTick.put(tick, bpm);
        }

        Iterable<Map.Entry<Long, Integer>> entries() {
            return byTick.entrySet();
        }

        TempoMap build() {
            TempoMap map = new TempoMap(initialBpm, PPQ);
            for (Map.Entry<Long, Integer> e : byTick.entrySet()) {
                map.addChangeAtTick(e.getKey(), e.getValue());
            }
            return map;
        }
    }

    // ── Tempo map: tick → ms via segments ──────────────────────────────────

    /**
     * Segment-based tempo map. Built by adding tempo changes anchored at ticks;
     * each segment integrates ms forward at its bpm.
     */
    static final class TempoMap {
        private final int ppq;
        private record Segment(long tickAtChange, long msAtChange, int bpm) {}
        private final List<Segment> segments = new ArrayList<>();

        TempoMap(int initialBpm, int ppq) {
            this.ppq = ppq;
            segments.add(new Segment(0, 0, initialBpm));
        }

        /** Add a tempo change at a tick position. Replaces the initial segment if at tick 0. */
        void addChangeAtTick(long atTick, int bpm) {
            Segment prev = segments.get(segments.size() - 1);
            if (atTick <= prev.tickAtChange) {
                if (segments.size() == 1 && atTick == 0) {
                    segments.set(0, new Segment(0, 0, bpm));
                }
                return;
            }
            long msAtChange = prev.msAtChange + ticksToMsInSegment(prev, atTick - prev.tickAtChange);
            segments.add(new Segment(atTick, msAtChange, bpm));
        }

        long tickToMs(long tick) {
            Segment seg = segmentContainingTick(tick);
            return seg.msAtChange + ticksToMsInSegment(seg, tick - seg.tickAtChange);
        }

        private Segment segmentContainingTick(long tick) {
            Segment chosen = segments.get(0);
            for (Segment s : segments) {
                if (s.tickAtChange <= tick) chosen = s;
                else break;
            }
            return chosen;
        }

        private long ticksToMsInSegment(Segment s, long deltaTicks) {
            return Math.round((double) deltaTicks * 60_000.0 / (s.bpm * ppq));
        }
    }

    // ── Per-track walker — mirrors PhraseInterpreter walking arithmetic ────

    private static final class TrackWalker {
        private final TempoTimeline tempoTimeline;
        private final List<PitchedTickNote> pitched = new ArrayList<>();
        private final List<DrumTickNote> drums = new ArrayList<>();

        private long tick;
        private int currentBpm;
        private boolean elisionPending;
        private long elisionTrailingPad;
        private long elisionBarSize;
        private long transitionStartTick = -1;
        private int transitionStartBpm;

        TrackWalker(TempoTimeline tempoTimeline, int initialBpm) {
            this.tempoTimeline = tempoTimeline;
            this.currentBpm = initialBpm;
        }

        void interpret(Phrase phrase) {
            // ELISION handling (mirror PhraseInterpreter).
            if (elisionPending) {
                elisionPending = false;
                long leadingPad = computeLeadingPadding(phrase);
                long barSize = elisionBarSize;
                long filler = elisionTrailingPad + leadingPad - barSize;
                if (filler < 0) {
                    throw new IllegalStateException(String.format(
                            "Elision overlap: prev trailing padding (%d) + next leading (%d) = %d, "
                                    + "but bar size is %d — overlap %d ticks.",
                            elisionTrailingPad, leadingPad, elisionTrailingPad + leadingPad,
                            barSize, -filler));
                }
                tick += filler;
                tick -= leadingPad;
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
                    if (!mp.voices().isEmpty()) {
                        for (VoiceOverlay voice : mp.voices()) {
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
                        }
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
                        long dur = MidiMapper.toTicks(chord.duration());
                        for (Pitch p : chord.pitches()) {
                            int midi = MidiMapper.toMidiNote(p);
                            pitched.add(new PitchedTickNote(tick, dur, midi, false));
                        }
                        tick += dur;
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
                    int beforeP = pitched.size();
                    interpret(sp.source());
                    for (int i = beforeP; i < pitched.size(); i++) {
                        PitchedTickNote n = pitched.get(i);
                        pitched.set(i, new PitchedTickNote(
                                n.tick, n.dur, sp.shiftMidiNote(n.midi), n.tiedToNext));
                    }
                    // Boundary gap already applied by inner interpret(source).
                }
            }
        }

        private void interpretNode(PhraseNode node) {
            switch (node) {
                case SimplePitchNode n -> interpretSimple(n);
                case PolyPitchNode n -> interpretPoly(n);
                case RestNode r -> tick += MidiMapper.toTicks(r.duration());
                case DynamicNode d -> { /* doctrine: dynamics not modelled */ }
                case SubPhrase sp -> interpret(sp.phrase());
                case PercussionNote pn -> {
                    int midi = pn.sound().midiNote();
                    long dur = MidiMapper.toTicks(pn.duration());
                    drums.add(new DrumTickNote(tick, dur, midi));
                    tick += dur;
                }
                case PaddingNode p -> tick += MidiMapper.toTicks(p.duration());
                case SlurStart s -> { /* doctrine: no legato extension */ }
                case SlurEnd s -> { /* doctrine: no legato extension */ }
                case TempoChangeNode t -> {
                    currentBpm = t.bpm();
                    tempoTimeline.add(tick, t.bpm());
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

        private void interpretSimple(SimplePitchNode n) {
            long mainDur = emitGraceNotes(n.graceNotes(),
                    MidiMapper.toTicks(n.duration()), n.equalDivision(),
                    /*pitched*/ true, MidiMapper.toMidiNote(n.pitch()));
            int midi = MidiMapper.toMidiNote(n.pitch());
            pitched.add(new PitchedTickNote(tick, mainDur, midi, n.tiedToNext()));
            tick += mainDur;
        }

        private void interpretPoly(PolyPitchNode n) {
            // For grace notes on poly nodes, mirror PhraseInterpreter: graces use the
            // first pitch as a placeholder grace target (PhraseInterpreter accesses
            // graces' own pitches anyway, not the chord's).
            long mainDur = emitGraceNotes(n.graceNotes(),
                    MidiMapper.toTicks(n.duration()), n.equalDivision(),
                    /*pitched*/ true, MidiMapper.toMidiNote(n.pitches().get(0)));
            for (Pitch p : n.pitches()) {
                int midi = MidiMapper.toMidiNote(p);
                pitched.add(new PitchedTickNote(tick, mainDur, midi, n.tiedToNext()));
            }
            tick += mainDur;
        }

        /** Emit graces; advance tick; return remaining duration for the main note. */
        private long emitGraceNotes(List<GraceNote> graces, long dur, boolean equalDivision,
                                    boolean pitchedTarget, int unusedTarget) {
            if (graces.isEmpty()) return dur;
            int slots = graces.size() + 1;
            long graceDur = equalDivision ? dur / slots : MidiMapper.GRACE_NOTE_TICK;
            long graceTotal = 0;
            for (GraceNote g : graces) {
                int gMidi = MidiMapper.toMidiNote(g.pitch());
                pitched.add(new PitchedTickNote(tick, graceDur, gMidi, false));
                tick += graceDur;
                graceTotal += graceDur;
            }
            return Math.max(dur - graceTotal, MidiMapper.GRACE_NOTE_TICK);
        }

        private void applyBoundaryGap(PhraseMarking marking, Phrase justFinished) {
            switch (marking.connection()) {
                case BREATH -> tick += MidiMapper.TICKS_PER_QUARTER / 4;
                case CAESURA -> tick += MidiMapper.TICKS_PER_QUARTER;
                case ATTACCA -> {}
                case ELISION -> {
                    elisionPending = true;
                    elisionTrailingPad = trailingPaddingOf(justFinished);
                    elisionBarSize = barSizeOf(justFinished);
                    tick = Math.max(0, tick - elisionTrailingPad);
                }
            }
        }

        private void emitTempoTransition(long startTick, int startBpm,
                                         long endTick, int endBpm,
                                         TransitionMethod method) {
            long range = endTick - startTick;
            if (range <= 0) return;
            long stepTicks = MidiMapper.TICKS_PER_QUARTER;
            int steps = Math.max(1, (int) (range / stepTicks));
            for (int i = 0; i <= steps; i++) {
                long t = startTick + (range * i / steps);
                int bpm = switch (method) {
                    case LINEAR -> startBpm + (endBpm - startBpm) * i / steps;
                };
                tempoTimeline.add(t, bpm);
            }
        }
    }

    // ── Padding helpers (mirror PhraseInterpreter's static helpers) ────────

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
}
