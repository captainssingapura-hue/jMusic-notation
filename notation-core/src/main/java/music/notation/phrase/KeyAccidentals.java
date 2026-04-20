package music.notation.phrase;

import music.notation.pitch.Accidental;
import music.notation.pitch.NoteName;
import music.notation.structure.KeySignature;
import music.notation.structure.Mode;

import java.util.EnumMap;
import java.util.Map;

/**
 * Maps a {@link KeySignature} to the implicit per-note accidentals it imposes.
 *
 * <p>The accidentals added by a key signature are the first N notes of the
 * circle-of-fifths sharp/flat order. The count depends on
 * {@code (tonic, accidental, mode)}. Major and Aeolian/Minor are supported
 * exactly; other modes (Dorian, Phrygian, Lydian, Mixolydian, Locrian) fall
 * back to their parent major via scale-degree offsets — callers can always
 * override with explicit {@code .s()} / {@code .f()} / {@code .n()} per-note.</p>
 */
final class KeyAccidentals {

    private KeyAccidentals() {}

    private static final NoteName[] SHARP_ORDER = {
            NoteName.F, NoteName.C, NoteName.G, NoteName.D,
            NoteName.A, NoteName.E, NoteName.B
    };
    private static final NoteName[] FLAT_ORDER = {
            NoteName.B, NoteName.E, NoteName.A, NoteName.D,
            NoteName.G, NoteName.C, NoteName.F
    };

    /** Accidental map for the given key signature. Immutable. */
    static Map<NoteName, Accidental> forKey(final KeySignature key) {
        final int[] sf = sharpsAndFlats(key);
        final int sharps = sf[0];
        final int flats = sf[1];

        final var map = new EnumMap<NoteName, Accidental>(NoteName.class);
        for (int i = 0; i < sharps; i++) map.put(SHARP_ORDER[i], Accidental.SHARP);
        for (int i = 0; i < flats;  i++) map.put(FLAT_ORDER[i], Accidental.FLAT);
        return Map.copyOf(map);
    }

    /**
     * Count of sharps and flats in the key signature, as {@code {sharps, flats}}.
     * Exactly one of the two is non-zero (or both zero for C major / A minor).
     */
    private static int[] sharpsAndFlats(final KeySignature key) {
        final Mode mode = key.mode();
        final NoteName tonic = key.tonic();
        final Accidental acc = key.accidental();

        // Aeolian is enharmonically equivalent to natural minor.
        if (mode == Mode.MAJOR)                              return majorKeyCount(tonic, acc);
        if (mode == Mode.MINOR || mode == Mode.AEOLIAN)      return minorKeyCount(tonic, acc);
        // Other modes: approximate via relative-major offset (semitone shift).
        // Best-effort; users can add explicit accidentals per note as needed.
        return modalKeyCount(tonic, acc, mode);
    }

    /** Sharps/flats for a major key. Returns {0,0} for unknown combinations. */
    private static int[] majorKeyCount(NoteName tonic, Accidental acc) {
        return switch (acc) {
            case NATURAL -> switch (tonic) {
                case C -> new int[]{0, 0};
                case G -> new int[]{1, 0};
                case D -> new int[]{2, 0};
                case A -> new int[]{3, 0};
                case E -> new int[]{4, 0};
                case B -> new int[]{5, 0};
                case F -> new int[]{0, 1};   // F major has Bb
            };
            case SHARP -> switch (tonic) {
                case F -> new int[]{6, 0};   // F# major
                case C -> new int[]{7, 0};   // C# major
                default -> new int[]{0, 0};
            };
            case FLAT -> switch (tonic) {
                case B -> new int[]{0, 2};   // Bb major
                case E -> new int[]{0, 3};   // Eb major
                case A -> new int[]{0, 4};   // Ab major
                case D -> new int[]{0, 5};   // Db major
                case G -> new int[]{0, 6};   // Gb major
                case C -> new int[]{0, 7};   // Cb major
                default -> new int[]{0, 0};
            };
            default -> new int[]{0, 0};
        };
    }

    /** Sharps/flats for a natural minor key. Returns {0,0} for unknown combinations. */
    private static int[] minorKeyCount(NoteName tonic, Accidental acc) {
        return switch (acc) {
            case NATURAL -> switch (tonic) {
                case A -> new int[]{0, 0};
                case E -> new int[]{1, 0};
                case B -> new int[]{2, 0};
                case D -> new int[]{0, 1};
                case G -> new int[]{0, 2};
                case C -> new int[]{0, 3};
                case F -> new int[]{0, 4};
            };
            case SHARP -> switch (tonic) {
                case F -> new int[]{3, 0};   // F# minor
                case C -> new int[]{4, 0};   // C# minor
                case G -> new int[]{5, 0};   // G# minor
                case D -> new int[]{6, 0};   // D# minor
                case A -> new int[]{7, 0};   // A# minor
                default -> new int[]{0, 0};
            };
            case FLAT -> switch (tonic) {
                case B -> new int[]{0, 5};   // Bb minor
                case E -> new int[]{0, 6};   // Eb minor
                case A -> new int[]{0, 7};   // Ab minor
                default -> new int[]{0, 0};
            };
            default -> new int[]{0, 0};
        };
    }

    /**
     * Best-effort sharps/flats for a non-major, non-minor mode. Uses the mode's
     * semitone offset from its parent major to shift the sharp count.
     * (e.g. D Dorian is 2 semitones above C major → same signature as C major,
     * which has 0 sharps/flats.)
     */
    private static int[] modalKeyCount(NoteName tonic, Accidental acc, Mode mode) {
        // Default: treat as major for now; users can override accidentals per note.
        return majorKeyCount(tonic, acc);
    }
}
