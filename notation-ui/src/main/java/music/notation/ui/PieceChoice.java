package music.notation.ui;

import music.notation.performance.MidiImport;

/**
 * Result of {@link PiecePickerDialog#show}: either the title of a piece
 * from the library, or a freshly-loaded MIDI import (session-only).
 */
sealed interface PieceChoice {
    /**
     * @param title          the piece's title (matches PieceLibrary).
     * @param providerIndex  which arrangement to select on load (0 = first).
     */
    record Library(String title, int providerIndex) implements PieceChoice {
        public Library(String title) { this(title, 0); }
    }
    record Imported(MidiImport imp) implements PieceChoice {}
}
