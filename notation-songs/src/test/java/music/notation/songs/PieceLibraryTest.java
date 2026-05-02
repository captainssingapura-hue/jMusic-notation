package music.notation.songs;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PieceLibraryTest {

    @Test
    void libraryContainsExpectedPieces() {
        final List<String> titles = PieceLibrary.titles();
        assertTrue(titles.contains("Für Elise (WoO 59)"));
        assertTrue(titles.contains("The Internationale"));
        assertTrue(titles.contains("Träumerei (Op. 15 No. 7)"));
    }

    @Test
    void everyDiscoveredPieceHasAtLeastOneProvider() {
        for (final var piece : PieceLibrary.pieces()) {
            assertFalse(PieceLibrary.providers(piece.getClass()).isEmpty(),
                    "No providers for " + piece.title());
        }
    }
}
