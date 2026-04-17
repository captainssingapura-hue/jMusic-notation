package music.notation.phrase;

/**
 * Zero-duration marker that sets the playback tempo at this position.
 * Analogous to {@link DynamicNode} for velocity.
 */
public record TempoChangeNode(int bpm) implements PhraseNode {
    public TempoChangeNode {
        if (bpm < 1) throw new IllegalArgumentException("BPM must be positive: " + bpm);
    }
}
