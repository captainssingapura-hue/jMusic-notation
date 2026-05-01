package music.notation.experiments.chinese.shang;

/**
 * Degrees of the <em>Shang</em> (商) mode — the "commerce" mode of the
 * traditional Chinese pentatonic system.
 *
 * <p>Interval pattern from the tonic, in semitones: {@code 0, 2, 5, 7, 10}
 * (pattern {@code 2-3-2-3-2}). Degrees map to {@code I, II, IV, V, ♭VII} —
 * a suspended / sus-4 pentatonic sound, gentle and open.</p>
 *
 * <p>Example (C tonic): C, D, F, G, B♭.</p>
 */
public enum ShangDegree {
    I(0), II(2), IV(5), V(7), VII_FLAT(10);

    private final int semitonesFromTonic;

    ShangDegree(int semitonesFromTonic) {
        this.semitonesFromTonic = semitonesFromTonic;
    }

    public int semitonesFromTonic() { return semitonesFromTonic; }
    public int index() { return ordinal(); }

    public static ShangDegree ofIndex(int index) {
        final var values = values();
        return values[Math.floorMod(index, values.length)];
    }
}
