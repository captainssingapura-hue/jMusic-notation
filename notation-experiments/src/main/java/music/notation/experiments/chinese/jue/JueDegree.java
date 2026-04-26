package music.notation.experiments.chinese.jue;

/**
 * Degrees of the <em>Jue</em> (角) mode — the "horn" mode of the
 * traditional Chinese pentatonic system.
 *
 * <p>Interval pattern from the tonic, in semitones: {@code 0, 3, 5, 8, 10}
 * (pattern {@code 3-2-3-2-2}). Degrees map to {@code I, ♭III, IV, ♭VI,
 * ♭VII} — a Phrygian-like colouring, somewhat plaintive.</p>
 *
 * <p>Example (C tonic): C, E♭, F, A♭, B♭.</p>
 */
public enum JueDegree {
    I(0), III_FLAT(3), IV(5), VI_FLAT(8), VII_FLAT(10);

    private final int semitonesFromTonic;

    JueDegree(int semitonesFromTonic) {
        this.semitonesFromTonic = semitonesFromTonic;
    }

    public int semitonesFromTonic() { return semitonesFromTonic; }
    public int index() { return ordinal(); }

    public static JueDegree ofIndex(int index) {
        final var values = values();
        return values[Math.floorMod(index, values.length)];
    }
}
