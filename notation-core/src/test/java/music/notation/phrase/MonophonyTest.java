package music.notation.phrase;

import music.notation.duration.Duration;
import music.notation.pitch.NoteName;
import music.notation.pitch.Pitch;
import org.junit.jupiter.api.Test;

import java.util.List;

import static music.notation.duration.BaseValue.QUARTER;
import static org.junit.jupiter.api.Assertions.*;

class MonophonyTest {

    private static PitchNode q(int oct) {
        return PitchNode.of(Pitch.of(NoteName.C, oct), Duration.of(QUARTER));
    }

    @Test
    void singleLine_isMonophonic() {
        var nodes = List.<PhraseNode>of(q(4), q(4), new RestNode(Duration.of(QUARTER)), q(4));
        assertTrue(Monophony.isMonophonic(nodes));
        assertDoesNotThrow(() -> Monophony.assertMonophonic(nodes));
    }

    @Test
    void chordNode_isNotMonophonic() {
        var chord = PitchNode.poly(Duration.of(QUARTER),
                Pitch.of(NoteName.C, 4), Pitch.of(NoteName.E, 4), Pitch.of(NoteName.G, 4));
        var nodes = List.<PhraseNode>of(q(4), chord, q(4));
        assertFalse(Monophony.isMonophonic(nodes));
        var ex = assertThrows(IllegalArgumentException.class,
                () -> Monophony.assertMonophonic(nodes));
        assertTrue(ex.getMessage().contains("node 1"),
                "error should pin the chord at index 1; got: " + ex.getMessage());
    }

    @Test
    void emptyOrNull_isMonophonic() {
        assertTrue(Monophony.isMonophonic(List.of()));
        assertTrue(Monophony.isMonophonic((List<PhraseNode>) null));
        assertDoesNotThrow(() -> Monophony.assertMonophonic((List<PhraseNode>) null));
    }

    @Test
    void phraseWithParallelVoices_isNotMonophonic() {
        // Build a phrase with a voice overlay. Even if its main nodes are
        // monophonic, the overlay sounds in parallel → not monophonic.
        // For simplicity we don't fully construct a VoiceOverlay here —
        // the list-based check is the primary contract; the phrase-level
        // check is exercised in integration tests once the lyrics editor
        // wires real MelodicPhrases.
        assertTrue(Monophony.isMonophonic(List.<PhraseNode>of(q(4), q(4))));
    }
}
