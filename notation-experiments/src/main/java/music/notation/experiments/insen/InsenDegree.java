package music.notation.experiments.insen;

/**
 * Degrees of the <em>In</em> (陰, "Insen") scale — the "dark" Japanese
 * pentatonic.
 *
 * <p>Interval pattern from the tonic, in semitones: {@code 0, 1, 5, 7, 10}
 * (pattern {@code 1-4-2-3-2}). Degrees map to {@code I, ♭II, IV, V, ♭VII}.
 * Very close to the Phrygian mode's characteristic colours but pentatonic;
 * common in traditional shakuhachi and koto music.</p>
 *
 * <p>Example (C tonic): C, D♭, F, G, B♭.</p>
 */
public enum InsenDegree {
    I(0), II_FLAT(1), IV(5), V(7), VII_FLAT(10);

    private final int semitonesFromTonic;

    InsenDegree(int semitonesFromTonic) {
        this.semitonesFromTonic = semitonesFromTonic;
    }

    public int semitonesFromTonic() { return semitonesFromTonic; }
    public int index() { return ordinal(); }

    public static InsenDegree ofIndex(int index) {
        final var values = values();
        return values[Math.floorMod(index, values.length)];
    }
}
