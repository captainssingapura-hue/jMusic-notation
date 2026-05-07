package music.notation.phrase;

import music.notation.duration.BarDuration;
import music.notation.duration.BaseValue;
import music.notation.duration.Duration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BarTest {

    private static final BarDuration BAR_4_4 = new BarDuration(4, BaseValue.QUARTER);
    private static final BarDuration BAR_3_4 = new BarDuration(3, BaseValue.QUARTER);
    private static final BarDuration BAR_6_8 = new BarDuration(6, BaseValue.EIGHTH);
    private static final BarDuration BAR_3_8 = new BarDuration(3, BaseValue.EIGHTH);

    private static RestNode rest(BaseValue v) {
        return new RestNode(Duration.of(v));
    }

    // ── BarDuration overload — happy paths ──────────────────────────

    @Test
    void barDurationOverload_44_acceptsFourQuarters() {
        var bar = Bar.of(BAR_4_4, rest(BaseValue.WHOLE));
        assertEquals(64, bar.expectedSixtyFourths());
    }

    @Test
    void barDurationOverload_34_acceptsDottedHalf() {
        var bar = Bar.of(BAR_3_4,
                rest(BaseValue.HALF), rest(BaseValue.QUARTER));
        assertEquals(48, bar.expectedSixtyFourths());
    }

    @Test
    void barDurationOverload_68_acceptsSixEighths() {
        var bar = Bar.of(BAR_6_8,
                rest(BaseValue.EIGHTH), rest(BaseValue.EIGHTH),
                rest(BaseValue.EIGHTH), rest(BaseValue.EIGHTH),
                rest(BaseValue.EIGHTH), rest(BaseValue.EIGHTH));
        assertEquals(48, bar.expectedSixtyFourths());
    }

    @Test
    void barDurationOverload_38_acceptsThreeEighths() {
        var bar = Bar.of(BAR_3_8,
                rest(BaseValue.EIGHTH), rest(BaseValue.EIGHTH), rest(BaseValue.EIGHTH));
        assertEquals(24, bar.expectedSixtyFourths());
    }

    // ── BarDuration overload — validation passes through ────────────

    @Test
    void barDurationOverload_rejectsMismatch() {
        // 3/4 bar but contents add to a whole note (64 sf)
        assertThrows(IllegalArgumentException.class,
                () -> Bar.of(BAR_3_4, rest(BaseValue.WHOLE)));
    }

    // ── silent(BarDuration) overload ────────────────────────────────

    @Test
    void silentBarDurationOverload_4_4() {
        var bar = Bar.silent(BAR_4_4);
        assertEquals(64, bar.expectedSixtyFourths());
        assertEquals(1, bar.nodes().size());
        assertInstanceOf(PaddingNode.class, bar.nodes().get(0));
    }

    @Test
    void silentBarDurationOverload_3_8() {
        var bar = Bar.silent(BAR_3_8);
        assertEquals(24, bar.expectedSixtyFourths());
    }

    // ── 6/8 vs 3/4: same total, both work as a Bar size ─────────────

    @Test
    void sixEightAndThreeFour_bothBuildValidBars() {
        // Different meter character, same physical length.
        var bar68 = Bar.of(BAR_6_8, rest(BaseValue.HALF), rest(BaseValue.QUARTER));
        var bar34 = Bar.of(BAR_3_4, rest(BaseValue.HALF), rest(BaseValue.QUARTER));
        assertEquals(bar68.expectedSixtyFourths(), bar34.expectedSixtyFourths());
        // Bar itself doesn't carry the meter character (yet); that lives in
        // BarDuration. See mixed-meter-plan.md for the future Bar refactor.
    }

    // ── Backward-compat: int overload still works ───────────────────

    @Test
    void intOverload_stillWorks() {
        var bar = Bar.of(64, rest(BaseValue.WHOLE));
        assertEquals(64, bar.expectedSixtyFourths());
    }

    // ── Triplet validation (Phase 2 of duration-rational-plan) ──────

    @Test
    void tripletEighth_threeOfThem_validateAsAQuarter() {
        // Three triplet-eighths should sum to exactly a quarter beat.
        // Under the OLD int-sum logic this would fail (3 × 5 = 15 ≠ 16);
        // under rational sum it succeeds (3 × 1/12 = 1/4 ✓).
        var bd_4_4 = new music.notation.duration.BarDuration(4, BaseValue.QUARTER);
        // Build a 4/4 bar: triplet-eighth × 3 (= quarter) + 3 quarters of rest = 4 quarters total
        var bar = Bar.of(bd_4_4,
                new music.notation.phrase.SimplePitchNode(
                        music.notation.pitch.Pitch.of(music.notation.pitch.NoteName.C, 4),
                        music.notation.duration.Triplet.EIGHTH,
                        java.util.Optional.empty(), java.util.List.of(), false, false),
                new music.notation.phrase.SimplePitchNode(
                        music.notation.pitch.Pitch.of(music.notation.pitch.NoteName.D, 4),
                        music.notation.duration.Triplet.EIGHTH,
                        java.util.Optional.empty(), java.util.List.of(), false, false),
                new music.notation.phrase.SimplePitchNode(
                        music.notation.pitch.Pitch.of(music.notation.pitch.NoteName.E, 4),
                        music.notation.duration.Triplet.EIGHTH,
                        java.util.Optional.empty(), java.util.List.of(), false, false),
                rest(BaseValue.QUARTER), rest(BaseValue.QUARTER), rest(BaseValue.QUARTER));
        assertEquals(64, bar.expectedSixtyFourths());
    }

    @Test
    void quintupletSixteenth_fiveOfThem_validateAsAQuarter() {
        // Quintuplet sixteenths: 5 notes in time of 4 sixteenths = 5-in-quarter.
        // Each = 4/(5*16) = 1/20. Five of them = 5/20 = 1/4 ✓.
        var bd_4_4 = new music.notation.duration.BarDuration(4, BaseValue.QUARTER);
        var quint = music.notation.duration.RawTuplet.ofStandard(5, BaseValue.SIXTEENTH);
        var bar = Bar.of(bd_4_4,
                new music.notation.phrase.RestNode(quint),
                new music.notation.phrase.RestNode(quint),
                new music.notation.phrase.RestNode(quint),
                new music.notation.phrase.RestNode(quint),
                new music.notation.phrase.RestNode(quint),
                rest(BaseValue.QUARTER), rest(BaseValue.QUARTER), rest(BaseValue.QUARTER));
        assertEquals(64, bar.expectedSixtyFourths());
    }

    @Test
    void quintupletEighth_fiveOfThem_validateAsAHalf() {
        // Five quintuplet-eighths = 5/10 = 1/2 (a half beat).
        var bd_4_4 = new music.notation.duration.BarDuration(4, BaseValue.QUARTER);
        var quint = music.notation.duration.RawTuplet.ofStandard(5, BaseValue.EIGHTH);
        var bar = Bar.of(bd_4_4,
                new music.notation.phrase.RestNode(quint),
                new music.notation.phrase.RestNode(quint),
                new music.notation.phrase.RestNode(quint),
                new music.notation.phrase.RestNode(quint),
                new music.notation.phrase.RestNode(quint),
                rest(BaseValue.HALF));
        assertEquals(64, bar.expectedSixtyFourths());
    }

    @Test
    void mismatchedTriplet_throws() {
        // Two triplet-eighths (= 2/12 = 1/6) plus a quarter (1/4) = 5/12 — does NOT fit a 4/4 bar.
        var bd_4_4 = new music.notation.duration.BarDuration(4, BaseValue.QUARTER);
        assertThrows(IllegalArgumentException.class,
                () -> Bar.of(bd_4_4,
                        new music.notation.phrase.RestNode(music.notation.duration.Triplet.EIGHTH),
                        new music.notation.phrase.RestNode(music.notation.duration.Triplet.EIGHTH),
                        rest(BaseValue.QUARTER)));
    }
}
