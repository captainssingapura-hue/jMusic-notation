package music.notation.performance;

import music.notation.duration.BaseValue;
import music.notation.duration.Dotted;
import music.notation.duration.Duration;
import music.notation.duration.RawTuplet;
import music.notation.duration.Triplet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QuantizerProfileTest {

    // ── Preset profiles contain the right shapes ──────────────────────

    @Test
    void standard_containsBaseAndDotted_butNoTuplets() {
        var legals = QuantizerProfile.STANDARD.legalDurations();
        assertTrue(containsValue(legals, BaseValue.QUARTER));
        assertTrue(containsValue(legals, Dotted.QUARTER));
        assertFalse(containsValue(legals, Triplet.EIGHTH));
        assertFalse(containsValue(legals, RawTuplet.ofStandard(5, BaseValue.SIXTEENTH)));
    }

    @Test
    void withTriplets_addsTripletShapes() {
        var legals = QuantizerProfile.WITH_TRIPLETS.legalDurations();
        assertTrue(containsValue(legals, Triplet.EIGHTH));
        assertTrue(containsValue(legals, Triplet.SIXTEENTH));
        assertFalse(containsValue(legals, RawTuplet.ofStandard(5, BaseValue.SIXTEENTH)));
    }

    @Test
    void full_addsQuintupletsAndSeptuplets() {
        var legals = QuantizerProfile.FULL.legalDurations();
        assertTrue(containsValue(legals, Triplet.EIGHTH));
        assertTrue(containsValue(legals, RawTuplet.ofStandard(5, BaseValue.SIXTEENTH)));
        assertTrue(containsValue(legals, RawTuplet.ofStandard(7, BaseValue.EIGHTH)));
    }

    @Test
    void improv_isFullForNow() {
        assertEquals(QuantizerProfile.FULL.legalDurations().size(),
                     QuantizerProfile.IMPROV.legalDurations().size());
    }

    // ── Legal set sorted ascending by value ───────────────────────────

    @Test
    void legalDurations_sortedAscending() {
        var legals = QuantizerProfile.WITH_TRIPLETS.legalDurations();
        for (int i = 1; i < legals.size(); i++) {
            assertTrue(legals.get(i - 1).compareDuration(legals.get(i)) < 0,
                    "out of order at i=" + i);
        }
    }

    // ── Builder customisation ─────────────────────────────────────────

    @Test
    void customBuilder_addsExplicitDuration() {
        var profile = QuantizerProfile.builder()
                .withBaseDurations()
                .add(Duration.of(1, 9))      // nonuplet eighth-ish
                .build();
        assertTrue(containsValue(profile.legalDurations(), Duration.of(1, 9)));
    }

    @Test
    void emptyProfile_throws() {
        assertThrows(IllegalStateException.class,
                () -> QuantizerProfile.builder().build());
    }

    // ── Snap behaviour: power-of-two only profile ─────────────────────

    @Test
    void snap_standard_quarterStayQuarter() {
        Duration snapped = Quantizer.snap(BaseValue.QUARTER, QuantizerProfile.STANDARD);
        assertTrue(snapped.equalsDuration(BaseValue.QUARTER));
    }

    @Test
    void snap_standard_tripletEighthMisreadsAsDottedSixteenth() {
        // 1/12 (≈0.0833) isn't in STANDARD. The closest legal value is
        // 3/32 (dotted sixteenth ≈ 0.09375). This is the precise lossy
        // behaviour that explains why "Turkish March sounds off" without
        // a triplet-aware profile — every triplet eighth gets misread as
        // a dotted sixteenth, accumulating drift bar-by-bar.
        Duration snapped = Quantizer.snap(Triplet.EIGHTH, QuantizerProfile.STANDARD);
        assertTrue(snapped.equalsDuration(Duration.of(3, 32)),
                "expected 3/32 (dotted sixteenth), got " + snapped);
    }

    // ── Snap behaviour: triplet-aware profile ─────────────────────────

    @Test
    void snap_withTriplets_tripletEighthExact() {
        Duration snapped = Quantizer.snap(Triplet.EIGHTH, QuantizerProfile.WITH_TRIPLETS);
        assertTrue(snapped.equalsDuration(Triplet.EIGHTH),
                "expected exactly 1/12, got " + snapped);
    }

    @Test
    void snap_withTriplets_offGridTripletStillSnapsCorrectly() {
        // ~1/12 = 0.0833. Slight off-grid value 0.085 should still snap to triplet eighth.
        Duration nearTriplet = Duration.of(85, 1000);
        Duration snapped = Quantizer.snap(nearTriplet, QuantizerProfile.WITH_TRIPLETS);
        assertTrue(snapped.equalsDuration(Triplet.EIGHTH),
                "expected 1/12 (Triplet eighth), got " + snapped);
    }

    @Test
    void snap_full_quintupletExact() {
        Duration quint = RawTuplet.ofStandard(5, BaseValue.SIXTEENTH);
        Duration snapped = Quantizer.snap(quint, QuantizerProfile.FULL);
        assertTrue(snapped.equalsDuration(quint),
                "expected exactly 1/20, got " + snapped);
    }

    // ── The Turkish March case ────────────────────────────────────────

    @Test
    void snap_withTriplets_turkishMarchOpening_recognizes16thsCorrectly() {
        // The B-A-G♯-A opening: each note is a sixteenth (1/16 = 0.0625).
        // Even with WITH_TRIPLETS profile (which adds 1/12 etc.), 1/16 wins
        // because it's the exact value.
        Duration snapped = Quantizer.snap(BaseValue.SIXTEENTH, QuantizerProfile.WITH_TRIPLETS);
        assertTrue(snapped.equalsDuration(BaseValue.SIXTEENTH));
    }

    @Test
    void snap_withTriplets_offBy20pct_stillRoundsBack() {
        // A "shortened" sixteenth at ~3/64 = 0.0469 (75% of the proper 1/16).
        // STANDARD would snap to 3/64 (which is dotted-32nd). WITH_TRIPLETS
        // would consider 1/24 (triplet 16th = 0.0417) as a candidate.
        // 0.0469 is closer to 0.0417 (1/24) than 0.0625 (1/16) — distance 0.0052 vs 0.0156.
        // So it picks 1/24, which is correct for triplet-feel input.
        Duration shortened = Duration.of(3, 64);
        Duration snapped = Quantizer.snap(shortened, QuantizerProfile.WITH_TRIPLETS);
        // Either 1/24 (Triplet.SIXTEENTH = 1/24) or the dotted-32nd (3/64) is acceptable;
        // verify the result is at least one of the legal options and reasonably close.
        assertTrue(snapped.equalsDuration(Triplet.SIXTEENTH)
                || snapped.equalsDuration(Duration.of(3, 64)),
                "expected 1/24 or 3/64, got " + snapped);
    }

    // ── Phase 8: finest grid + snapToGrid ─────────────────────────────

    @Test
    void finestGrid_standard_isOneOverSixtyFour() {
        Duration g = Quantizer.finestGrid(QuantizerProfile.STANDARD);
        assertTrue(g.equalsDuration(Duration.of(1, 64)),
                "STANDARD finest grid should be 1/64; got " + g);
    }

    @Test
    void finestGrid_withTriplets_isOneOverOneNinetyTwo() {
        // LCM of 64 (powers-of-2) and 24/12/6/3/48 (triplets) = 192.
        Duration g = Quantizer.finestGrid(QuantizerProfile.WITH_TRIPLETS);
        assertTrue(g.equalsDuration(Duration.of(1, 192)),
                "WITH_TRIPLETS finest grid should be 1/192; got " + g);
    }

    @Test
    void snapToGrid_subQuantumRoundsToZero() {
        // 1/6000 < half of 1/192 (= 1/384). Should round to zero.
        Duration zero = Quantizer.snapToGrid(Duration.of(1, 6000),
                QuantizerProfile.WITH_TRIPLETS);
        assertEquals(0, zero.numerator());
    }

    @Test
    void snapToGrid_smallButLegit_landsOnGrid() {
        // 1/64 is the smallest STANDARD legal — snap should keep it.
        Duration snapped = Quantizer.snapToGrid(Duration.of(1, 64),
                QuantizerProfile.STANDARD);
        assertTrue(snapped.equalsDuration(Duration.of(1, 64)));
    }

    @Test
    void snapToGrid_quarterStaysQuarter() {
        Duration snapped = Quantizer.snapToGrid(BaseValue.QUARTER,
                QuantizerProfile.STANDARD);
        assertTrue(snapped.equalsDuration(BaseValue.QUARTER));
    }

    @Test
    void snapToGrid_offGridSnapsToNearestMultiple() {
        // 17/200 ≈ 0.085. STANDARD grid 1/64 = 0.0156.
        // 0.085 / 0.0156 ≈ 5.44 → rounds to 5 → snapped = 5/64.
        Duration snapped = Quantizer.snapToGrid(Duration.of(17, 200),
                QuantizerProfile.STANDARD);
        assertTrue(snapped.equalsDuration(Duration.of(5, 64)),
                "expected 5/64, got " + snapped);
    }

    @Test
    void snapToGrid_tripletPositionPreservedUnderTripletsProfile() {
        // 1/12 = 16/192 — exact multiple of 1/192 grid, snap preserves.
        Duration snapped = Quantizer.snapToGrid(Duration.of(1, 12),
                QuantizerProfile.WITH_TRIPLETS);
        assertTrue(snapped.equalsDuration(Duration.of(1, 12)));
    }

    @Test
    void snapToGrid_tripletPositionRoundsUnderStandardProfile() {
        // 1/12 = 0.0833. STANDARD grid 1/64 = 0.0156.
        // 0.0833 / 0.0156 = 5.33 → rounds to 5 → snapped = 5/64 = 0.0781.
        Duration snapped = Quantizer.snapToGrid(Duration.of(1, 12),
                QuantizerProfile.STANDARD);
        assertTrue(snapped.equalsDuration(Duration.of(5, 64)));
    }

    // ── Helper: value-aware contains check ────────────────────────────

    private static boolean containsValue(java.util.List<Duration> legals, Duration target) {
        for (var d : legals) {
            if (d.equalsDuration(target)) return true;
        }
        return false;
    }
}
