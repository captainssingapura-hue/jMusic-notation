package music.notation.performance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QuantizerTest {

    @Test
    void exactLegalDurations_areReturnedUnchanged() {
        for (int sf : Quantizer.LEGAL_SIXTY_FOURTHS) {
            assertEquals(sf, Quantizer.snap(sf), "sf=" + sf);
        }
    }

    @Test
    void slightlyOffGrid_snapsToNearestLegal() {
        // 16 = QUARTER. 17 is closer to 16 than 24, so snap → 16.
        assertEquals(16, Quantizer.snap(17));
        // 23 is closer to 24 (dotted quarter) than 16.
        assertEquals(24, Quantizer.snap(23));
        // 9 closer to 8 (eighth) than 12 (dotted eighth).
        assertEquals(8, Quantizer.snap(9));
    }

    @Test
    void fractionalInputs_areRoundedThenSnapped() {
        // 15.6 → round → 16 (QUARTER, exact).
        assertEquals(16, Quantizer.snap(15.6));
        // 15.4 → round → 15 → snap → 16 (closer than 12).
        assertEquals(16, Quantizer.snap(15.4));
        // 14.0 → 14 → snap: 14-12=2, 16-14=2 → tie → smaller (12).
        assertEquals(12, Quantizer.snap(14.0));
    }

    @Test
    void zeroAndSubMinimum_clampToOne() {
        assertEquals(1, Quantizer.snap(0));
        assertEquals(1, Quantizer.snap(0.4));
        assertEquals(1, Quantizer.snap(-5));
    }

    @Test
    void aboveMaximum_returnsLargestLegal() {
        // 70 > WHOLE (64); floor exists, ceiling does not → floor.
        assertEquals(64, Quantizer.snap(70));
        assertEquals(64, Quantizer.snap(1000));
    }

    @Test
    void floor_supportsGreedyDecomposition() {
        // Decompose 13sf as 12 + 1.
        assertEquals(Integer.valueOf(12), Quantizer.floor(13));
        // Decompose 5sf as 4 + 1.
        assertEquals(Integer.valueOf(4), Quantizer.floor(5));
        // Below minimum returns null.
        assertNull(Quantizer.floor(0));
    }

    @Test
    void isLegal_matchesTheGrid() {
        assertTrue(Quantizer.isLegal(16));   // QUARTER
        assertTrue(Quantizer.isLegal(48));   // HALF.dot()
        assertFalse(Quantizer.isLegal(15));  // off-grid
        assertFalse(Quantizer.isLegal(0));
    }
}
