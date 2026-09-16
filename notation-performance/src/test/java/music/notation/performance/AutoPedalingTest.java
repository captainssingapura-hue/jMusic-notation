package music.notation.performance;

import music.notation.duration.Duration;
import music.notation.expressivity.*;

import music.notation.structure.TimeSignature;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Post-ms→Duration migration: pedal positions are pure musical
 * positions (fractions of a whole note). Tempo never enters the
 * heuristic — the old 120 bpm ms literals map 1:1 onto quarter notes
 * (500 ms = 1/4, 2000 ms = one 4/4 bar = 1/1).
 */
class AutoPedalingTest {

    /** {@code n} quarter notes. */
    private static Duration q(long n) { return Duration.of(n, 4); }

    /** {@code n} sixteenth notes. */
    private static Duration s(long n) { return Duration.of(n, 16); }

    private static void assertAt(Duration expected, PedalChange actual) {
        assertTrue(expected.equalsDuration(actual.at()),
                "expected " + expected + " but was " + actual.at());
    }

    private static Performance pieceWithDuration(Duration lastNoteEnd) {
        return pieceWithDuration(lastNoteEnd, TempoTrack.constant(120));
    }

    private static Performance pieceWithDuration(Duration lastNoteEnd, TempoTrack tempos) {
        var note = new PitchedNote(Duration.zero(), lastNoteEnd, 60);
        var track = new Track(new TrackId("Piano"), TrackKind.PITCHED, List.of(note));
        return new Performance(
                new Score(List.of(track)),
                tempos,
                Instrumentation.empty(),
                Volume.empty(),
                Articulations.empty(),
                Pedaling.empty());
    }

    @Test
    void emptyPerformanceYieldsEmptyPedaling() {
        var perf = new Performance(
                Score.empty(), TempoTrack.empty(),
                Instrumentation.empty(), Volume.empty(), Articulations.empty(),
                Pedaling.empty());
        assertTrue(AutoPedaling.generate(perf, new TimeSignature(4, 4))
                .byTrack().isEmpty());
    }

    @Test
    void nullTimeSigYieldsEmpty() {
        assertTrue(AutoPedaling.generate(pieceWithDuration(q(4)), null)
                .byTrack().isEmpty());
    }

    @Test
    void fourFourGivesOneWholeNotePerBar() {
        // 1 bar of 4/4 = 4 quarters = one whole. A 3-bar piece (12 quarters)
        // should produce: DOWN @ 0, CHANGE @ 4q, CHANGE @ 8q, UP @ 12q.
        var perf = pieceWithDuration(q(12));
        var ped = AutoPedaling.generate(perf, new TimeSignature(4, 4));
        assertEquals(1, ped.byTrack().size());

        var changes = ped.byTrack().values().iterator().next().changes();
        assertEquals(4, changes.size());
        assertEquals(PedalState.DOWN,   changes.get(0).state());
        assertAt(Duration.zero(),       changes.get(0));
        assertEquals(PedalState.CHANGE, changes.get(1).state());
        assertAt(q(4),                  changes.get(1));
        assertEquals(PedalState.CHANGE, changes.get(2).state());
        assertAt(q(8),                  changes.get(2));
        assertEquals(PedalState.UP,     changes.get(3).state());
        assertAt(q(12),                 changes.get(3));
    }

    @Test
    void threeFourGivesShorterBars() {
        // 3/4: 1 bar = 3 quarters. A 9-quarter piece → 3 bars.
        var perf = pieceWithDuration(q(9));
        var changes = AutoPedaling.generate(perf, new TimeSignature(3, 4))
                .byTrack().values().iterator().next().changes();
        assertEquals(4, changes.size());   // DOWN + 2 CHANGEs + UP
        assertAt(q(3), changes.get(1));
        assertAt(q(6), changes.get(2));
        assertAt(q(9), changes.get(3));
    }

