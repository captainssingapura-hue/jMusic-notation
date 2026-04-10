package music.notation.phrase;

import java.util.List;

/**
 * A phrase of timed lyrics — syllables aligned to the music.
 *
 * <p>Each {@link LyricEvent} carries a syllable and its duration,
 * matching the note it is sung on. The phrase's total duration must
 * align with the corresponding musical phrases so all tracks stay
 * in sync.</p>
 *
 * <p>At playback time, a {@code LyricPhrase} produces no MIDI events;
 * it only advances the tick position. The UI layer reads the syllable
 * timing to display synchronized karaoke-style text.</p>
 */
public record LyricPhrase(List<LyricEvent> syllables, PhraseMarking marking) implements Phrase {

    public LyricPhrase {
        if (syllables.isEmpty()) {
            throw new IllegalArgumentException("LyricPhrase must contain at least one event");
        }
        syllables = List.copyOf(syllables);
    }
}
