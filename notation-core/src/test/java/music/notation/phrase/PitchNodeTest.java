package music.notation.phrase;

import music.notation.duration.Duration;
import music.notation.pitch.Pitch;
import org.junit.jupiter.api.Test;

import java.util.List;

import static music.notation.duration.BaseValue.QUARTER;
import static music.notation.pitch.NoteName.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Sealed-split coverage for {@link PitchNode} and its subtypes.
 */
class PitchNodeTest {

    private static final Pitch C4 = Pitch.of(C, 4);
    private static final Pitch E4 = Pitch.of(E, 4);
    private static final Pitch G4 = Pitch.of(G, 4);
    private static final Duration Q = Duration.of(QUARTER);

    @Test
    void ofProducesSimpleWithOnePitch() {
        var node = PitchNode.of(C4, Q);
        assertInstanceOf(SimplePitchNode.class, node);
        assertEquals(List.of(C4), node.pitches());
        assertFalse(node.tiedToNext());
    }

    @Test
    void polyWithMultiplePitchesProducesPoly() {
        var node = PitchNode.poly(Q, C4, E4, G4);
        assertInstanceOf(PolyPitchNode.class, node);
        assertEquals(3, node.pitches().size());
    }

    @Test
    void polyWithSinglePitchRoutesToSimple() {
        var node = PitchNode.poly(Q, C4);
        assertInstanceOf(SimplePitchNode.class, node);
    }

    @Test
    void polyPitchNodeRequiresAtLeastTwoPitches() {
        assertThrows(IllegalArgumentException.class, () ->
                new PolyPitchNode(List.of(C4), Q, List.of(), false, false));
    }

    @Test
    void simpleWithTiedToNextFlipsFlag() {
        var node = PitchNode.of(C4, Q);
        var tied = node.withTiedToNext();
        assertInstanceOf(SimplePitchNode.class, tied);
        assertTrue(tied.tiedToNext());
        assertFalse(node.tiedToNext(), "original should be unchanged");
    }

    @Test
    void polyWithTiedToNextFlipsFlag() {
        PolyPitchNode node = (PolyPitchNode) PitchNode.poly(Q, C4, E4);
        var tied = node.withTiedToNext();
        assertInstanceOf(PolyPitchNode.class, tied);
        assertTrue(tied.tiedToNext());
    }

    @Test
    void sealedSwitchExhaustiveOverSimpleAndPoly() {
        PitchNode node = PitchNode.of(C4, Q);
        // Compiles iff the sealed split is exhaustive across {Simple, Poly}.
        String kind = switch (node) {
            case SimplePitchNode s -> "simple";
            case PolyPitchNode p -> "poly";
        };
        assertEquals("simple", kind);
    }
}
