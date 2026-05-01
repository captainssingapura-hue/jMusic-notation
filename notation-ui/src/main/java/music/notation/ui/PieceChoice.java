package music.notation.ui;

import music.notation.performance.MidiImport;

/**
 * Result of {@link PiecePickerDialog#show}: either the title of a piece
 * from the library, or a freshly-loaded MIDI import (session-only).
 */
sealed interface PieceChoice {
    record Library(String title) implements PieceChoice {}
    record Imported(MidiImport imp) implements PieceChoice {}
}
