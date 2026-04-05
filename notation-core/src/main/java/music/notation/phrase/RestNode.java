package music.notation.phrase;

import music.notation.duration.Duration;

public record RestNode(Duration duration) implements PhraseNode {
}
