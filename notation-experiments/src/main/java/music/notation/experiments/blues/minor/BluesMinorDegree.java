package music.notation.experiments.blues.minor;

/**
 * Degrees of the <em>Minor Blues</em> scale — the classic six-note blues
 * scale with the signature "blue note" (♭5) that gives blues its
 * characteristic bent / melancholic flavour.
 *
 * <p>Interval pattern from the tonic, in semitones:
 * {@code 0, 3, 5, 6, 7, 10} (pattern {@code 3-2-1-1-3-2}). Degrees map to
 * {@code I, ♭III, IV, ♭V, V, ♭VII} — a minor pentatonic with the passing
 * ♭V added between IV and V. The tension between ♭V and V (a half-step
 * clash resolved by upward pull) is the core of the blues sound.</p>
 *
 * <p>Example (C tonic): C, E♭, F, G♭, G, B♭.</p>
 *
 * <p>This scale is <b>hexatonic</b> ({@code degreeCount() == 6}) — note
 * that {@code ScaleTranspose} requires matching degree counts, so
 * transposing a blues motif into a pentatonic scale (Hirajoshi, Yo,
 * Gong, …) is not supported. Blues ↔ Blues (minor ↔ major) does work.</p>
 */
public enum BluesMinorDegree {
    I(0),
    III_FLAT(3),
    IV(5),
    V_FLAT(6),      // the "blue note"
    V(7),
    VII_FLAT(10);

    private final int semitonesFromTonic;

    BluesMinorDegree(int semitonesFromTonic) {
        this.semitonesFromTonic = semitonesFromTonic;
    }

    public int semitonesFromTonic() { return semitonesFromTonic; }
    public int index() { return ordinal(); }

    public static BluesMinorDegree ofIndex(int index) {
        final var values = values();
        return values[Math.floorMod(index, values.length)];
    }
}
