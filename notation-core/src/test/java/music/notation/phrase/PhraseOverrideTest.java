package music.notation.phrase;

import music.notation.structure.KeySignature;
import music.notation.structure.Mode;
import music.notation.structure.TimeSignature;
import org.junit.jupiter.api.Test;

import static music.notation.duration.BaseValue.*;
import static music.notation.pitch.NoteName.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the {@link LayeredPhrase} + {@link OverlayBuilder} override system.
 */
class PhraseOverrideTest {

    private static final KeySignature KEY = new KeySignature(C, Mode.MAJOR);
    private static final TimeSignature TS = new TimeSignature(4, 4);

    private StaffPhraseBuilderTyped builder() {
        return StaffPhraseBuilderTyped.in(KEY, TS, QUARTER);
    }

    @Test
    void overridePreservesUnchangedBars() {
        var original = builder()
                .bar().o4(C).o4(D).o4(E).o4(F).done()
                .bar().o4(G).o4(A).o4(B).o5(C).done()
                .bar().o5(D).o5(E).o5(F).o5(G).done()
                .build(new PhraseMarking(PhraseConnection.CAESURA, true));

        assertEquals(3, original.bars().size(), "Source should retain 3 bars");

        // Override only bar 1 (0-indexed), leave bars 0 and 2 intact
        var overridden = OverlayBuilder.over(original, KEY, TS, QUARTER)
                .at(1, b -> b.o4(A).o4(A).o4(A).o4(A))
                .build();

        var resolved = overridden.resolve();
        assertEquals(3, resolved.bars().size());
        assertEquals(original.bars().get(0).nodes(), resolved.bars().get(0).nodes());
        assertNotEquals(original.bars().get(1).nodes(), resolved.bars().get(1).nodes());
        assertEquals(original.bars().get(2).nodes(), resolved.bars().get(2).nodes());
    }

    @Test
    void overrideFirstBar() {
        var original = builder()
                .bar().o4(C).o4(D).o4(E).o4(F).done()
                .bar().o4(G).o4(A).o4(B).o5(C).done()
                .build(new PhraseMarking(PhraseConnection.ATTACCA, true));

        var overridden = OverlayBuilder.over(original, KEY, TS, QUARTER)
                .at(0, b -> b.o5(C).o5(D).o5(E).o5(F))
                .build();

        var resolved = overridden.resolve();
        assertEquals(2, resolved.bars().size());
        assertNotEquals(original.bars().get(0).nodes(), resolved.bars().get(0).nodes());
        assertEquals(original.bars().get(1).nodes(), resolved.bars().get(1).nodes());
    }

    @Test
    void overrideLastBar() {
        var original = builder()
                .bar().o4(C).o4(D).o4(E).o4(F).done()
                .bar().o4(G).o4(A).o4(B).o5(C).done()
                .build(new PhraseMarking(PhraseConnection.CAESURA, true));

        var overridden = OverlayBuilder.over(original, KEY, TS, QUARTER)
                .at(1, b -> b.o5(G).o5(A).o5(B).o5(C))
                .build();

        var resolved = overridden.resolve();
        assertEquals(2, resolved.bars().size());
        assertEquals(original.bars().get(0).nodes(), resolved.bars().get(0).nodes());
        assertNotEquals(original.bars().get(1).nodes(), resolved.bars().get(1).nodes());
    }

    @Test
    void overrideUsesSourceMarking() {
        var marking = new PhraseMarking(PhraseConnection.ATTACCA, true);
        var original = builder()
                .bar().o4(C).o4(D).o4(E).o4(F).done()
                .build(marking);

        var overridden = OverlayBuilder.over(original, KEY, TS, QUARTER)
                .at(0, b -> b.o4(G).o4(A).o4(B).o5(C))
                .build(); // no-arg — uses source marking

        assertEquals(marking, overridden.marking());
    }

    @Test
    void overrideCanOverrideMarking() {
        var original = builder()
                .bar().o4(C).o4(D).o4(E).o4(F).done()
                .build(new PhraseMarking(PhraseConnection.ATTACCA, true));

        var newMarking = new PhraseMarking(PhraseConnection.CAESURA, true);
        var overridden = OverlayBuilder.over(original, KEY, TS, QUARTER)
                .build(newMarking); // marking-only override, no bar changes

        assertEquals(newMarking, overridden.marking());
    }

