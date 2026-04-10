package music.notation.phrase;

import music.notation.duration.Duration;

/**
 * Structural filler that completes a partial bar (pickup or ending).
 *
 * <p>Unlike {@link RestNode}, padding has no inherent musical meaning.
 * Its playback duration depends on other tracks: if no other track has
 * real content at the same position, the padding duration is skipped
 * entirely.  If another track has notes or rests, the padding behaves
 * as silence so the tracks stay aligned.</p>
 */
public record PaddingNode(Duration duration) implements PhraseNode {}
