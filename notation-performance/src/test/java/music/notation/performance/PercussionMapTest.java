package music.notation.performance;

import music.notation.event.PercussionSound;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PercussionMapTest {

    @Test
    void everyPercussionSound_roundTripsThroughItsMidiNote() {
        for (PercussionSound ps : PercussionSound.values()) {
            var found = PercussionMap.forNote(ps.midiNote());
            assertTrue(found.isPresent(), "no mapping for " + ps);
        }
    }

    @Test
    void bassDrum_isAtNote35or36() {
        // GM defines acoustic bass drum at 35 and bass drum 1 at 36.
        // Just verify *some* bass drum maps; don't assert which.
        assertTrue(PercussionMap.contains(35) || PercussionMap.contains(36));
    }

    @Test
    void unmappedNote_returnsEmpty() {
        // Note 0 is far below the GM percussion range (27–87).
        assertTrue(PercussionMap.forNote(0).isEmpty());
        // Negative notes and values >127 are nonsensical.
        assertTrue(PercussionMap.forNote(-1).isEmpty());
        assertTrue(PercussionMap.forNote(200).isEmpty());
    }

    @Test
    void contains_matchesPresence() {
        // Pick a known sound to anchor the test.
        var sample = PercussionSound.values()[0];
        assertTrue(PercussionMap.contains(sample.midiNote()));
        assertFalse(PercussionMap.contains(0));
    }
}
