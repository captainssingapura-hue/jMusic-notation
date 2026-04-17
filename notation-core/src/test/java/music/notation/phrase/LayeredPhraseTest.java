package music.notation.phrase;

import music.notation.structure.KeySignature;
import music.notation.structure.Mode;
import music.notation.structure.TimeSignature;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.TreeMap;

import static music.notation.duration.BaseValue.*;
import static music.notation.pitch.NoteName.*;
import static org.junit.jupiter.api.Assertions.*;

class LayeredPhraseTest {

    private static final KeySignature KEY = new KeySignature(C, Mode.MAJOR);
    private static final TimeSignature TS = new TimeSignature(4, 4);
    private static final PhraseMarking ATTACCA = new PhraseMarking(PhraseConnection.ATTACCA, true);
    private static final PhraseMarking CAESURA = new PhraseMarking(PhraseConnection.CAESURA, true);

    private StaffPhraseBuilder builder() {
        return StaffPhraseBuilder.in(KEY, TS, QUARTER);
    }

    private MelodicPhrase threeBarPhrase() {
        return builder()
                .bar().o4(C).o4(D).o4(E).o4(F)
                .bar().o4(G).o4(A).o4(B).o5(C)
                .bar().o5(D).o5(E).o5(F).o5(G)
                .build(CAESURA);
    }

    @Test
    void singleBarOverrideResolvesCorrectly() {
        var base = threeBarPhrase();
        var overlay = OverlayBuilder.over(base, KEY, TS, QUARTER)
                .at(1, b -> b.o4(A).o4(A).o4(A).o4(A))
                .build();

        var resolved = overlay.resolve();
        assertEquals(3, resolved.bars().size());
        assertEquals(base.bars().get(0).nodes(), resolved.bars().get(0).nodes());
        assertNotEquals(base.bars().get(1).nodes(), resolved.bars().get(1).nodes());
        assertEquals(base.bars().get(2).nodes(), resolved.bars().get(2).nodes());
    }

    @Test
    void multipleBarOverrides() {
        var base = threeBarPhrase();
        var overlay = OverlayBuilder.over(base, KEY, TS, QUARTER)
                .at(0, b -> b.o3(C).o3(D).o3(E).o3(F))
                .at(2, b -> b.o3(G).o3(A).o3(B).o4(C))
                .build();

        var resolved = overlay.resolve();
        assertEquals(3, resolved.bars().size());
        assertNotEquals(base.bars().get(0).nodes(), resolved.bars().get(0).nodes());
        assertEquals(base.bars().get(1).nodes(), resolved.bars().get(1).nodes());
        assertNotEquals(base.bars().get(2).nodes(), resolved.bars().get(2).nodes());
    }

    @Test
    void endingAtAddsPadding() {
        var base = builder()
                .bar().o4(C).o4(D).o4(E).o4(F)
                .bar().o4(G).o4(A).o4(B).o5(C)
                .build(ATTACCA);

        var overlay = OverlayBuilder.over(base, KEY, TS, QUARTER)
                .endingAt(1, b -> b.o4(HALF, G))
                .build();

        var resolved = overlay.resolve();
        assertEquals(2, resolved.bars().size());
        // The ending bar should have padding after the half note
        var endingNodes = resolved.bars().get(1).nodes();
        assertTrue(endingNodes.stream().anyMatch(n -> n instanceof PaddingNode));
    }

    @Test
    void markingInheritedFromBase() {
        var base = threeBarPhrase(); // CAESURA marking
        var overlay = OverlayBuilder.over(base, KEY, TS, QUARTER)
                .at(0, b -> b.o4(A).o4(A).o4(A).o4(A))
                .build();

        assertEquals(CAESURA, overlay.marking());
    }

    @Test
    void markingCanBeOverridden() {
        var base = threeBarPhrase(); // CAESURA marking
        var overlay = OverlayBuilder.over(base, KEY, TS, QUARTER)
                .at(0, b -> b.o4(A).o4(A).o4(A).o4(A))
                .build(ATTACCA);

        assertEquals(ATTACCA, overlay.marking());
    }

    @Test
    void recursiveLayering() {
        var base = threeBarPhrase();

        // First layer: override bar 0
        var layer1 = OverlayBuilder.over(base, KEY, TS, QUARTER)
                .at(0, b -> b.o3(C).o3(D).o3(E).o3(F))
                .build();

        // Second layer on top: override bar 2
        var layer2 = OverlayBuilder.over(layer1, KEY, TS, QUARTER)
                .at(2, b -> b.o3(G).o3(A).o3(B).o4(C))
                .build();

        var resolved = layer2.resolve();
        assertEquals(3, resolved.bars().size());
        // Bar 0 from layer1
        assertNotEquals(base.bars().get(0).nodes(), resolved.bars().get(0).nodes());
        // Bar 1 from base (untouched)
        assertEquals(base.bars().get(1).nodes(), resolved.bars().get(1).nodes());
        // Bar 2 from layer2
        assertNotEquals(base.bars().get(2).nodes(), resolved.bars().get(2).nodes());
    }

    @Test
    void overrideIndexOutOfRangeThrowsOnResolve() {
        var base = builder()
                .bar().o4(C).o4(D).o4(E).o4(F)
                .build(ATTACCA);

        var overlay = new LayeredPhrase(base, new TreeMap<>(Map.of(5, base.bars().getFirst())), TS, ATTACCA);
        assertThrows(IndexOutOfBoundsException.class, overlay::resolve);
    }

    @Test
    void negativeIndexThrowsOnConstruction() {
        var base = builder()
                .bar().o4(C).o4(D).o4(E).o4(F)
                .build(ATTACCA);

        assertThrows(IllegalArgumentException.class, () ->
                new LayeredPhrase(base, new TreeMap<>(Map.of(-1, base.bars().getFirst())), TS, ATTACCA));
    }

    @Test
    void durationMatchesBase() {
        var base = threeBarPhrase();
        var overlay = OverlayBuilder.over(base, KEY, TS, QUARTER)
                .at(1, b -> b.o4(A).o4(A).o4(A).o4(A))
                .build();

        assertEquals(Bar.phraseSixtyFourths(base), Bar.phraseSixtyFourths(overlay));
    }

    @Test
    void emptyOverridesPreserveBase() {
        var base = threeBarPhrase();
        var overlay = new LayeredPhrase(base, new TreeMap<>(), TS, ATTACCA);

        var resolved = overlay.resolve();
        assertEquals(3, resolved.bars().size());
        for (int i = 0; i < 3; i++) {
            assertEquals(base.bars().get(i).nodes(), resolved.bars().get(i).nodes());
        }
    }

    @Test
    void baseWithNoBarStructureThrows() {
        var noBarPhrase = new MelodicPhrase(
                java.util.List.of(NoteNode.of(
                        music.notation.pitch.Pitch.of(C, music.notation.pitch.Accidental.NATURAL, 4),
                        QUARTER)),
                ATTACCA);

        var overlay = new LayeredPhrase(noBarPhrase, new TreeMap<>(), TS, ATTACCA);
        assertThrows(IllegalStateException.class, overlay::resolve);
    }

    @Test
    void atWithBarDefaultDuration() {
        var base = builder()
                .bar().o4(C).o4(D).o4(E).o4(F)
                .build(ATTACCA);

        var overlay = OverlayBuilder.over(base, KEY, TS, QUARTER)
                .at(0, EIGHTH, b -> b.o4(C).o4(D).o4(E).o4(F).o4(G).o4(A).o4(B).o5(C))
                .build();

        var resolved = overlay.resolve();
        assertEquals(1, resolved.bars().size());
    }
}
