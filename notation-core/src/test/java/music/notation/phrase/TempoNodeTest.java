package music.notation.phrase;

import music.notation.structure.KeySignature;
import music.notation.structure.Mode;
import music.notation.structure.TimeSignature;
import org.junit.jupiter.api.Test;

import static music.notation.duration.BaseValue.*;
import static music.notation.pitch.NoteName.*;
import static org.junit.jupiter.api.Assertions.*;

class TempoNodeTest {

    private static final KeySignature KEY = new KeySignature(C, Mode.MAJOR);
    private static final TimeSignature TS = new TimeSignature(4, 4);

    @Test
    void tempoChangeNodeRejectsZeroBpm() {
        assertThrows(IllegalArgumentException.class, () -> new TempoChangeNode(0));
    }

    @Test
    void tempoChangeNodeRejectsNegativeBpm() {
        assertThrows(IllegalArgumentException.class, () -> new TempoChangeNode(-1));
    }

    @Test
    void tempoTransitionEndRejectsZeroBpm() {
        assertThrows(IllegalArgumentException.class,
                () -> new TempoTransitionEndNode(0, TransitionMethod.LINEAR));
    }

    @Test
    void tempoNodesHaveZeroDuration() {
        assertEquals(0, Bar.nodeSixtyFourths(new TempoChangeNode(120)));
        assertEquals(0, Bar.nodeSixtyFourths(new TempoTransitionStartNode()));
        assertEquals(0, Bar.nodeSixtyFourths(new TempoTransitionEndNode(80, TransitionMethod.LINEAR)));
    }

    @Test
    void builderTempoAddsNodeToBar() {
        var P = StaffPhraseBuilder.in(KEY, TS, QUARTER);
        var phrase = P.bar().tempo(120).o4(C).o4(D).o4(E).o4(F)
                .build(new PhraseMarking(PhraseConnection.ATTACCA, true));

        // First node should be the TempoChangeNode
        assertInstanceOf(TempoChangeNode.class, phrase.nodes().getFirst());
        assertEquals(120, ((TempoChangeNode) phrase.nodes().getFirst()).bpm());
    }

    @Test
    void builderTransitionStartEndAddsNodes() {
        var P = StaffPhraseBuilder.in(KEY, TS, QUARTER);
        var phrase = P
                .bar().transitionStart().o4(C).o4(D).o4(E).o4(F)
                .bar().transitionEnd(80).o4(G).o4(A).o4(B).o5(C)
                .build(new PhraseMarking(PhraseConnection.ATTACCA, true));

        // Check transition start is in first bar's nodes
        boolean hasStart = phrase.nodes().stream()
                .anyMatch(n -> n instanceof TempoTransitionStartNode);
        assertTrue(hasStart, "Should contain TempoTransitionStartNode");

        // Check transition end is in second bar's nodes
        boolean hasEnd = phrase.nodes().stream()
                .anyMatch(n -> n instanceof TempoTransitionEndNode);
        assertTrue(hasEnd, "Should contain TempoTransitionEndNode");
    }

    @Test
    void builderConvenienceAliases() {
        var P = StaffPhraseBuilder.in(KEY, TS, QUARTER);
        var phrase = P
                .bar().ritStart().o4(C).o4(D).o4(E).o4(F)
                .bar().rit(60).o4(G).o4(A).o4(B).o5(C)
                .build(new PhraseMarking(PhraseConnection.ATTACCA, true));

        boolean hasStart = phrase.nodes().stream()
                .anyMatch(n -> n instanceof TempoTransitionStartNode);
        boolean hasEnd = phrase.nodes().stream()
                .anyMatch(n -> n instanceof TempoTransitionEndNode te && te.targetBpm() == 60);
        assertTrue(hasStart);
        assertTrue(hasEnd);
    }

    @Test
    void tempoNodePreservedInOverride() {
        var P = StaffPhraseBuilder.in(KEY, TS, QUARTER);
        var original = P.bar().tempo(100).o4(C).o4(D).o4(E).o4(F)
                .bar().o4(G).o4(A).o4(B).o5(C)
                .build(new PhraseMarking(PhraseConnection.ATTACCA, true));

        // Override bar 1 only; bar 0 with tempo node should be preserved
        var overridden = OverlayBuilder.over(original, KEY, TS, QUARTER)
                .at(1, b -> b.o4(A).o4(B).o5(C).o5(D))
                .build();

        var resolved = overridden.resolve();
        assertInstanceOf(TempoChangeNode.class, resolved.nodes().getFirst());
        assertEquals(100, ((TempoChangeNode) resolved.nodes().getFirst()).bpm());
    }
}
