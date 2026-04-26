package music.notation.experiments.blues.major;

/**
 * Degrees of the <em>Major Blues</em> scale — six-note sibling of the
 * minor blues, with the "blue note" inserted between major II and III
 * rather than between IV and V.
 *
 * <p>Interval pattern from the tonic, in semitones:
 * {@code 0, 2, 3, 4, 7, 9} (pattern {@code 2-1-1-3-2-3}). Degrees map to
 * {@code I, II, ♭III, III, V, VI} — a major pentatonic with the passing
 * ♭III added between II and III. The ♭III → III chromatic lean is the
 * signature "Memphis blues" / "country blues" sound; much sunnier than
 * the minor blues, but still unmistakably blues.</p>
 *
 * <p>Example (C tonic): C, D, E♭, E, G, A.</p>
 *
 * <p>Hexatonic ({@code degreeCount() == 6}). Compatible with
 * {@link music.notation.experiments.blues.minor.BluesMinorNote Blues Minor}
 * via {@code ScaleTranspose}; not compatible with pentatonic scales.</p>
 */
public enum BluesMajorDegree {
    I(0),
    II(2),
    III_FLAT(3),    // the major-blues "blue note"
    III(4),
    V(7),
    VI(9);

    private final int semitonesFromTonic;

    BluesMajorDegree(int semitonesFromTonic) {
        this.semitonesFromTonic = semitonesFromTonic;
    }

    public int semitonesFromTonic() { return semitonesFromTonic; }
    public int index() { return ordinal(); }

    public static BluesMajorDegree ofIndex(int index) {
        final var values = values();
        return values[Math.floorMod(index, values.length)];
    }
}
