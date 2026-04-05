package music.notation.phrase;

import music.notation.event.ChordEvent;

import java.util.List;

public record ChordPhrase(List<ChordEvent> chords, PhraseMarking marking) implements Phrase {
    public ChordPhrase {
        if (chords.isEmpty()) {
            throw new IllegalArgumentException("ChordPhrase must contain at least one chord");
        }
        chords = List.copyOf(chords);
    }
}
