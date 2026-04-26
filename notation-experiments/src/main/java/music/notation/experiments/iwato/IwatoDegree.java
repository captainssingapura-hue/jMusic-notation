package music.notation.experiments.iwato;

/**
 * Degrees of the <em>Iwato</em> (岩戸) scale — austere, cave-like; borrows
 * its name from the Amaterasu myth.
 *
 * <p>Interval pattern from the tonic, in semitones: {@code 0, 1, 5, 6, 10}
 * (pattern {@code 1-4-1-4-2}). Degrees map to {@code I, ♭II, IV, ♭V, ♭VII}
 * — a pentatonic slice of the Locrian mode, dissonant and unsettled.</p>
 *
 * <p>Example (C tonic): C, D♭, F, G♭, B♭.</p>
 */
public enum IwatoDegree {
    I(0), II_FLAT(1), IV(5), V_FLAT(6), VII_FLAT(10);

    private final int semitonesFromTonic;

    IwatoDegree(int semitonesFromTonic) {
        this.semitonesFromTonic = semitonesFromTonic;
    }

    public int semitonesFromTonic() { return semitonesFromTonic; }
    public int index() { return ordinal(); }

    public static IwatoDegree ofIndex(int index) {
        final var values = values();
        return values[Math.floorMod(index, values.length)];
    }
}
