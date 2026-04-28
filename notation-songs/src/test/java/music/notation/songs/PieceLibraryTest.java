package music.notation.songs;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PieceLibraryTest {

    @Test
    void libraryContainsExpectedPieces() {
        // Phase 4c.3: only the four manually-authored survivors remain.
        final List<String> titles = PieceLibrary.titles();
        assertEquals(4, titles.size(),
                "Expected exactly 4 pieces post-4c.3, got " + titles.size() + ": " + titles);
        assertTrue(titles.contains("Für Elise (WoO 59)"));
        assertTrue(titles.contains("The Internationale"));
    }

    @Test
    void everyDiscoveredPieceHasAtLeastOneProvider() {
        for (final var piece : PieceLibrary.pieces()) {
            assertFalse(PieceLibrary.providers(piece.getClass()).isEmpty(),
                    "No providers for " + piece.title());
        }
    }
}
