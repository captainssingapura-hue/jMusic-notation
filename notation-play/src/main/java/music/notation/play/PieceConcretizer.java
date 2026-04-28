package music.notation.play;

import music.notation.duration.Duration;
import music.notation.event.Instrument;
import music.notation.phrase.Bar;
import music.notation.phrase.DynamicNode;
import music.notation.phrase.GraceNote;
import music.notation.phrase.PaddingNode;
import music.notation.phrase.PercussionNote;
import music.notation.phrase.PhraseNode;
import music.notation.phrase.PolyPitchNode;
import music.notation.phrase.RestNode;
import music.notation.phrase.SimplePitchNode;
import music.notation.phrase.TempoChangeNode;
import music.notation.phrase.TempoTransitionEndNode;
import music.notation.phrase.TempoTransitionStartNode;
import music.notation.phrase.TransitionMethod;
import music.notation.pitch.Pitch;
import music.notation.performance.*;
import music.notation.structure.DrumTrack;
import music.notation.structure.MelodicTrack;
import music.notation.structure.Piece;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Walks a {@link Piece} into a structural {@link Performance}.
 *
 * <p>Phase 4d cutover: consumes {@link music.notation.structure.Track}
 * via the sealed interface, reading {@code track.bars()} directly. Any
 * elision merging has already happened inside the
 * {@link music.notation.phrase.BarPhrase} tree by the time bars surface
 * here — this walker has no boundary-gap or tick-rewind logic.</p>
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

        TempoTimeline tempoTimeline = new TempoTimeline(piece.tempo().bpm());
        List<TrackEvents> trackEventsList = new ArrayList<>();

        for (music.notation.structure.Track t : piece.tracks()) {
            trackEventsList.add(walkTrack(t, t.name(), piece, tempoTimeline));
            int auxIndex = 1;
            for (music.notation.structure.Track aux : t.auxTracks()) {
                String auxName = t.name() + " Aux " + auxIndex++;
                trackEventsList.add(walkTrack(aux, auxName, piece, tempoTimeline));
            }
        }

        TempoMap tempoMap = tempoTimeline.build();

        List<TempoChange> tempoChanges = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : tempoTimeline.entries()) {
            tempoChanges.add(new TempoChange(tempoMap.tickToMs(e.getKey()), e.getValue()));
        }
        TempoTrack tempoTrack = new TempoTrack(tempoChanges);

        List<Track> outTracks = new ArrayList<>();
        Map<TrackId, InstrumentControl> instrMap = new LinkedHashMap<>();
        Set<String> seenIds = new HashSet<>();

        for (TrackEvents te : trackEventsList) {
            TrackId id = new TrackId(te.name);
            if (!seenIds.add(te.name)) {
                throw new IllegalArgumentException(
                        "Duplicate TrackId in concretized Piece: " + te.name);
            }
            List<ConcreteNote> notes = new ArrayList<>();
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
            instrMap.put(id, InstrumentControl.constant(te.program));
        }

        Score score = new Score(outTracks);
        return new Performance(score, tempoTrack, new Instrumentation(instrMap),
                Articulations.empty());
    }

    // ── Per-track walker ────────────────────────────────────────────────────

    private static TrackEvents walkTrack(music.notation.structure.Track t, String name,
                                         Piece piece, TempoTimeline tempoTimeline) {
        TrackKind kind;
        int program;
        switch (t) {
            case MelodicTrack mt -> {
                kind = TrackKind.PITCHED;
                program = mt.defaultInstrument().program();
            }
            case DrumTrack dt -> {
                kind = TrackKind.DRUM;
                program = Instrument.DRUM_KIT.program();
            }
        }
        BarWalker walker = new BarWalker(tempoTimeline, piece.tempo().bpm());
        for (Bar bar : t.bars()) {
            for (PhraseNode node : bar.nodes()) {
                walker.interpretNode(node);
            }
        }
        return new TrackEvents(name, kind, walker.pitched, walker.drums, program);
    }

    // ── Pass-1 collected data ──────────────────────────────────────────────

    private static final class TrackEvents {
        final String name;
        final TrackKind kind;
        final List<PitchedTickNote> pitched;
        final List<DrumTickNote> drums;
        final int program;

        TrackEvents(String name, TrackKind kind, List<PitchedTickNote> pitched,
                    List<DrumTickNote> drums, int program) {
            this.name = name;
            this.kind = kind;
            this.pitched = pitched;
            this.drums = drums;
            this.program = program;
        }
    }

    private record PitchedTickNote(long tick, long dur, int midi, boolean tiedToNext) {}
    private record DrumTickNote(long tick, long dur, int piece) {}

    // ── Tempo timeline collected piece-wide ────────────────────────────────

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

    static final class TempoMap {
        private final int ppq;
        private record Segment(long tickAtChange, long msAtChange, int bpm) {}
        private final List<Segment> segments = new ArrayList<>();

        TempoMap(int initialBpm, int ppq) {
            this.ppq = ppq;
            segments.add(new Segment(0, 0, initialBpm));
        }

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

    // ── Bar-node walker ─────────────────────────────────────────────────────

    private static final class BarWalker {
        private final TempoTimeline tempoTimeline;
        private final List<PitchedTickNote> pitched = new ArrayList<>();
        private final List<DrumTickNote> drums = new ArrayList<>();

        private long tick;
        private int currentBpm;
        private long transitionStartTick = -1;
        private int transitionStartBpm;

        BarWalker(TempoTimeline tempoTimeline, int initialBpm) {
            this.tempoTimeline = tempoTimeline;
            this.currentBpm = initialBpm;
        }

        void interpretNode(PhraseNode node) {
            switch (node) {
                case SimplePitchNode n -> interpretSimple(n);
                case PolyPitchNode n -> interpretPoly(n);
                case RestNode r -> tick += MidiMapper.toTicks(r.duration());
                case DynamicNode d -> { /* dynamics not modelled */ }
                case music.notation.phrase.SubPhrase sp -> { /* dropped: legacy nesting */ }
                case PercussionNote pn -> {
                    int midi = pn.sound().midiNote();
                    long dur = MidiMapper.toTicks(pn.duration());
                    drums.add(new DrumTickNote(tick, dur, midi));
                    tick += dur;
                }
                case PaddingNode p -> tick += MidiMapper.toTicks(p.duration());
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
                    MidiMapper.toTicks(n.duration()), n.equalDivision());
            int midi = MidiMapper.toMidiNote(n.pitch());
            pitched.add(new PitchedTickNote(tick, mainDur, midi, n.tiedToNext()));
            tick += mainDur;
        }

        private void interpretPoly(PolyPitchNode n) {
            long mainDur = emitGraceNotes(n.graceNotes(),
                    MidiMapper.toTicks(n.duration()), n.equalDivision());
            for (Pitch p : n.pitches()) {
                int midi = MidiMapper.toMidiNote(p);
                pitched.add(new PitchedTickNote(tick, mainDur, midi, n.tiedToNext()));
            }
            tick += mainDur;
        }

        private long emitGraceNotes(List<GraceNote> graces, long dur, boolean equalDivision) {
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
}
