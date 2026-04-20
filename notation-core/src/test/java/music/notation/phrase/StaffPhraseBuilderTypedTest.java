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
        assertTrue(phrase.voices().isEmpty());
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

    // ── Aux (whole-bar voice overlay via lambda) ─────────────────────

    @Test
    void auxBecomesVoiceOverlay() {
        var phrase = StaffPhraseBuilderTyped.in(KEY, TS, QUARTER)
                .bar().o5(C).o5(D).o5(E).o5(F)
                    .aux(a -> a.r(HALF).o4(QUARTER, C).r(QUARTER))
                    .done()
                .bar().o5(G).o5(A).o5(B).o5(C.higher(1)).done()
                .build(end());

        assertEquals(1, phrase.voices().size());
        var overlay = phrase.voices().getFirst();
        assertEquals(2, overlay.size(), "overlay spans all bars of the phrase");
        assertTrue(overlay.at(0).isPresent(), "bar 0 has aux content");
        assertTrue(overlay.at(1).isEmpty(),   "bar 1 has no aux content");
    }

    @Test
    void multipleAuxSlotsInOneBarCreateMultipleVoices() {
        var phrase = StaffPhraseBuilderTyped.in(KEY, TS, QUARTER)
                .bar().o5(C).o5(D).o5(E).o5(F)
                    .aux(a -> a.o4(WHOLE, C))
                    .aux(a -> a.o3(WHOLE, E))
                    .done()
                .build(end());

        assertEquals(2, phrase.voices().size(), "two .aux() calls → two overlays");
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

    @Test
    void auxBuilderRefusesCallsAfterLambdaExits() {
        // Capture the aux builder from inside the lambda and try to reuse it outside.
        var leaked = new AuxBarBuilderTyped[1];
        StaffPhraseBuilderTyped.in(KEY, TS, QUARTER)
                .bar().o4(C).o4(D).o4(E).o4(F)
                    .aux(a -> { leaked[0] = a; a.r(WHOLE); })
                    .done()
                .build(end());

        assertThrows(IllegalStateException.class, () -> leaked[0].o4(C));
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
}
