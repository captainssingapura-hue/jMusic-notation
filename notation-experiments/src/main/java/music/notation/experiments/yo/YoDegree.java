package music.notation.experiments.yo;

/**
 * Degrees of the <em>Yo</em> (陽) scale — the "bright" Japanese pentatonic.
 *
 * <p>Interval pattern from the tonic, in semitones: {@code 0, 2, 5, 7, 9}
 * (pattern {@code 2-3-2-2-3}). Degrees map to major-scale positions
 * {@code I, II, IV, V, VI} — essentially a minor-pentatonic rotation,
 * often used in folk songs ("warabe-uta") and children's songs.</p>
 *
 * <p>Example (C tonic): C, D, F, G, A.</p>
 */
public enum YoDegree {
    I(0), II(2), IV(5), V(7), VI(9);

    private final int semitonesFromTonic;

    YoDegree(int semitonesFromTonic) {
        this.semitonesFromTonic = semitonesFromTonic;
    }

    public int semitonesFromTonic() { return semitonesFromTonic; }
    public int index() { return ordinal(); }

    public static YoDegree ofIndex(int index) {
        final var values = values();
        return values[Math.floorMod(index, values.length)];
    }
}