    @Test
    void drumTracksAreSkipped() {
        var pitchedNote = new PitchedNote(Duration.zero(), q(4), 60);
        var drumNote = new DrumNote(Duration.zero(), Duration.of(1, 20), 36);
        var pitched = new Track(new TrackId("Piano"), TrackKind.PITCHED, List.of(pitchedNote));
        var drums   = new Track(new TrackId("Drums"), TrackKind.DRUM,    List.of(drumNote));
        var perf = new Performance(
                new Score(List.of(pitched, drums)),
                TempoTrack.constant(120),
                Instrumentation.empty(), Volume.empty(),
                Articulations.empty(), Pedaling.empty());

        var ped = AutoPedaling.generate(perf, new TimeSignature(4, 4));
        assertEquals(1, ped.byTrack().size(), "only pitched tracks get auto-pedal");
        assertTrue(ped.byTrack().containsKey(new TrackId("Piano")));
    }

    @Test
    void tempoDoesNotShiftMusicalBarBoundaries() {
        // Under the musical model the heuristic is tempo-independent:
        // a 3-bar 4/4 piece at 60 bpm has bar boundaries at exactly the
        // same musical positions as at 120 bpm. Only the wall-clock
        // rendering (via TimeMapper) stretches: 60 bpm → 4000 ms/bar.
        var perf = pieceWithDuration(q(12), TempoTrack.constant(60));
        var changes = AutoPedaling.generate(perf, new TimeSignature(4, 4))
                .byTrack().values().iterator().next().changes();
        assertEquals(4, changes.size());
        assertAt(q(4),  changes.get(1));
        assertAt(q(8),  changes.get(2));
        assertAt(q(12), changes.get(3));
        assertEquals(PedalState.UP, changes.get(3).state());

        var mapper = new TimeMapper(perf.tempo());
        assertEquals(4000L,  mapper.toMs(changes.get(1).at()));
        assertEquals(8000L,  mapper.toMs(changes.get(2).at()));
        assertEquals(12000L, mapper.toMs(changes.get(3).at()));
    }

    @Test
    void bassChangeAddsMidBarChangeEvent() {
        // 4/4, 1 bar = 4 quarters. Two-bar piece (8q total)
        // with bass moving inside each bar:
        //   t=0  : C2 (36) + C5 (72)
        //   t=2q : G2 (43)            ← bass C→G mid-bar 1
        //   t=4q : C2 (36)            ← bar boundary; also bass G→C
        //   t=6q : F2 (41)            ← bass C→F mid-bar 2
        // Expect: DOWN @ 0, CHANGE @ 2q, CHANGE @ 4q, CHANGE @ 6q, UP @ 8q.
        var bass = new Track(new TrackId("LH"), TrackKind.PITCHED, List.of(
                new PitchedNote(q(0), q(2), 36),
                new PitchedNote(q(2), q(2), 43),
                new PitchedNote(q(4), q(2), 36),
                new PitchedNote(q(6), q(2), 41)));
        var treble = new Track(new TrackId("RH"), TrackKind.PITCHED, List.of(
                new PitchedNote(q(0), q(8), 72)));
        var perf = new Performance(
                new Score(List.of(bass, treble)),
                TempoTrack.constant(120),
                Instrumentation.empty(), Volume.empty(),
                Articulations.empty(), Pedaling.empty());

        var changes = AutoPedaling.generate(perf, new TimeSignature(4, 4))
                .byTrack().get(new TrackId("LH")).changes();
        assertEquals(5, changes.size());
        assertEquals(PedalState.DOWN,   changes.get(0).state());
        assertAt(q(0),                  changes.get(0));
        assertEquals(PedalState.CHANGE, changes.get(1).state());
        assertAt(q(2),                  changes.get(1));
        assertEquals(PedalState.CHANGE, changes.get(2).state());
        assertAt(q(4),                  changes.get(2));
        assertEquals(PedalState.CHANGE, changes.get(3).state());
        assertAt(q(6),                  changes.get(3));
        assertEquals(PedalState.UP,     changes.get(4).state());
        assertAt(q(8),                  changes.get(4));
    }

