package music.notation.phrase;

/**
 * Zero-duration marker that begins a gradual tempo transition.
 * The current tempo and tick position are captured at interpretation time.
 * Must be paired with a subsequent {@link TempoTransitionEndNode}.
 */
public record TempoTransitionStartNode() implements PhraseNode {}
