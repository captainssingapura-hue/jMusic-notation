package music.notation.experiments.chinese.zhi;

/**
 * Degrees of the <em>Zhi</em> (徵) mode — the "governance" mode of the
 * traditional Chinese pentatonic system.
 *
 * <p>Interval pattern from the tonic, in semitones: {@code 0, 2, 5, 7, 9}
 * (pattern {@code 2-3-2-2-3}). Degrees map to {@code I, II, IV, V, VI} —
 * an "Egyptian" pentatonic flavour, neither bright-major nor dark-minor.</p>
 *
 * <p>Example (C tonic): C, D, F, G, A.</p>
 */
public enum ZhiDegree {
    I(0), II(2), IV(5), V(7), VI(9);

    private final int semitonesFromTonic;

    ZhiDegree(int semitonesFromTonic) {
        this.semitonesFromTonic = semitonesFromTonic;
    }

    public int semitonesFromTonic() { return semitonesFromTonic; }
    public int index() { return ordinal(); }

    public static ZhiDegree ofIndex(int index) {
        final var values = values();
        return values[Math.floorMod(index, values.length)];
    }
}
