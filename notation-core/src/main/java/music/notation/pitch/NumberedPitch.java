package music.notation.pitch;

/**
 * A pitch in numbered musical notation (简谱 / jiǎnpǔ).
 *
 * <p>The {@code tonic} + {@code tonicAccidental} define what scale degree 1 maps to
 * (e.g. {@code 1=C}, {@code 1=♭E}).
 * Degrees 1–7 follow the major scale intervals from the tonic.
 * The {@code octave} is absolute (4 = middle octave, matching staff notation convention).</p>
 *
 * <p>In printed 简谱, octave is shown as dots above (higher) or below (lower) the digit.</p>
 *
 * @param tonic            the base note name that degree 1 maps to
 * @param tonicAccidental  accidental applied to the tonic (e.g. FLAT for ♭E)
 * @param degree           scale degree, 1–7
 * @param octave           absolute octave
 */
public record NumberedPitch(NoteName tonic, Accidental tonicAccidental, int degree, Octave octave) implements Pitch {

    public NumberedPitch {
        if (degree < 1 || degree > 7) {
            throw new IllegalArgumentException("Degree must be 1–7, got: " + degree);
        }
    }

    /** Major scale semitone offsets for degrees 1–7. */
    private static final int[] MAJOR_SCALE = {0, 2, 4, 5, 7, 9, 11};

    /** Semitone offset of this degree from the tonic. */
    public int semitoneOffset() {
        return MAJOR_SCALE[degree - 1];
    }

    /** Semitone offset of the tonic accidental. */
    public int tonicAccidentalOffset() {
        return switch (tonicAccidental) {
            case DOUBLE_FLAT -> -2;
            case FLAT -> -1;
            case NATURAL -> 0;
            case SHARP -> 1;
            case DOUBLE_SHARP -> 2;
        };
    }

    public static NumberedPitch of(NoteName tonic, int degree, int octave) {
        return new NumberedPitch(tonic, Accidental.NATURAL, degree, new Octave(octave));
    }

    public static NumberedPitch of(NoteName tonic, Accidental tonicAccidental, int degree, int octave) {
        return new NumberedPitch(tonic, tonicAccidental, degree, new Octave(octave));
    }
}