    @Test
    void trebleOnlyMelodyDoesNotOverPedal() {
        // Solo melody entirely above middle C → bass detector finds
        // nothing → only bar-boundary CHANGEs survive.
        var melody = new Track(new TrackId("RH"), TrackKind.PITCHED, List.of(
                new PitchedNote(q(0), q(1), 72),  // C5
                new PitchedNote(q(1), q(1), 74),  // D5
                new PitchedNote(q(2), q(1), 76),  // E5
                new PitchedNote(q(3), q(1), 77),  // F5
                new PitchedNote(q(4), q(1), 79),  // G5
                new PitchedNote(q(5), q(1), 81),  // A5
                new PitchedNote(q(6), q(1), 83),  // B5
                new PitchedNote(q(7), q(1), 84))); // C6
        var perf = new Performance(
                new Score(List.of(melody)),
                TempoTrack.constant(120),
                Instrumentation.empty(), Volume.empty(),
                Articulations.empty(), Pedaling.empty());

        // Plain bar-only output: DOWN @ 0, CHANGE @ 4q, UP @ 8q.
        // (No mid-bar CHANGEs because no notes are below MIDI 60.)
        var changes = AutoPedaling.generate(perf, new TimeSignature(4, 4))
                .byTrack().values().iterator().next().changes();
        assertEquals(3, changes.size());
        assertEquals(PedalState.DOWN,   changes.get(0).state());
        assertEquals(PedalState.CHANGE, changes.get(1).state());
        assertAt(q(4),                  changes.get(1));
        assertEquals(PedalState.UP,     changes.get(2).state());
        assertAt(q(8),                  changes.get(2));
    }

    @Test
    void repeatedBassNoteDoesNotEmitChange() {
        // Bass plays the same C2 eight times — same harmony, no
        // mid-bar CHANGEs. Only the bar boundary contributes.
        var bass = new Track(new TrackId("LH"), TrackKind.PITCHED, List.of(
                new PitchedNote(q(0), q(1), 36),
                new PitchedNote(q(1), q(1), 36),
                new PitchedNote(q(2), q(1), 36),
                new PitchedNote(q(3), q(1), 36),
                new PitchedNote(q(4), q(1), 36),
                new PitchedNote(q(5), q(1), 36),
                new PitchedNote(q(6), q(1), 36),
                new PitchedNote(q(7), q(1), 36)));
        var perf = new Performance(
                new Score(List.of(bass)),
                TempoTrack.constant(120),
                Instrumentation.empty(), Volume.empty(),
                Articulations.empty(), Pedaling.empty());

        var changes = AutoPedaling.generate(perf, new TimeSignature(4, 4))
                .byTrack().values().iterator().next().changes();
        // DOWN @ 0, CHANGE @ 4q (bar), UP @ 8q.
        assertEquals(3, changes.size());
        assertAt(q(4), changes.get(1));
    }

    @Test
    void bassChangeNearBarBoundaryIsDeduped() {
        // Bass one 32nd (1/32) before the bar boundary at 4q — the
        // 1/16 MIN_GAP should drop it; only the bar boundary survives.
        Duration justBeforeBar = q(4).minus(Duration.of(1, 32));
        var bass = new Track(new TrackId("LH"), TrackKind.PITCHED, List.of(
                new PitchedNote(q(0),          q(2), 36),
                new PitchedNote(justBeforeBar, q(2), 43),  // bass C→G but too close to bar 2
                new PitchedNote(q(6),          q(2), 41))); // bass G→F mid-bar 2 — kept
        var perf = new Performance(
                new Score(List.of(bass)),
                TempoTrack.constant(120),
                Instrumentation.empty(), Volume.empty(),
                Articulations.empty(), Pedaling.empty());

        var changes = AutoPedaling.generate(perf, new TimeSignature(4, 4))
                .byTrack().values().iterator().next().changes();
        // DOWN @ 0, CHANGE @ 4q (bar; bass just before dropped),
        // CHANGE @ 6q (bass G→F mid-bar 2), UP @ 8q.
        assertEquals(4, changes.size());
        assertAt(q(0), changes.get(0));
        assertAt(q(4), changes.get(1));
        assertAt(q(6), changes.get(2));
        assertAt(q(8), changes.get(3));
    }

