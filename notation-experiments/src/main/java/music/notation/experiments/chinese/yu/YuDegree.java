package music.notation.experiments.chinese.yu;

/**
 * Degrees of the <em>Yu</em> (羽) mode — the "feather" mode of the
 * traditional Chinese pentatonic system.
 *
 * <p>Interval pattern from the tonic, in semitones: {@code 0, 3, 5, 7, 10}
 * (pattern {@code 3-2-2-3-2}). Degrees map to {@code I, ♭III, IV, V,
 * ♭VII} — the familiar minor pentatonic, common in lyrical and sorrowful
 * folk melodies.</p>
 *
 * <p>Example (C tonic): C, E♭, F, G, B♭.</p>
 */
public enum YuDegree {
    I(0), III_FLAT(3), IV(5), V(7), VII_FLAT(10);

    private final int semitonesFromTonic;

    YuDegree(int semitonesFromTonic) {
        this.semitonesFromTonic = semitonesFromTonic;
    }

    public int semitonesFromTonic() { return semitonesFromTonic; }
    public int index() { return ordinal(); }

    public static YuDegree ofIndex(int index) {
        final var values = values();
        return values[Math.floorMod(index, values.length)];
    }
}
