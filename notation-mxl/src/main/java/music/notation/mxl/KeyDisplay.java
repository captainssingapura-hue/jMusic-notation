package music.notation.mxl;

import music.notation.pitch.Accidental;
import music.notation.structure.KeySignature;
import music.notation.structure.Mode;

/**
 * Tiny helper that turns a {@link KeySignature} into a readable string
 * for CLI / logs. Skips the accidental when natural, and skips the mode
 * label when the source didn't declare one ({@link Mode#NONE}) — so
 * pieces with no {@code <mode>} surface as just their tonic + accidental
 * instead of "G NONE".
 */
public final class KeyDisplay {

    private KeyDisplay() {}

    public static String format(KeySignature key) {
        StringBuilder sb = new StringBuilder();
        sb.append(key.tonic().name());
        if (key.accidental() != Accidental.NATURAL) {
            sb.append('-').append(key.accidental().name().toLowerCase());
        }
        if (key.mode() != Mode.NONE) {
            sb.append(' ').append(key.mode().name().toLowerCase());
        }
        return sb.toString();
    }
}
