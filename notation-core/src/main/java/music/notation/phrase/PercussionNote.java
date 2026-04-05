package music.notation.phrase;

import music.notation.duration.Duration;
import music.notation.event.PercussionSound;

public record PercussionNote(PercussionSound sound, Duration duration) implements PhraseNode {
}
