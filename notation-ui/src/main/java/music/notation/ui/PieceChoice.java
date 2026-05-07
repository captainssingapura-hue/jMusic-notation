package music.notation.ui;

import music.notation.performance.MusicalImport;
import music.notation.performance.QuantizerProfile;

/**
 * Result of {@link PiecePickerDialog#show}: either the title of a piece
 * from the library, or a freshly-loaded external import (session-only).
 *
 * <p>The {@code Imported} variant carries any {@link MusicalImport} —
 * MIDI, MXL, or a JSON-folder reload — through a single channel.
 * {@code voiceSplit} and {@code profile} are MIDI-specific; for
 * already-concretized sources (MXL / JSON) they are no-ops.</p>
 */
sealed interface PieceChoice {
    /**
     * @param title          the piece's title (matches PieceLibrary).
     * @param providerIndex  which arrangement to select on load (0 = first).
     */
    record Library(String title, int providerIndex) implements PieceChoice {
        public Library(String title) { this(title, 0); }
    }
    /**
     * @param imp           the freshly-loaded import (any {@link MusicalImport}).
     * @param voiceSplit    MIDI-only: if true, run Tier-1 + Tier-2 voice split.
     * @param profile       MIDI-only: quantizer profile (note values recognised).
     */
    record Imported(MusicalImport imp, boolean voiceSplit, QuantizerProfile profile)
            implements PieceChoice {
        /** Convenience: default to no splitting + STANDARD profile. */
        public Imported(MusicalImport imp) {
            this(imp, false, QuantizerProfile.STANDARD);
        }
    }
}
