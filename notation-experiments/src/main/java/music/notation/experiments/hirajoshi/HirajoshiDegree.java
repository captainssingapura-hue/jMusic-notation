package music.notation.experiments.hirajoshi;

/**
 * Degrees of the Hirajoshi pentatonic scale — a traditional Japanese
 * shamisen/koto tuning with a dark, melancholic colour.
 *
 * <p>Interval pattern from the tonic, in semitones: {@code 0, 2, 3, 7, 8}
 * (pattern {@code 2-1-4-1-4}). Relative to major-scale degrees the
 * Hirajoshi intervals are {@code 1, 2, ♭3, 5, ♭6}.</p>
 *
 * <p>Example (C tonic): C, D, E♭, G, A♭.</p>
 */
public enum HirajoshiDegree {
    I(0),     // tonic
    II(2),    // major second
    III(3),   // minor third (flat 3)
    V(7),     // perfect fifth
    VI(8);    // minor sixth (flat 6)

    private final int semitonesFromTonic;

    HirajoshiDegree(int semitonesFromTonic) {
        this.semitonesFromTonic = semitonesFromTonic;
    }

    public int semitonesFromTonic() {
        return semitonesFromTonic;
    }

    /** Degree index 0..4, useful for modular arithmetic in transformations. */
    public int index() {
        return ordinal();
    }

    /** Resolve a 0..4 index back to the enum value. */
    public static HirajoshiDegree ofIndex(int index) {
        final var values = values();
        final int normalised = Math.floorMod(index, values.length);
        return values[normalised];
    }
}