    @Test
    void overriddenPhraseRetainsBarsForChaining() {
        var original = builder()
                .bar().o4(C).o4(D).o4(E).o4(F).done()
                .bar().o4(G).o4(A).o4(B).o5(C).done()
                .build(new PhraseMarking(PhraseConnection.CAESURA, true));

        // First layer: override bar 0
        var first = OverlayBuilder.over(original, KEY, TS, QUARTER)
                .at(0, b -> b.o5(C).o5(D).o5(E).o5(F))
                .build();

        // Second layer on top: override bar 1
        var second = OverlayBuilder.over(first, KEY, TS, QUARTER)
                .at(1, b -> b.o3(C).o3(D).o3(E).o3(F))
                .build();

        var resolved = second.resolve();
        assertEquals(2, resolved.bars().size());
        // Bar 0 from first layer
        var firstResolved = first.resolve();
        assertEquals(firstResolved.bars().get(0).nodes(), resolved.bars().get(0).nodes());
        // Bar 1 from second layer
        assertNotEquals(firstResolved.bars().get(1).nodes(), resolved.bars().get(1).nodes());
    }

    @Test
    void dynamicOnlyOverride() {
        var original = builder()
                .bar().mp().o4(C).o4(D).o4(E).o4(F).done()
                .bar().o4(G).o4(A).o4(B).o5(C).done()
                .build(new PhraseMarking(PhraseConnection.CAESURA, true));

        // Override bar 0 with ff dynamic but same notes
        var loud = OverlayBuilder.over(original, KEY, TS, QUARTER)
                .at(0, b -> b.ff().o4(C).o4(D).o4(E).o4(F))
                .build();

        var resolved = loud.resolve();
        assertNotEquals(original.bars().get(0).nodes(), resolved.bars().get(0).nodes());
        assertEquals(original.bars().get(1).nodes(), resolved.bars().get(1).nodes());
    }

    @Test
    void overrideIndexOutOfRangeThrows() {
        var original = builder()
                .bar().o4(C).o4(D).o4(E).o4(F).done()
                .build(new PhraseMarking(PhraseConnection.CAESURA, true));

        var overlay = OverlayBuilder.over(original, KEY, TS, QUARTER)
                .at(5, b -> b.o4(C).o4(D).o4(E).o4(F))
                .build();

        assertThrows(IndexOutOfBoundsException.class, overlay::resolve);
    }

    @Test
    void overridingEmptyBarsThrows() {
        var phrase = new MelodicPhrase(
                java.util.List.of(PitchNode.of(
                        music.notation.pitch.Pitch.of(C, music.notation.pitch.Accidental.NATURAL, 4),
                        QUARTER)),
                new PhraseMarking(PhraseConnection.CAESURA, true));

        var overlay = OverlayBuilder.over(phrase, KEY, TS, QUARTER)
                .build();

        assertThrows(IllegalStateException.class, overlay::resolve);
    }

    @Test
    void multipleOverridesInSinglePass() {
        var original = builder()
                .bar().o4(C).o4(D).o4(E).o4(F).done()     // bar 0
                .bar().o4(G).o4(A).o4(B).o5(C).done()     // bar 1
                .bar().o5(D).o5(E).o5(F).o5(G).done()     // bar 2
                .bar().o5(A).o5(B).o5(C).o5(D).done()     // bar 3
                .build(new PhraseMarking(PhraseConnection.CAESURA, true));

        // Override bars 1 and 3, keep 0 and 2
        var overridden = OverlayBuilder.over(original, KEY, TS, QUARTER)
                .at(1, b -> b.o3(C).o3(D).o3(E).o3(F))
                .at(3, b -> b.o3(G).o3(A).o3(B).o3(C))
                .build();

        var resolved = overridden.resolve();
        assertEquals(4, resolved.bars().size());
        assertEquals(original.bars().get(0).nodes(), resolved.bars().get(0).nodes());
        assertNotEquals(original.bars().get(1).nodes(), resolved.bars().get(1).nodes());
        assertEquals(original.bars().get(2).nodes(), resolved.bars().get(2).nodes());
        assertNotEquals(original.bars().get(3).nodes(), resolved.bars().get(3).nodes());
    }
}
