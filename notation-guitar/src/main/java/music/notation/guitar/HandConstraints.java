package music.notation.guitar;

/**
 * What one left hand can do.
 *
 * <ul>
 *   <li>{@code maxFret} — highest fret the arrangement may use.</li>
 *   <li>{@code span} — frets covered without moving the hand: the index
 *       finger at fret h covers h … h+span−1 (4 is one-finger-per-fret).</li>
 *   <li>{@code maxStretch} — widest fretted interval allowed inside one
 *       chord, in frets.</li>
 *   <li>{@code preferredMaxFret} — frets above this are penalised
 *       (see {@link CostWeights#highFret()}).</li>
 *   <li>{@code allowOpenStrings} — whether open strings may be used at all.</li>
 *   <li>{@code foldOutOfRange} — move notes the guitar cannot reach by
 *       octaves into range instead of failing.</li>
 * </ul>
 */
public record HandConstraints(int maxFret, int span, int maxStretch, int preferredMaxFret,
                              boolean allowOpenStrings, boolean foldOutOfRange) {

    public static final HandConstraints DEFAULT = new HandConstraints(22, 4, 5, 12, true, false);

    public HandConstraints {
        if (maxFret < 1 || maxFret > 30) throw new IllegalArgumentException("maxFret out of range: " + maxFret);
        if (span < 1 || span > maxFret) throw new IllegalArgumentException("span out of range: " + span);
        if (maxStretch < span - 1) throw new IllegalArgumentException("maxStretch must be >= span-1");
        if (preferredMaxFret < 0 || preferredMaxFret > maxFret) throw new IllegalArgumentException("preferredMaxFret out of range");
    }
}
