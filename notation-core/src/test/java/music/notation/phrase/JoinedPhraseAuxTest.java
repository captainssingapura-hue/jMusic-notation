package music.notation.phrase;

import music.notation.duration.Duration;
import music.notation.pitch.NoteName;
import music.notation.pitch.Pitch;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static music.notation.duration.BaseValue.WHOLE;
import static org.junit.jupiter.api.Assertions.*;

class JoinedPhraseAuxTest {

    private static Bar wholeNote(NoteName n) {
        return Bar.of(64, PitchNode.of(Pitch.of(n, 4), Duration.of(WHOLE)));
    }

    @Test
    void attaccaConcatenatesAuxPerVoice() {
        var left = Phrase.of(List.of(wholeNote(NoteName.C), wholeNote(NoteName.D)),
                Map.of("v2", Map.of(0, wholeNote(NoteName.G))));
        var right = Phrase.of(List.of(wholeNote(NoteName.E), wholeNote(NoteName.F)),
                Map.of("v2", Map.of(1, wholeNote(NoteName.B))));

        var joined = Phrase.join(ConnectingMode.ATTACCA, left, right);
        assertEquals(4, joined.bars().size());
        var v2 = joined.auxBars().get("v2");
        assertNotNull(v2);
        assertEquals(4, v2.size());
        assertEquals(wholeNote(NoteName.G), v2.get(0));
        // index 1: silent (left has no aux at bar 1)
        assertInstanceOf(PaddingNode.class, v2.get(1).nodes().get(0));
        // index 2: silent (right has no aux at bar 0)
        assertInstanceOf(PaddingNode.class, v2.get(2).nodes().get(0));
        assertEquals(wholeNote(NoteName.B), v2.get(3));
    }

    @Test
    void auxAbsentInOneSideStillStaysAlignedWithPrimary() {
        var left = Phrase.of(List.of(wholeNote(NoteName.C), wholeNote(NoteName.D)));
        var right = Phrase.of(List.of(wholeNote(NoteName.E)),
                Map.of("v2", Map.of(0, wholeNote(NoteName.A))));
        var joined = Phrase.join(ConnectingMode.ATTACCA, left, right);
        var v2 = joined.auxBars().get("v2");
        assertEquals(joined.bars().size(), v2.size());
    }

    @Test
    void elidedKeepsAuxLengthMatchingPrimary() {
        // left ends with trailing pad (audible at start), right starts with a pickup
        // — primary will absorb. Aux should stay length-aligned.
        var leftLast = Bar.of(64,
                PitchNode.of(Pitch.of(NoteName.C, 4), Duration.ofSixtyFourths(16)),
                new PaddingNode(Duration.ofSixtyFourths(48)));
        var rightFirst = Bar.of(64,
                new PaddingNode(Duration.ofSixtyFourths(48)),
                PitchNode.of(Pitch.of(NoteName.D, 4), Duration.ofSixtyFourths(16)));
        var rightSecond = wholeNote(NoteName.E);

        var left = Phrase.of(List.of(leftLast),
                Map.of("v2", Map.of(0, wholeNote(NoteName.G))));
        var right = Phrase.of(List.of(rightFirst, rightSecond),
                Map.of("v2", Map.of(1, wholeNote(NoteName.A))));

        var joined = Phrase.join(ConnectingMode.ELIDED, left, right);
        // Primary absorbed → 1 + 2 - 1 = 2 bars.
        assertEquals(2, joined.bars().size());
        var v2 = joined.auxBars().get("v2");
        assertEquals(2, v2.size(),
                "aux must follow primary length under ELIDED");
    }
}
