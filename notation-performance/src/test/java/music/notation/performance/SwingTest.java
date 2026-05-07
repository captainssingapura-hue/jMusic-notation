package music.notation.performance;

import music.notation.expressivity.*;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SwingTest {

    private static final TrackId TRACK = new TrackId("test");

    @Test
    void tripletSwing_makesFirstOfPairTwiceAsLong() {
        // Two equal 100ms notes -> pair total 200ms; triplet ratio 2/3.
        var p = onePerformance(
                new PitchedNote(0,   100, 60),
                new PitchedNote(100, 100, 62)
        );
        var swung = Swing.apply(p, Swing.TRIPLET).score().tracks().get(0).notes();

        // First: tick 0, dur round(200 * 2/3) = 133.
        // Second: tick 133, dur 200 - 133 = 67.
        assertNote(swung.get(0), 0,   133, 60);
        assertNote(swung.get(1), 133, 67,  62);
    }

    @Test
    void shuffleSwing_appliesGivenRatio() {
        var p = onePerformance(
                new PitchedNote(0,   100, 60),
                new PitchedNote(100, 100, 62)
        );
        var swung = Swing.apply(p, 0.60).score().tracks().get(0).notes();

        // 200 * 0.60 = 120; second starts at 120, lasts 80.
        assertNote(swung.get(0), 0,   120, 60);
        assertNote(swung.get(1), 120, 80,  62);
    }

    @Test
    void noSwing_returnsIdenticalPerformance() {
        var p = onePerformance(
                new PitchedNote(0,   100, 60),
                new PitchedNote(100, 100, 62)
        );
        assertSame(p, Swing.apply(p, Swing.NONE));
    }

    @Test
    void oddTailNote_isUnchanged() {
        var p = onePerformance(
                new PitchedNote(0,   100, 60),
                new PitchedNote(100, 100, 62),
                new PitchedNote(200, 100, 64)
        );
        var swung = Swing.apply(p, Swing.TRIPLET).score().tracks().get(0).notes();

        assertEquals(3, swung.size());
        // First pair is swung; tail note untouched.
        assertNote(swung.get(0), 0,   133, 60);
        assertNote(swung.get(1), 133, 67,  62);
        assertNote(swung.get(2), 200, 100, 64);
    }

    @Test
    void multiTrack_swingsEachIndependently() {
        var trackA = new Track(new TrackId("a"), TrackKind.PITCHED, List.of(
                new PitchedNote(0,   100, 60),
                new PitchedNote(100, 100, 62)
        ));
        var trackB = new Track(new TrackId("b"), TrackKind.PITCHED, List.of(
                new PitchedNote(50,  100, 67),
                new PitchedNote(150, 100, 69)
        ));
        var p = new Performance(new Score(List.of(trackA, trackB)),
                TempoTrack.empty(), Instrumentation.empty(), Articulations.empty());
        var swung = Swing.apply(p, Swing.TRIPLET);

        var a = swung.score().tracks().get(0).notes();
        var b = swung.score().tracks().get(1).notes();
        // A starts at 0, pair total 200, first dur 133.
        assertNote(a.get(0), 0,   133, 60);
        // B starts at 50, pair total 200, first dur 133.
        assertNote(b.get(0), 50,  133, 67);
        assertNote(b.get(1), 183, 67,  69);
    }

    @Test
    void swing_roundTripsThroughMidi() {
        var p = onePerformance(
                new PitchedNote(0,   220, 60),
                new PitchedNote(220, 220, 62),
                new PitchedNote(440, 220, 64),
                new PitchedNote(660, 220, 65)
        );
        var swung = Swing.apply(p, Swing.TRIPLET);
        assertEquals(swung, MidiCodec.fromMidi(MidiCodec.toMidi(swung)));
    }

    @Test
    void rejectsRatioOutsideRange() {
        var p = onePerformance(new PitchedNote(0, 100, 60));
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

    private static void assertNote(ConcreteNote n, long tick, long dur, int midi) {
        var pn = (PitchedNote) n;
        assertEquals(tick, pn.tickMs(),  "tickMs");
        assertEquals(dur,  pn.durationMs(), "durationMs");
        assertEquals(midi, pn.midi(), "midi");
    }
}
