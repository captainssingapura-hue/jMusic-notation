package music.notation.duration;

import org.junit.jupiter.api.Test;

import static music.notation.duration.BaseValue.*;
import static org.junit.jupiter.api.Assertions.*;

class DurationTest {

    // ── BaseValue rational ────────────────────────────────────────────

    @Test
    void baseValue_eighth_isOneOverEight() {
        assertEquals(1, EIGHTH.numerator());
        assertEquals(8, EIGHTH.denominator());
        assertEquals(8, EIGHTH.sixtyFourths());
    }

    @Test
    void baseValue_quarter_is16sf() {
        assertEquals(16, QUARTER.sixtyFourths());
    }

    @Test
    void baseValue_hundredTwentyEighth_exists() {
        assertEquals(1, HUNDRED_TWENTY_EIGHTH.numerator());
        assertEquals(128, HUNDRED_TWENTY_EIGHTH.denominator());
    }

    // ── Dotted rational ───────────────────────────────────────────────

    @Test
    void dotted_quarter_is3Over8() {
        var d = new Dotted(QUARTER);
        assertEquals(3, d.numerator());
        assertEquals(8, d.denominator());
        assertEquals(24, d.sixtyFourths());     // 64 × 3/8
    }

    @Test
    void doubleDotted_quarter_is7Over16() {
        var d = new Dotted(QUARTER, 2);
        assertEquals(7, d.numerator());
        assertEquals(16, d.denominator());
        assertEquals(28, d.sixtyFourths());
    }

    @Test
    void tripleDotted_half_is15Over16() {
        var d = new Dotted(HALF, 3);
        assertEquals(15, d.numerator());
        assertEquals(16, d.denominator());
    }

    @Test
    void dotted_invalidDotCount_throws() {
        assertThrows(IllegalArgumentException.class, () -> new Dotted(QUARTER, 0));
        assertThrows(IllegalArgumentException.class, () -> new Dotted(QUARTER, 4));
    }

    @Test
    void dotted_constants_match() {
        assertEquals(new Dotted(QUARTER), Dotted.QUARTER);
        assertEquals(new Dotted(EIGHTH),  Dotted.EIGHTH);
    }

    @Test
    void dotted_dot_addsAnotherDot() {
        Duration d = Dotted.QUARTER.dot();
        assertInstanceOf(Dotted.class, d);
        assertEquals(2, ((Dotted) d).dotCount());
    }

    // ── Triplet rational ──────────────────────────────────────────────

    @Test
    void triplet_eighth_isOneOver12() {
        var t = new Triplet(EIGHTH);
        // 2 / (8*3) = 2/24, but record stores raw — equalsDuration handles reduction.
        assertEquals(2, t.numerator());
        assertEquals(24, t.denominator());
        assertTrue(t.equalsDuration(Duration.of(1, 12)));
    }

    @Test
    void triplet_quarter_isOneOver6() {
        var t = new Triplet(QUARTER);
        assertTrue(t.equalsDuration(Duration.of(1, 6)));
    }

    @Test
    void triplet_threeOfThem_sumToBaseTimes2() {
        // Three triplet-eighths should sum to a quarter.
        Duration sum = Triplet.EIGHTH.plus(Triplet.EIGHTH).plus(Triplet.EIGHTH);
        assertTrue(sum.equalsDuration(QUARTER), "expected 1/4, got " + sum);
    }

    @Test
    void triplet_constants_match() {
        assertEquals(new Triplet(EIGHTH),   Triplet.EIGHTH);
        assertEquals(new Triplet(QUARTER),  Triplet.QUARTER);
    }

    // ── RawTuplet rational ────────────────────────────────────────────

    @Test
    void rawTuplet_quintupletEighth_isOneOver10() {
        var t = RawTuplet.ofStandard(5, EIGHTH);
        assertEquals(5, t.actualCount());
        assertEquals(4, t.normalCount());
        assertTrue(t.equalsDuration(Duration.of(1, 10)));
    }

    @Test
    void rawTuplet_septupletEighth_isOneOver14() {
        var t = RawTuplet.ofStandard(7, EIGHTH);
        assertTrue(t.equalsDuration(Duration.of(1, 14)));
    }

    @Test
    void rawTuplet_nonupletEighth_isOneOver9() {
        var t = RawTuplet.ofStandard(9, EIGHTH);
        assertEquals(8, t.normalCount());
        assertTrue(t.equalsDuration(Duration.of(1, 9)));
    }

    @Test
    void rawTuplet_sextupletEighth_sameAsTripletEighth() {
        // Sextuplet eighth: 4/(6*8) = 1/12. Triplet eighth: 2/(3*8) = 1/12. Same.
        var sext = RawTuplet.ofStandard(6, EIGHTH);
        assertTrue(sext.equalsDuration(Triplet.EIGHTH));
    }

    @Test
    void rawTuplet_nonStandardRatio_works() {
        // 5 in the time of 6 eighths — non-standard.
        var t = new RawTuplet(5, 6, EIGHTH);
        // 6/(5*8) = 6/40 = 3/20.
        assertTrue(t.equalsDuration(Duration.of(3, 20)));
    }

    // ── RawDuration rational ──────────────────────────────────────────

    @Test
    void rawDuration_normalizesToLowestTerms() {
        var r = new RawDuration(48, 64);
        assertEquals(3, r.numerator());
        assertEquals(4, r.denominator());
    }

    @Test
    void rawDuration_normalisesNegativeDenominator() {
        var r = new RawDuration(1, -2);
        assertEquals(-1, r.numerator());
        assertEquals(2,  r.denominator());
    }

