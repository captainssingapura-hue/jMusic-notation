package music.notation.performance;

import music.notation.duration.Duration;
import music.notation.expressivity.*;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Post-ms→Duration: swing is pure musical arithmetic. A pair of straight
 * eighths (1/8 + 1/8 = 1/4) is re-divided by the long ratio, snapped
 * to Swing's 1/96 grid (24 grid units per quarter).
 */
class SwingTest {

    private static final TrackId TRACK = new TrackId("test");

    /** {@code n} eighth notes. */
    private static Duration e(long n) { return Duration.of(n, 8); }

    @Test
    void tripletSwing_makesFirstOfPairTwiceAsLong() {
        // Two straight eighths -> pair total 1/4; triplet ratio 2/3.
        var p = onePerformance(
                new PitchedNote(e(0), e(1), 60),
                new PitchedNote(e(1), e(1), 62)
        );
        var swung = Swing.apply(p, Swing.TRIPLET).score().tracks().get(0).notes();

        // First: at 0, dur 1/4 × 2/3 = 1/6 (a triplet quarter).
        // Second: at 1/6, dur 1/4 − 1/6 = 1/12 (a triplet eighth).
        assertNote(swung.get(0), Duration.zero(),   Duration.of(1, 6),  60);
        assertNote(swung.get(1), Duration.of(1, 6), Duration.of(1, 12), 62);
    }

    @Test
    void shuffleSwing_appliesGivenRatio() {
        var p = onePerformance(
                new PitchedNote(e(0), e(1), 60),
                new PitchedNote(e(1), e(1), 62)
        );
        var swung = Swing.apply(p, 0.60).score().tracks().get(0).notes();

        // 1/4 = 24 grid units (1/96); 24 × 0.60 = 14.4 → 14/96 = 7/48.
        // Second starts at 7/48, lasts 1/4 − 7/48 = 5/48.
        assertNote(swung.get(0), Duration.zero(),    Duration.of(7, 48), 60);
        assertNote(swung.get(1), Duration.of(7, 48), Duration.of(5, 48), 62);
    }

    @Test
    void noSwing_returnsIdenticalPerformance() {
        var p = onePerformance(
                new PitchedNote(e(0), e(1), 60),
                new PitchedNote(e(1), e(1), 62)
        );
        assertSame(p, Swing.apply(p, Swing.NONE));
    }

    @Test
    void oddTailNote_isUnchanged() {
        var p = onePerformance(
                new PitchedNote(e(0), e(1), 60),
                new PitchedNote(e(1), e(1), 62),
                new PitchedNote(e(2), e(1), 64)
        );
        var swung = Swing.apply(p, Swing.TRIPLET).score().tracks().get(0).notes();

        assertEquals(3, swung.size());
        // First pair is swung; tail note untouched.
        assertNote(swung.get(0), Duration.zero(),   Duration.of(1, 6),  60);
        assertNote(swung.get(1), Duration.of(1, 6), Duration.of(1, 12), 62);
        assertNote(swung.get(2), e(2),              e(1),               64);
    }

    @Test
    void multiTrack_swingsEachIndependently() {
        var trackA = new Track(new TrackId("a"), TrackKind.PITCHED, List.of(
                new PitchedNote(e(0), e(1), 60),
                new PitchedNote(e(1), e(1), 62)
        ));
        // Track B is offset by a sixteenth.
        Duration s = Duration.of(1, 16);
        var trackB = new Track(new TrackId("b"), TrackKind.PITCHED, List.of(
                new PitchedNote(s,           e(1), 67),
                new PitchedNote(s.plus(e(1)), e(1), 69)
        ));
        var p = new Performance(new Score(List.of(trackA, trackB)),
                TempoTrack.empty(), Instrumentation.empty(), Articulations.empty());
        var swung = Swing.apply(p, Swing.TRIPLET);

        var a = swung.score().tracks().get(0).notes();
        var b = swung.score().tracks().get(1).notes();
        // A starts at 0, pair total 1/4, first dur 1/6.
        assertNote(a.get(0), Duration.zero(), Duration.of(1, 6), 60);
        // B starts at 1/16, pair total 1/4, first dur 1/6; second at 1/16 + 1/6 = 11/48.
        assertNote(b.get(0), s,                    Duration.of(1, 6),  67);
        assertNote(b.get(1), Duration.of(11, 48),  Duration.of(1, 12), 69);
    }

    @Test
    void swing_roundTripsThroughMidi() {
        var p = onePerformance(
                new PitchedNote(e(0), e(1), 60),
                new PitchedNote(e(1), e(1), 62),
                new PitchedNote(e(2), e(1), 64),
                new PitchedNote(e(3), e(1), 65)
        );
        var swung = Swing.apply(p, Swing.TRIPLET);
        assertEquals(swung, MidiCodec.fromMidi(MidiCodec.toMidi(swung)));
    }

    @Test
    void rejectsRatioOutsideRange() {
        var p = onePerformance(new PitchedNote(e(0), e(1), 60));
        assertThrows(IllegalArgumentException.class, () -> Swing.apply(p, 0.4));
        assertThrows(IllegalArgumentException.class, () -> Swing.apply(p, 1.1));
    }

    // ── helpers ──

    private static Performance onePerformance(ConcreteNote... notes) {
        var track = new Track(TRACK, TrackKind.PITCHED, List.of(notes));
        return new Performance(
                new Score(List.of(track)),
                TempoTrack.empty(),
                Instrumentation.empty(),
                Articulations.empty());
    }

    private static void assertNote(ConcreteNote n, Duration at, Duration dur, int midi) {
        var pn = (PitchedNote) n;
        assertTrue(at.equalsDuration(pn.at()),
                "at: expected " + at + " but was " + pn.at());
        assertTrue(dur.equalsDuration(pn.duration()),
                "duration: expected " + dur + " but was " + pn.duration());
        assertEquals(midi, pn.midi(), "midi");
    }
}
