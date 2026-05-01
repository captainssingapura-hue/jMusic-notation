package music.notation.phrase;

import music.notation.duration.Duration;
import music.notation.pitch.NoteName;
import music.notation.pitch.Pitch;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static music.notation.duration.BaseValue.WHOLE;
import static org.junit.jupiter.api.Assertions.*;

class LeafPhraseAuxTest {

    private static Bar wholeNote(NoteName n) {
        return Bar.of(64, PitchNode.of(Pitch.of(n, 4), Duration.of(WHOLE)));
    }

    @Test
    void emptyAuxIsDefault() {
        var lp = new LeafPhrase("", List.of(wholeNote(NoteName.C)));
        assertTrue(lp.auxBars().isEmpty());
    }

    @Test
    void sparseExpandsToDenseWithSilentGaps() {
        var lp = new LeafPhrase("", List.of(wholeNote(NoteName.C), wholeNote(NoteName.D), wholeNote(NoteName.E)),
                Map.of("Voice2", Map.of(0, wholeNote(NoteName.G), 2, wholeNote(NoteName.A))));
        var dense = lp.auxBars();
        assertEquals(1, dense.size());
        var v2 = dense.get("Voice2");
        assertEquals(3, v2.size());
        assertEquals(wholeNote(NoteName.G), v2.get(0));
        // Index 1: silent fill.
        assertEquals(64, v2.get(1).expectedSixtyFourths());
        assertEquals(1, v2.get(1).nodes().size());
        assertInstanceOf(PaddingNode.class, v2.get(1).nodes().get(0));
        assertEquals(wholeNote(NoteName.A), v2.get(2));
    }

    @Test
    void blankNameNormalisesToDefault() {
        var sparse = new LinkedHashMap<String, Map<Integer, Bar>>();
        sparse.put("", Map.of(0, wholeNote(NoteName.C)));
        var lp = new LeafPhrase("", List.of(wholeNote(NoteName.C)), sparse);
        assertTrue(lp.auxBarsSparse().containsKey("default"));
    }

    @Test
    void rejectsDuplicateAuxNamesAfterNormalisation() {
        var sparse = new LinkedHashMap<String, Map<Integer, Bar>>();
        sparse.put("default", Map.of(0, wholeNote(NoteName.C)));
        sparse.put("", Map.of(0, wholeNote(NoteName.C)));
        assertThrows(IllegalArgumentException.class,
                () -> new LeafPhrase("", List.of(wholeNote(NoteName.C)), sparse));
    }

    @Test
    void rejectsAuxBarIndexOutOfRange() {
        assertThrows(IllegalArgumentException.class,
                () -> new LeafPhrase("", List.of(wholeNote(NoteName.C)),
                        Map.of("v2", Map.of(5, wholeNote(NoteName.D)))));
    }

    @Test
    void rejectsAuxBarSizeMismatch() {
        var smaller = Bar.of(32, new RestNode(Duration.ofSixtyFourths(32)));
        assertThrows(IllegalArgumentException.class,
                () -> new LeafPhrase("", List.of(wholeNote(NoteName.C)),
                        Map.of("v2", Map.of(0, smaller))));
    }
}
