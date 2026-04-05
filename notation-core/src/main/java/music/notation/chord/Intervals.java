package music.notation.chord;

import music.notation.pitch.Accidental;
import music.notation.pitch.NoteName;
import music.notation.pitch.Pitch;

/**
 * Package-private utility for computing pitches above a given root
 * by letter-name steps and semitone distance.
 */
final class Intervals {

    private Intervals() {}

    /** Semitone value of each natural note name (C=0 .. B=11). */
    private static final int[] NATURAL_SEMITONES = {0, 2, 4, 5, 7, 9, 11};

    /**
     * Compute the pitch that is {@code semitones} chromatic steps above the
     * given root, spelled with the note name that is {@code letterSteps}
     * diatonic steps above the root letter.
     *
     * <p>For example, a major third above C4 is {@code above(C, NATURAL, 4, 2, 4)}
     * → E4.  A major third above D4 is {@code above(D, NATURAL, 4, 2, 4)} → F♯4.</p>
     */
    static Pitch above(final NoteName root, final Accidental rootAccidental,
                       final int octave, final int letterSteps, final int semitones) {
        final int rootOrdinal = root.ordinal();
        final int targetOrdinal = (rootOrdinal + letterSteps) % 7;
        final int octaveShift = (rootOrdinal + letterSteps) / 7;

        final NoteName targetName = NoteName.values()[targetOrdinal];
        final int targetOctave = octave + octaveShift;

        final int rootSemitone = NATURAL_SEMITONES[rootOrdinal] + semitoneOffset(rootAccidental);
        final int targetNatural = NATURAL_SEMITONES[targetOrdinal] + octaveShift * 12;
        final int delta = (rootSemitone + semitones) - targetNatural;

        final Accidental targetAccidental = fromOffset(delta);
        return Pitch.of(targetName, targetAccidental, targetOctave);
    }

    private static int semitoneOffset(final Accidental acc) {
        return switch (acc) {
            case DOUBLE_FLAT  -> -2;
            case FLAT         -> -1;
            case NATURAL      ->  0;
            case SHARP        ->  1;
            case DOUBLE_SHARP ->  2;
        };
    }

    private static Accidental fromOffset(final int offset) {
        return switch (offset) {
            case -2 -> Accidental.DOUBLE_FLAT;
            case -1 -> Accidental.FLAT;
            case  0 -> Accidental.NATURAL;
            case  1 -> Accidental.SHARP;
            case  2 -> Accidental.DOUBLE_SHARP;
            default -> throw new IllegalArgumentException(
                    "Interval produces an accidental offset out of range (−2..+2): " + offset);
        };
    }
}
