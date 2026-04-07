package music.notation.songs;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PieceLibraryTest {

    @Test
    void libraryContainsExpectedPieces() {
        final List<String> titles = PieceLibrary.titles();
        assertTrue(titles.size() >= 7, "Should have at least 7 pieces, got " + titles.size());
        assertTrue(titles.contains("Blue Lotus (蓝莲花)"));
        assertTrue(titles.contains("Pachelbel's Canon"));
        assertTrue(titles.contains("Two Tigers (两只老虎)"));
    }

    @Test
    void everyDiscoveredPieceHasAtLeastOneProvider() {
        for (final var piece : PieceLibrary.pieces()) {
            assertFalse(PieceLibrary.providers(piece.getClass()).isEmpty(),
                    "No providers for " + piece.title());
        }
    }
}
