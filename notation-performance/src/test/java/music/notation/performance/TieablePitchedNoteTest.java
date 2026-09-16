package music.notation.performance;

import music.notation.duration.Duration;
import music.notation.expressivity.*;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link PitchedNote} carries a {@code tiedToNext} flag (the {@code Tieable}
 * contract) which round-trips through the JSON codec.
 */
class TieablePitchedNoteTest {

    /** {@code n} quarter notes. */
    private static Duration q(long n) { return Duration.of(n, 4); }

    @Test
    void tiedToNextFlagIsAccessible() {
        var pn = new PitchedNote(q(0), q(1), 60, true);
        assertTrue(pn.tiedToNext());
    }

    @Test
    void withTiedToNextFlipsTheFlag() {
        var pn = new PitchedNote(q(0), q(1), 60, false);
        var tied = pn.withTiedToNext();
        assertInstanceOf(PitchedNote.class, tied);
        assertTrue(((PitchedNote) tied).tiedToNext());
        assertFalse(pn.tiedToNext(), "original should be unchanged");
    }

    @Test
    void backwardCompatConstructorDefaultsTiedToNextFalse() {
        var pn = new PitchedNote(q(0), q(1), 60);
        assertFalse(pn.tiedToNext());
    }

    @Test
    void jsonRoundTripPreservesTiedToNext() {
        var trackId = new TrackId("lead");
        var track = new Track(trackId, TrackKind.PITCHED, List.of(
                new PitchedNote(q(0), q(1), 60, true),
                new PitchedNote(q(1), q(1), 62, false),
                new PitchedNote(q(2), q(1), 64, true)));
        var p = Performance.of(Score.of(track));
        var back = PerformanceJson.fromJson(PerformanceJson.toJson(p));
        assertEquals(p, back);

        // Verify the flag actually survived (not just that .equals lines up trivially).
        var notes = back.score().tracks().get(0).notes();
        assertTrue(((PitchedNote) notes.get(0)).tiedToNext());
        assertFalse(((PitchedNote) notes.get(1)).tiedToNext());
        assertTrue(((PitchedNote) notes.get(2)).tiedToNext());
    }
}
