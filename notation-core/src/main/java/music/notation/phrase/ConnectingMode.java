package music.notation.phrase;

/**
 * How adjacent children of a {@link JoinedPhrase} stitch together when
 * resolved into a flat bar list.
 *
 * <ul>
 *   <li>{@link #ATTACCA} — pure concatenation. Children's bar lists
 *       are flat-mapped with no merging or trimming.</li>
 *   <li>{@link #ELIDED} — the trailing silence of one child overlaps
 *       with the leading silence of the next. Resolved via two-stage
 *       trim spec: within-bar pickup absorption (pickup audible content
 *       slides into the previous bar's trailing pad), then whole-bar
 *       trim for any remaining leading silence bars. See
 *       {@link JoinedPhrase}.</li>
 * </ul>
 *
 * <p>Composers wanting a small inter-phrase gap (BREATH-style) use
 * {@link #ATTACCA} and let trailing-pad + leading-pad stack naturally,
 * or write an explicit {@link RestNode}/{@link PaddingNode} at the
 * start of the next phrase.</p>
 */
public enum ConnectingMode {
    ATTACCA,
    ELIDED
}
