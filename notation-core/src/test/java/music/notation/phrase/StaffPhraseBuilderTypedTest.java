package music.notation.phrase;

import music.notation.structure.KeySignature;
import music.notation.structure.Mode;
import music.notation.structure.TimeSignature;
import org.junit.jupiter.api.Test;

import static music.notation.duration.BaseValue.*;
import static music.notation.pitch.NoteName.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke tests for the typed sub-builder suite: asserts the chain compiles and
 * runs correctly, that one-shot enforcement is active on every stage, and
 * that the output matches {@link StaffPhraseBuilderTyped} for the same input.
 */
class StaffPhraseBuilderTypedTest {

    private static final KeySignature KEY = new KeySignature(C, Mode.MAJOR);
    private static final TimeSignature TS  = new TimeSignature(4, 4);

    private static PhraseMarking attacca() {
        return new PhraseMarking(PhraseConnection.ATTACCA, false);
    }

    private static PhraseMarking end() {
        return new PhraseMarking(PhraseConnection.CAESURA, true);
    }

    // ── Shape: basic bar chain ───────────────────────────────────────

    @Test
    void basicBarChainBuilds() {
        var phrase = StaffPhraseBuilderTyped.in(KEY, TS, QUARTER)
                .bar().o4(C).o4(D).o4(E).o4(F).done()
                .bar().o4(G).o4(A).o4(B).o5(C).done()
                .build(end());

        assertEquals(2, phrase.bars().size());
    }

    // ── Pickup + ending: leading/trailing PaddingNode ────────────────

    @Test
    void pickupPrependsLeadingPadding() {
        var phrase = StaffPhraseBuilderTyped.in(KEY, TS, QUARTER)
                .pickup().o4(QUARTER, G).done()
                .bar().o4(C).o4(D).o4(E).o4(F).done()
                .build(attacca());

        assertEquals(2, phrase.bars().size());
        assertTrue(phrase.bars().getFirst().nodes().getFirst() instanceof PaddingNode,
                "pickup bar must lead with PaddingNode");
    }

    @Test
    void explicitPadAppendsPaddingNode() {
        var phrase = StaffPhraseBuilderTyped.in(KEY, TS, QUARTER)
                .bar().o4(C).o4(D).o4(E).o4(F).done()
                .bar().o4(HALF, C).pad(HALF).done()
                .build(end());

        var endingBar = phrase.bars().get(1);
        assertTrue(endingBar.nodes().getLast() instanceof PaddingNode,
                "explicit .pad(...) must emit a PaddingNode");
    }

    // ── tieNext() across bar boundary ───────────────────────────────

    @Test
    void tieNextWorksAcrossBarLambdas() {
        var phrase = StaffPhraseBuilderTyped.in(KEY, TS, QUARTER)
                .bar().o4(HALF, F).tieNext().o4(HALF, F).done()
                .bar().o4(WHOLE, F).done()
                .build(end());

        // Two bars present; tie-merging collapses same-pitch F's in the node stream.
        assertEquals(2, phrase.bars().size());
    }

    // ── One-shot enforcement ─────────────────────────────────────────

    @Test
    void phraseBuilderRefusesSecondBuild() {
        var p = StaffPhraseBuilderTyped.in(KEY, TS, QUARTER);
        p.bar().o4(C).o4(D).o4(E).o4(F).done().build(end());

        var ex = assertThrows(IllegalStateException.class,
                () -> p.bar().o4(C).o4(D).o4(E).o4(F));
        assertTrue(ex.getMessage().contains("one-shot"),
                "error message should flag one-shot invariant, got: " + ex.getMessage());
    }

    @Test
    void barBuilderRefusesCallsAfterDone() {
        var p = StaffPhraseBuilderTyped.in(KEY, TS, QUARTER);
        var bar = p.bar().o4(C).o4(D).o4(E).o4(F);
        bar.done();

        assertThrows(IllegalStateException.class, () -> bar.o4(G));
        assertThrows(IllegalStateException.class, bar::done);
    }

    // ── Parity with untyped StaffPhraseBuilderTyped ───────────────────────

    @Test
    void outputMatchesUntypedBuilderForSameInput() {
        var typed = StaffPhraseBuilderTyped.in(KEY, TS, QUARTER)
                .bar().mf().o4(C).o4(D).o4(E).o4(F).done()
                .bar().o4(G).o4(A).o4(B).o5(C).done()
                .build(end());

        var untyped = StaffPhraseBuilderTyped.in(KEY, TS, QUARTER)
                .bar().mf().o4(C).o4(D).o4(E).o4(F).done()
                .bar().o4(G).o4(A).o4(B).o5(C).done()
                .build(end());

        assertEquals(untyped.bars().size(), typed.bars().size());
        assertEquals(untyped.nodes(), typed.nodes());
    }

    // ── octaveShift: same notation, another register ────────────────

    @Test
    void octaveShiftTransposesEveryResolvedPitch() {
        var written = StaffPhraseBuilderTyped.in(KEY, TS, QUARTER)
                .bar().o4(C).o4(E, G).grace(B, 3).main(QUARTER, 4, C).o4(D).done()
                .build(end());
        var lower = StaffPhraseBuilderTyped.in(KEY, TS, QUARTER).octaveShift(-1)
                .bar().o4(C).o4(E, G).grace(B, 3).main(QUARTER, 4, C).o4(D).done()
                .build(end());

        var w = written.bars().get(0).nodes();
        var l = lower.bars().get(0).nodes();
        assertEquals(w.size(), l.size());
        for (int i = 0; i < w.size(); i++) {
            if (w.get(i) instanceof PitchNode wp && l.get(i) instanceof PitchNode lp) {
                assertEquals(wp.pitches().size(), lp.pitches().size());
                for (int k = 0; k < wp.pitches().size(); k++) {
                    var a = (music.notation.pitch.StaffPitch) wp.pitches().get(k);
                    var b = (music.notation.pitch.StaffPitch) lp.pitches().get(k);
                    assertEquals(a.noteName(), b.noteName());
                    assertEquals(a.accidental(), b.accidental());
                    assertEquals(a.octave().value() - 1, b.octave().value(), "node " + i);
                }
            }
        }
    }

    @Test
    void octaveShiftMustPrecedeTheFirstBar() {
        var b = StaffPhraseBuilderTyped.in(KEY, TS, QUARTER);
        b.bar().o4(C).o4(D).o4(E).o4(F).done();
        assertThrows(IllegalStateException.class, () -> b.octaveShift(1));
    }
}
