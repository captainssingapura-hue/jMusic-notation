package music.notation.phrase;

import music.notation.duration.Duration;
import music.notation.pitch.NoteName;
import music.notation.pitch.StaffPitch;
import org.junit.jupiter.api.Test;

import java.util.List;

import static music.notation.duration.BaseValue.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 4d.1 — locks the {@link JoinedPhrase#bars()} resolution spec.
 *
 * <p>The spec is two-stage for {@link ConnectingMode#ELIDED}:</p>
 * <ol>
 *   <li>Within-bar pickup absorption: pickup audible head slides into
 *       the previous bar's trailing pad; the pickup bar is consumed.</li>
 *   <li>Whole-bar trim: any remaining leading-silence bars in the
 *       next phrase are dropped pairwise against trailing-silence
 *       bars of the previous phrase.</li>
 * </ol>
 */
class JoinedPhraseElisionTest {

    private static final int BAR64 = 64;  // 4/4 worth of 64ths

    private static StaffPitch p(NoteName n, int o) { return StaffPitch.of(n, o); }
    private static PitchNode quarter(NoteName n, int o) {
        return PitchNode.of(p(n, o), Duration.of(QUARTER));
    }
    private static PitchNode eighth(NoteName n, int o) {
        return PitchNode.of(p(n, o), Duration.of(EIGHTH));
    }
    private static PaddingNode pad(int sf) { return new PaddingNode(Duration.ofSixtyFourths(sf)); }

    /** Bar of four quarter-notes at the given pitch (full 64sf). */
    private static Bar fullBar(NoteName n, int o) {
        return Bar.of(BAR64, quarter(n, o), quarter(n, o), quarter(n, o), quarter(n, o));
    }

    /** 4/4 bar ending with `padSf` of trailing PaddingNode. */
    private static Bar barWithTrailingPad(int padSf, NoteName n, int o) {
        int audibleSf = BAR64 - padSf;
        return Bar.of(BAR64,
                PitchNode.of(p(n, o), Duration.ofSixtyFourths(audibleSf)),
                pad(padSf));
    }

    /** Pickup bar: leadSf of leading PaddingNode then audibleSf of audible content. */
    private static Bar pickupBar(int leadSf, NoteName n, int o) {
        int audibleSf = BAR64 - leadSf;
        return Bar.of(BAR64, pad(leadSf), PitchNode.of(p(n, o), Duration.ofSixtyFourths(audibleSf)));
    }

    // ── ATTACCA: pure flatMap ────────────────────────────────────────

    @Test
    void attacca_isPureFlatMap_noTrim() {
        var a = BarPhrase.of(fullBar(NoteName.C, 4));
        var b = BarPhrase.of(fullBar(NoteName.D, 4));
        var joined = BarPhrase.join(ConnectingMode.ATTACCA, a, b);

        List<Bar> bars = joined.bars();
        assertEquals(2, bars.size());
        assertSame(a.bars().get(0), bars.get(0));
        assertSame(b.bars().get(0), bars.get(0 + 1));
    }

    // ── Stage 1: within-bar pickup absorption ────────────────────────

    @Test
    void elided_stage1_exactFit_noMiddleGap() {
        // bar=64, trail=16, lead=48. audibleLast=48, audibleFirst=16.
        // middleGap = 64 - 48 - 16 = 0.
        var lastBar  = Bar.of(BAR64,
                PitchNode.of(p(NoteName.C, 4), Duration.ofSixtyFourths(48)),
                pad(16));
        var pickupBr = Bar.of(BAR64, pad(48),
                PitchNode.of(p(NoteName.D, 4), Duration.ofSixtyFourths(16)));

        var a = BarPhrase.of(lastBar);
        var b = BarPhrase.of(pickupBr, fullBar(NoteName.E, 4));
        var joined = BarPhrase.join(ConnectingMode.ELIDED, a, b);

        List<Bar> bars = joined.bars();
        assertEquals(2, bars.size(), "pickup bar consumed");
        Bar merged = bars.get(0);
        // Layout: [audible_last(48)] [audible_first(16)] — no middle gap.
        assertEquals(2, merged.nodes().size());
        assertTrue(merged.nodes().get(0) instanceof PitchNode);
        assertTrue(merged.nodes().get(1) instanceof PitchNode);
        assertSame(b.bars().get(1), bars.get(1));
    }

    @Test
    void elided_stage1_withMiddleGap() {
        // bar=64, trail=44, lead=48. audibleLast=20, audibleFirst=16.
        // middleGap = 64 - 20 - 16 = 28.
        var lastBar = Bar.of(BAR64,
                PitchNode.of(p(NoteName.C, 4), Duration.ofSixtyFourths(20)),
                pad(44));
        var pickupBr = Bar.of(BAR64, pad(48),
                PitchNode.of(p(NoteName.D, 4), Duration.ofSixtyFourths(16)));

        var joined = BarPhrase.join(ConnectingMode.ELIDED,
                BarPhrase.of(lastBar), BarPhrase.of(pickupBr));

        List<Bar> bars = joined.bars();
        assertEquals(1, bars.size());
        Bar merged = bars.get(0);
        // Layout: [audible_last(20)] [middle_gap(28)] [audible_first(16)] = 3 nodes.
        assertEquals(3, merged.nodes().size());
        assertTrue(merged.nodes().get(0) instanceof PitchNode);
        assertTrue(merged.nodes().get(1) instanceof PaddingNode);
        assertEquals(28, ((PaddingNode) merged.nodes().get(1)).duration().sixtyFourths());
        assertTrue(merged.nodes().get(2) instanceof PitchNode);
    }

    @Test
    void elided_stage1_silentOnlyPickup_dropsBarWithoutMerge() {
        // bar=24 (3/8), trail=12, lead=24, audibleFirst=0.
        // FurElise LH alignment-pickup pattern: silent-only pickup.
        int bar38 = 24;
        var lastBar = Bar.of(bar38,
                PitchNode.of(p(NoteName.C, 4), Duration.ofSixtyFourths(12)),
                pad(12));
        var silentPickup = Bar.of(bar38, pad(bar38));

        var a = BarPhrase.of(lastBar);
        var b = BarPhrase.of(silentPickup, Bar.of(bar38,
                PitchNode.of(p(NoteName.D, 4), Duration.ofSixtyFourths(24))));
        var joined = BarPhrase.join(ConnectingMode.ELIDED, a, b);

        List<Bar> bars = joined.bars();
        assertEquals(2, bars.size(), "silent pickup dropped, last unchanged");
        assertSame(lastBar, bars.get(0));
    }

    @Test
    void elided_stage1_belowMinOverlap_fallsBackToAttacca() {
        // bar=64, trail=8, lead=8. trail+lead=16 < bar=64 → can't merge.
        // No throw — falls through to ATTACCA-like sequential playout.
        var lastBar = Bar.of(BAR64,
                PitchNode.of(p(NoteName.C, 4), Duration.ofSixtyFourths(56)),
                pad(8));
        var pickupBr = Bar.of(BAR64, pad(8),
                PitchNode.of(p(NoteName.D, 4), Duration.ofSixtyFourths(56)));
        var joined = BarPhrase.join(ConnectingMode.ELIDED,
                BarPhrase.of(lastBar), BarPhrase.of(pickupBr));

        List<Bar> bars = joined.bars();
        assertEquals(2, bars.size(), "no merge — both bars survive");
        assertSame(lastBar, bars.get(0));
        assertSame(pickupBr, bars.get(1));
    }

    // ── Stage 2: whole-bar trim ─────────────────────────────────────

    @Test
    void elided_stage2_dropsWholeSilenceBars() {
        // No pickup absorption: last bar has no trailing pad; first bar of next is fully audible.
        // But there's an interstitial whole-silence bar AT the end of A and at the START of B.
        Bar silenceBar = Bar.of(BAR64, pad(BAR64));

        var a = BarPhrase.of(fullBar(NoteName.C, 4), silenceBar);
        var b = BarPhrase.of(silenceBar, fullBar(NoteName.D, 4));
        var joined = BarPhrase.join(ConnectingMode.ELIDED, a, b);

        List<Bar> bars = joined.bars();
        // Stage 1 absorption: last (silenceBar) trailPad = 64; first (silenceBar) leadPad = 64;
        // firstAudible64 = 0 → skip absorption. Stage 2: t=1, l=1, drop 1 leading from B.
        // Result: [fullC, silenceBar, fullD] (3 bars).
        assertEquals(3, bars.size());
    }

    // ── Nested join ─────────────────────────────────────────────────

    @Test
    void nestedJoin_innerResolvesFirst() {
        // join(ELIDED, A, join(ATTACCA, B, C))
        // Inner ATTACCA: B||C. Outer ELIDED applies absorption at A's last vs B's first.
        var lastBarOfA = barWithTrailingPad(16, NoteName.C, 4);
        var pickupB = pickupBar(48, NoteName.D, 4);
        var fullC = fullBar(NoteName.E, 4);

        var a = BarPhrase.of(lastBarOfA);
        var b = BarPhrase.of(pickupB);
        var c = BarPhrase.of(fullC);
        var inner = BarPhrase.join(ConnectingMode.ATTACCA, b, c);
        var outer = BarPhrase.join(ConnectingMode.ELIDED, a, inner);

        List<Bar> bars = outer.bars();
        // pickup of B absorbed into A's last → 2 bars total.
        assertEquals(2, bars.size());
        // Second bar should be unchanged fullC.
        assertSame(fullC, bars.get(1));
    }

    // ── Bar identity preservation ────────────────────────────────────

    @Test
    void attaccaPreservesBarIdentity() {
        Bar b1 = fullBar(NoteName.C, 4);
        Bar b2 = fullBar(NoteName.D, 4);
        var joined = BarPhrase.join(ConnectingMode.ATTACCA,
                BarPhrase.of(b1), BarPhrase.of(b2));

        List<Bar> bars = joined.bars();
        assertSame(b1, bars.get(0));
        assertSame(b2, bars.get(1));
    }
}
