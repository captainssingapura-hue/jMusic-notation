package music.notation.transcribe;

import music.notation.duration.Duration;
import music.notation.structure.TimeSignature;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GridQuantizerTest {

    /** ♩ = 120: a quarter is 500 ms, a sixteenth 125 ms. */
    private static final TimingSpec T = new TimingSpec(120);

    private static TranscribedNote n(double startMs, double endMs, int midi) {
        return new TranscribedNote(startMs, endMs, midi, 0, 0.9, 0.2);
    }

    private static void assertDur(long num, long den, Duration d) {
        assertTrue(d.equalsDuration(Duration.of(num, den)), "expected " + num + "/" + den + " got " + d.numerator() + "/" + d.denominator());
    }

    @Test
    void onsetsAndReleasesSnapToTheGrid() {
        var r = GridQuantizer.quantize(List.of(
                n(1010, 1460, 60),                // ≈ beat 1, a quarter (450 ms → 4 steps of 125)
                n(1520, 1740, 62),                // ≈ beat 2, an eighth
                n(1760, 2990, 64)), T, 1.0);      // ≈ beat 2&, to the bar's end
        var q = r.notes();
        assertEquals(3, q.size());
        assertDur(0, 1, q.get(0).at());   assertDur(1, 4, q.get(0).duration());
        assertDur(1, 4, q.get(1).at());   assertDur(1, 8, q.get(1).duration());
        assertDur(3, 8, q.get(2).at());   assertDur(5, 8, q.get(2).duration());
        assertEquals(1, q.get(2).bar());
        assertEquals(2.5, q.get(2).beat(), 1e-12);
        assertEquals(0, r.pickupBars());
        assertEquals(0, r.dropped());
    }

    @Test
    void notesBeforeTheDownbeatBecomeAPickupBar() {
        var r = GridQuantizer.quantize(List.of(n(500, 950, 67), n(1000, 2000, 72)), T, 1.0);
        assertEquals(1, r.pickupBars());
        var q = r.notes();
        assertDur(3, 4, q.get(0).at());                         // beat 4 of the pickup bar
        assertEquals(1, q.get(0).bar());
        assertEquals(4.0, q.get(0).beat(), 1e-12);
        assertDur(1, 1, q.get(1).at());
        assertEquals(2, q.get(1).bar());
        assertEquals(1.0, q.get(1).beat(), 1e-12);
    }

    @Test
    void collisionsKeepTheLongerNoteAndOverlapsAreTrimmed() {
        var r = GridQuantizer.quantize(List.of(
                n(1000, 1040, 59),                // a 40 ms scrap landing on the same step…
                n(1030, 1500, 60),                // …as the real note
                n(1480, 2000, 62)), T, 1.0);      // starts as the previous one ends
        assertEquals(1, r.dropped());
        assertEquals(List.of(60, 62), r.notes().stream().map(QuantizedNote::midi).toList());
        assertDur(1, 4, r.notes().get(0).duration());
    }

    @Test
    void everyNoteKeepsAtLeastOneStep() {
        var r = GridQuantizer.quantize(List.of(n(1000, 1050, 60), n(1130, 1300, 62)), T, 1.0);
        assertDur(1, 16, r.notes().get(0).duration());
        assertDur(1, 16, r.notes().get(1).at());
    }

    @Test
    void tripletGridAndOtherMeters() {
        var triplets = new TimingSpec(120, null, null, null, Duration.of(1, 12));
        var r = GridQuantizer.quantize(List.of(n(1000, 1160, 60), n(1167, 1330, 62), n(1333, 1500, 64)), triplets, 1.0);
        assertDur(1, 12, r.notes().get(1).at());
        assertDur(1, 6, r.notes().get(2).at());

        var sixEight = new TimingSpec(120, null, new TimeSignature(6, 8), null, Duration.of(1, 8));
        assertEquals(6, sixEight.stepsPerBar());
        var r2 = GridQuantizer.quantize(List.of(n(1000, 1240, 60), n(4000, 4240, 62)), sixEight, 1.0);
        assertEquals(3, r2.notes().get(1).bar());                                // 3 s after the downbeat = 12 eighths = bar 3
    }

    @Test
    void gridMustDivideTheBar() {
        assertThrows(IllegalArgumentException.class,
                () -> new TimingSpec(120, null, new TimeSignature(3, 4), null, Duration.of(1, 5)));
    }

    @Test
    void velocityFollowsRelativeLoudness() {
        assertEquals(127, GridQuantizer.velocity(0.4, 0.4));
        assertEquals(40 + 44, GridQuantizer.velocity(0.1, 0.4));         // √0.25 = 0.5
        assertEquals(80, GridQuantizer.velocity(0.1, 0));
    }
}
