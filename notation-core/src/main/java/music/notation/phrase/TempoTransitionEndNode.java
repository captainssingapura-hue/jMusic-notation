package music.notation.phrase;

/**
 * Zero-duration marker that ends a gradual tempo transition.
 * When reached, the interpreter interpolates tempo steps from the
 * paired {@link TempoTransitionStartNode} to this point using the
 * specified {@link TransitionMethod}.
 */
public record TempoTransitionEndNode(int targetBpm, TransitionMethod method) implements PhraseNode {
    public TempoTransitionEndNode {
        if (targetBpm < 1) throw new IllegalArgumentException("Target BPM must be positive: " + targetBpm);
    }
}
