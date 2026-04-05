package music.notation.phrase;

import music.notation.duration.Duration;

public record RestPhrase(Duration duration, PhraseMarking marking) implements Phrase {
}