    @Test
    void closeBassChangesAreDebouncedAgainstEachOther() {
        // Chromatic bass walk: notes one 32nd apart. The first one starts
        // a chord; subsequent ones are within MIN_GAP (1/16) of the
        // previous emitted change → dropped. Bar boundary remains.
        Duration t = Duration.of(1, 32);
        var bass = new Track(new TrackId("LH"), TrackKind.PITCHED, List.of(
                new PitchedNote(t.times(0), t, 36),  // C2
                new PitchedNote(t.times(1), t, 37),  // C#2 — bass change candidate @ 1/32
                new PitchedNote(t.times(2), t, 38),  // D2  — candidate @ 2/32
                new PitchedNote(t.times(3), t, 39),  // D#2 — candidate @ 3/32
                new PitchedNote(q(7), q(1), 39))); // same pitch as prev bass → no candidate
        var perf = new Performance(
                new Score(List.of(bass)),
                TempoTrack.constant(120),
                Instrumentation.empty(), Volume.empty(),
                Articulations.empty(), Pedaling.empty());

        var changes = AutoPedaling.generate(perf, new TimeSignature(4, 4))
                .byTrack().values().iterator().next().changes();
        // Bass@1/32 is within 1/16 of DOWN@0 → DROPPED.
        // Bass@2/32 → gap to DOWN@0 is exactly 1/16, NOT < 1/16 → kept.
        // Bass@3/32 → 3/32-2/32 = 1/32 < 1/16 → dropped.
        // Plus bar boundary at 4q, UP at 8q.
        // So we expect: DOWN@0, CHANGE@1/16, CHANGE@4q, UP@8q.
        assertEquals(4, changes.size());
        assertAt(q(0), changes.get(0));
        assertAt(s(1), changes.get(1));
        assertAt(q(4), changes.get(2));
        assertAt(q(8), changes.get(3));
    }

    @Test
    void tempoChangeMidPieceDoesNotShiftBarBoundary() {
        // 4/4. Bar 1 at 60 bpm, then tempo doubles to 120 bpm at bar 2.
        // Musically, bar boundaries stay at 4q and 8q regardless — only
        // the wall-clock rendering differs: bar 1 takes 4000 ms, bar 2
        // 2000 ms. Piece length 2.5 bars (10 quarters) → UP @ 10q.
        var tempos = new TempoTrack(List.of(
                new TempoChange(q(0), 60),
                new TempoChange(q(4), 120)));
        var perf = pieceWithDuration(q(10), tempos);
        var changes = AutoPedaling.generate(perf, new TimeSignature(4, 4))
                .byTrack().values().iterator().next().changes();
        // Expect: DOWN @ 0, CHANGE @ 4q, CHANGE @ 8q, UP @ 10q
        assertEquals(4, changes.size());
        assertEquals(PedalState.DOWN,   changes.get(0).state());
        assertAt(q(0),                  changes.get(0));
        assertEquals(PedalState.CHANGE, changes.get(1).state());
        assertAt(q(4),                  changes.get(1));
        assertEquals(PedalState.CHANGE, changes.get(2).state());
        assertAt(q(8),                  changes.get(2));
        assertEquals(PedalState.UP,     changes.get(3).state());
        assertAt(q(10),                 changes.get(3));

        // Wall-clock (the original ms expectations): bar 1 @ 60 bpm =
        // 4000 ms; bar 2 @ 120 bpm = 2000 ms; half of bar 3 = 1000 ms.
        var mapper = new TimeMapper(perf.tempo());
        assertEquals(4000L, mapper.toMs(changes.get(1).at()));
        assertEquals(6000L, mapper.toMs(changes.get(2).at()));
        assertEquals(7000L, mapper.toMs(changes.get(3).at()));
    }
}
