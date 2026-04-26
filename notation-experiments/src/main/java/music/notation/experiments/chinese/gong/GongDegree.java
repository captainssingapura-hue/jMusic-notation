package music.notation.experiments.chinese.gong;

/**
 * Degrees of the <em>Gong</em> (宫) mode — the "palace" mode, tonic of the
 * traditional Chinese pentatonic system.
 *
 * <p>Interval pattern from the tonic, in semitones: {@code 0, 2, 4, 7, 9}
 * (pattern {@code 2-2-3-2-3}). Degrees map to {@code I, II, III, V, VI} —
 * this is the familiar major pentatonic scale, widely used in Han folk
 * songs and instrumental pieces.</p>
 *
 * <p>Example (C tonic): C, D, E, G, A.</p>
 */
public enum GongDegree {
    I(0), II(2), III(4), V(7), VI(9);

    private final int semitonesFromTonic;

    GongDegree(int semitonesFromTonic) {
        this.semitonesFromTonic = semitonesFromTonic;
    }

    public int semitonesFromTonic() { return semitonesFromTonic; }
    public int index() { return ordinal(); }

    public static GongDegree ofIndex(int index) {
        final var values = values();
        return values[Math.floorMod(index, values.length)];
    }
}
