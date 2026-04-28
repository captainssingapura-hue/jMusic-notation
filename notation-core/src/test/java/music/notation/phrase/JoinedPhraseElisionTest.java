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
    void elided_stage1_fitsExactly() {
        // last bar: 48sf audible + 16sf pad. pickup: 16sf pad + 48sf audible.
        // Wait — pickup audible 48sf > trail pad 16sf; would throw. Use smaller pickup.
        // last: 56sf audible + 8sf pad. pickup: 56sf pad + 8sf audible.
        // firstAudible64 = 8 = trailPad64 = 8 → exact fit, residualPad = 0.
        var lastBar  = Bar.of(BAR64,
                PitchNode.of(p(NoteName.C, 4), Duration.ofSixtyFourths(56)),
                pad(8));
        var pickupBr = Bar.of(BAR64, pad(56), eighth(NoteName.D, 4));

        var a = BarPhrase.of(lastBar);
        var b = BarPhrase.of(pickupBr, fullBar(NoteName.E, 4));
        var joined = BarPhrase.join(ConnectingMode.ELIDED, a, b);

        List<Bar> bars = joined.bars();
        // Pickup absorbed → bar count = 1 (merged) + 1 (E bar) = 2 (was 3).
        assertEquals(2, bars.size(), "pickup bar consumed");
        Bar merged = bars.get(0);
        assertEquals(BAR64, merged.expectedSixtyFourths());
        // Merged bar: audible-of-last (1 PitchNode) + audible-of-pickup (1 eighth).
        assertEquals(2, merged.nodes().size());
        assertTrue(merged.nodes().get(0) instanceof PitchNode);
        assertTrue(merged.nodes().get(1) instanceof PitchNode);
        // Second bar is unchanged E bar.
        assertSame(b.bars().get(1), bars.get(1));
    }

    @Test
    void elided_stage1_fitsWithLeftoverPad() {
        // last: 48sf audible + 16sf pad. pickup: 56sf pad + 8sf audible.
        // firstAudible64 = 8, trailPad64 = 16, residualPad = 8.
        var lastBar  = Bar.of(BAR64,
                PitchNode.of(p(NoteName.C, 4), Duration.ofSixtyFourths(48)),
                pad(16));
        var pickupBr = Bar.of(BAR64, pad(56), eighth(NoteName.D, 4));

        var a = BarPhrase.of(lastBar);
        var b = BarPhrase.of(pickupBr);
        var joined = BarPhrase.join(ConnectingMode.ELIDED, a, b);

        List<Bar> bars = joined.bars();
        assertEquals(1, bars.size(), "pickup absorbed; output is one merged bar");
        Bar merged = bars.get(0);
        assertEquals(BAR64, merged.expectedSixtyFourths());
        // Nodes: audible of last (1) + audible of pickup (1) + residual pad (1).
        assertEquals(3, merged.nodes().size());
        assertTrue(merged.nodes().get(0) instanceof PitchNode);
        assertTrue(merged.nodes().get(1) instanceof PitchNode);
        assertTrue(merged.nodes().get(2) instanceof PaddingNode);
        assertEquals(8, ((PaddingNode) merged.nodes().get(2)).duration().sixtyFourths());
    }

    @Test
    void elided_stage1_doesNotFit_throws() {
        // last: 48sf audible + 16sf pad. pickup: 32sf pad + 32sf audible.
        // firstAudible64 = 32 > trailPad64 = 16 → IllegalStateException.
        var lastBar = barWithTrailingPad(16, NoteName.C, 4);
        var pickupBr = pickupBar(32, NoteName.D, 4);
        var joined = BarPhrase.join(ConnectingMode.ELIDED,
                BarPhrase.of(lastBar), BarPhrase.of(pickupBr));

        var ex = assertThrows(IllegalStateException.class, joined::bars);
        assertTrue(ex.getMessage().contains("pickup audible content"),
                "message: " + ex.getMessage());
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
        var lastBarOfA = barWithTrailingPad(8, NoteName.C, 4);
        var pickupB = pickupBar(56, NoteName.D, 4);
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