    @Test
    void rawDuration_zeroNumerator_isCanonicalZero() {
        var r = new RawDuration(0, 7);
        assertEquals(0, r.numerator());
        assertEquals(1, r.denominator());
    }

    @Test
    void rawDuration_zeroDenominator_throws() {
        assertThrows(IllegalArgumentException.class, () -> new RawDuration(1, 0));
    }

    @Test
    void rawDuration_ofSixtyFourths_isLegacyShape() {
        Duration d = Duration.ofSixtyFourths(48);
        assertEquals(48, d.sixtyFourths());
        assertTrue(d.equalsDuration(Dotted.HALF));   // 3/4
    }

    // ── arithmetic ────────────────────────────────────────────────────

    @Test
    void plus_quarterPlusEighth_isThreeEighths() {
        Duration sum = QUARTER.plus(EIGHTH);
        assertTrue(sum.equalsDuration(Dotted.QUARTER));    // = 3/8
    }

    @Test
    void plus_threeQuarters_isDottedHalf() {
        Duration sum = QUARTER.plus(QUARTER).plus(QUARTER);
        assertTrue(sum.equalsDuration(Dotted.HALF));    // = 3/4
    }

    @Test
    void plus_fourQuarters_isWhole() {
        Duration sum = QUARTER.plus(QUARTER).plus(QUARTER).plus(QUARTER);
        assertTrue(sum.equalsDuration(WHOLE));
    }

    @Test
    void plus_acrossVariants_ratioMatches() {
        Duration sum = Triplet.EIGHTH.plus(Triplet.EIGHTH);   // 2/12 = 1/6
        assertTrue(sum.equalsDuration(Triplet.QUARTER));
    }

    @Test
    void minus_works() {
        Duration diff = QUARTER.minus(EIGHTH);
        assertTrue(diff.equalsDuration(EIGHTH));
    }

    @Test
    void times_doubles() {
        Duration doubled = EIGHTH.times(2);
        assertTrue(doubled.equalsDuration(QUARTER));
    }

    @Test
    void dividedBy_halves() {
        Duration half = QUARTER.dividedBy(2);
        assertTrue(half.equalsDuration(EIGHTH));
    }

    // ── compare / equals ──────────────────────────────────────────────

    @Test
    void compareDuration_smallerIsNegative() {
        assertTrue(EIGHTH.compareDuration(QUARTER) < 0);
        assertTrue(QUARTER.compareDuration(EIGHTH) > 0);
        assertEquals(0, EIGHTH.compareDuration(EIGHTH));
    }

    @Test
    void equalsDuration_isValueAware() {
        // Different variants, same fraction (1/8).
        assertTrue(EIGHTH.equalsDuration(Duration.of(1, 8)));
        assertTrue(EIGHTH.equalsDuration(Duration.ofSixtyFourths(8)));
    }

    @Test
    void javaEquals_isTypeAware() {
        // Different variants, same value — Java equals is FALSE.
        assertNotEquals(EIGHTH, (Object) Duration.of(1, 8));
        // Same variant, same value — TRUE.
        assertEquals(Triplet.EIGHTH, new Triplet(EIGHTH));
    }

    // ── ticks (PPQ) ───────────────────────────────────────────────────

    @Test
    void ticks_atPpq480() {
        // PPQ 480: quarter = 480 ticks. Whole = 1920. Eighth = 240.
        assertEquals(1920, WHOLE.ticks(480));
        assertEquals(480,  QUARTER.ticks(480));
        assertEquals(240,  EIGHTH.ticks(480));
        // Triplet eighth: 1/12 of whole = 1920/12 = 160 ticks.
        assertEquals(160, Triplet.EIGHTH.ticks(480));
        // Quintuplet eighth: 1/10 = 192 ticks.
        assertEquals(192, RawTuplet.ofStandard(5, EIGHTH).ticks(480));
        // Septuplet eighth: 1/14 = 137.14 → 137 (integer truncation).
        assertEquals(137, RawTuplet.ofStandard(7, EIGHTH).ticks(480));
    }

    // ── canonical ─────────────────────────────────────────────────────

    @Test
    void canonical_returnsRawDuration() {
        Duration c = Triplet.EIGHTH.canonical();
        assertInstanceOf(RawDuration.class, c);
        assertTrue(c.equalsDuration(Triplet.EIGHTH));
    }

    @Test
    void canonical_normalizes() {
        Duration c = new RawDuration(2, 24).canonical();
        assertEquals(1, c.numerator());
        assertEquals(12, c.denominator());
    }

    // ── factories ─────────────────────────────────────────────────────

    @Test
    void factory_triplet() {
        assertEquals(Triplet.EIGHTH, Duration.triplet(EIGHTH));
    }

    @Test
    void factory_tupletStandard() {
        Duration q = Duration.tuplet(5, EIGHTH);
        assertInstanceOf(RawTuplet.class, q);
        assertEquals(5, ((RawTuplet) q).actualCount());
        assertEquals(4, ((RawTuplet) q).normalCount());
    }

    @Test
    void factory_tupletGeneral() {
        Duration q = Duration.tuplet(5, 6, EIGHTH);
        assertInstanceOf(RawTuplet.class, q);
        assertEquals(5, ((RawTuplet) q).actualCount());
        assertEquals(6, ((RawTuplet) q).normalCount());
    }

    @Test
    void factory_dotted() {
        assertEquals(Dotted.QUARTER, Duration.dotted(QUARTER));
    }
}
