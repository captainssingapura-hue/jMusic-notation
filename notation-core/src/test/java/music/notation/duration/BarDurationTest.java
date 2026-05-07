package music.notation.duration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BarDurationTest {

    // ── basic shape ────────────────────────────────────────────────

    @Test
    void fourFour_isFourQuarters_64sf() {
        var bd = new BarDuration(4, BaseValue.QUARTER);
        assertEquals(4, bd.unitCount());
        assertEquals(BaseValue.QUARTER, bd.unit());
        assertEquals(64, bd.sixtyFourths());
    }

    @Test
    void sixEight_isSixEighths_48sf() {
        var bd = new BarDuration(6, BaseValue.EIGHTH);
        assertEquals(48, bd.sixtyFourths());
    }

    @Test
    void twelveEight_is12Eighths_96sf() {
        var bd = new BarDuration(12, BaseValue.EIGHTH);
        assertEquals(96, bd.sixtyFourths());
    }

    @Test
    void cutTime_2half_64sf() {
        var bd = new BarDuration(2, BaseValue.HALF);
        assertEquals(64, bd.sixtyFourths());
    }

    @Test
    void irregular_5quarter_80sf() {
        var bd = new BarDuration(5, BaseValue.QUARTER);
        assertEquals(80, bd.sixtyFourths());
    }

    // ── totalDuration mirrors sixtyFourths ─────────────────────────

    @Test
    void totalDuration_matchesSixtyFourths() {
        var bd = new BarDuration(3, BaseValue.QUARTER);
        assertEquals(48, bd.totalDuration().sixtyFourths());
    }

    // ── meter character: 6/8 vs 3/4 ────────────────────────────────

    @Test
    void sixEightAndThreeFour_sameTotalDifferentValue() {
        var sixEight  = new BarDuration(6, BaseValue.EIGHTH);
        var threeFour = new BarDuration(3, BaseValue.QUARTER);
        // Same physical duration:
        assertEquals(sixEight.sixtyFourths(), threeFour.sixtyFourths());
        // …but different meters — record equality preserves the (count, unit) pair.
        assertNotEquals(sixEight, threeFour);
    }

    // ── fromTimeSignature factory ──────────────────────────────────

    @Test
    void fromTimeSignature_fourFour() {
        assertEquals(new BarDuration(4, BaseValue.QUARTER),
                BarDuration.fromTimeSignature(4, 4));
    }

    @Test
    void fromTimeSignature_sixEight() {
        assertEquals(new BarDuration(6, BaseValue.EIGHTH),
                BarDuration.fromTimeSignature(6, 8));
    }

    @Test
    void fromTimeSignature_threeEight() {
        assertEquals(new BarDuration(3, BaseValue.EIGHTH),
                BarDuration.fromTimeSignature(3, 8));
    }

    @Test
    void fromTimeSignature_cutTime() {
        assertEquals(new BarDuration(2, BaseValue.HALF),
                BarDuration.fromTimeSignature(2, 2));
    }

    @Test
    void fromTimeSignature_allLegalDenominators() {
        for (int d : new int[] {1, 2, 4, 8, 16, 32, 64}) {
            assertDoesNotThrow(() -> BarDuration.fromTimeSignature(1, d),
                    "denominator " + d + " should be legal");
        }
    }

    // ── fromSixtyFourths reverse-math factory ──────────────────────

    @Test
    void fromSixtyFourths_64_isFourQuarters() {
        assertEquals(new BarDuration(4, BaseValue.QUARTER),
                BarDuration.fromSixtyFourths(64));
    }

    @Test
    void fromSixtyFourths_48_prefersThreeQuartersOverSixEighths() {
        // Both (3, QUARTER) and (6, EIGHTH) are valid; we prefer the QUARTER reading.
        assertEquals(new BarDuration(3, BaseValue.QUARTER),
                BarDuration.fromSixtyFourths(48));
    }

    @Test
    void fromSixtyFourths_24_isThreeEighths() {
        // 24 % 16 != 0, so falls through to EIGHTH preference.
        assertEquals(new BarDuration(3, BaseValue.EIGHTH),
                BarDuration.fromSixtyFourths(24));
    }

    @Test
    void fromSixtyFourths_80_isFiveQuarters() {
        assertEquals(new BarDuration(5, BaseValue.QUARTER),
                BarDuration.fromSixtyFourths(80));
    }

    @Test
    void fromSixtyFourths_12_isThreeSixteenths() {
        // 12 % 8 != 0, falls through to SIXTEENTH.
        assertEquals(new BarDuration(3, BaseValue.SIXTEENTH),
                BarDuration.fromSixtyFourths(12));
    }

    @Test
    void fromSixtyFourths_7_fallsBackToSixtyFourths() {
        // No clean division; pathological fallback to SIXTY_FOURTH.
        assertEquals(new BarDuration(7, BaseValue.SIXTY_FOURTH),
                BarDuration.fromSixtyFourths(7));
    }

    @Test
    void fromSixtyFourths_roundTripsTotalDuration() {
        // Whatever (count, unit) the heuristic picks, the total sf
        // must equal the input (lossless on the duration side).
        for (int sf = 1; sf <= 200; sf++) {
            assertEquals(sf, BarDuration.fromSixtyFourths(sf).sixtyFourths(),
                    "round-trip failed at sf=" + sf);
        }
    }

    @Test
    void fromSixtyFourths_zeroOrNegative_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> BarDuration.fromSixtyFourths(0));
        assertThrows(IllegalArgumentException.class,
                () -> BarDuration.fromSixtyFourths(-1));
    }

    // ── validation ─────────────────────────────────────────────────

    @Test
    void zeroUnitCount_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new BarDuration(0, BaseValue.QUARTER));
    }

    @Test
    void negativeUnitCount_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new BarDuration(-1, BaseValue.QUARTER));
    }

    @Test
    void nullUnit_throws() {
        assertThrows(NullPointerException.class,
                () -> new BarDuration(4, null));
    }

    @Test
    void unsupportedDenominator_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> BarDuration.fromTimeSignature(4, 3));   // 3 isn't pow2
        assertThrows(IllegalArgumentException.class,
                () -> BarDuration.fromTimeSignature(4, 5));
        assertThrows(IllegalArgumentException.class,
                () -> BarDuration.fromTimeSignature(4, 0));
        assertThrows(IllegalArgumentException.class,
                () -> BarDuration.fromTimeSignature(4, 128));  // beyond SIXTY_FOURTH
    }
}
