package music.notation.phrase;

import music.notation.duration.Duration;

/**
 * A single timed syllable in a lyric line.
 *
 * <p>The {@code syllable} is the text to display (a single character,
 * word, or syllable fragment). The {@code duration} is the time the
 * syllable is held — typically matching the note it's sung on.</p>
 *
 * <p>An empty syllable ({@code ""}) represents an instrumental break
 * or rest where no text is displayed but time still advances.</p>
 */
public record LyricEvent(String syllable, Duration duration) {

    public LyricEvent {
        if (syllable == null) throw new NullPointerException("syllable");
        if (duration == null) throw new NullPointerException("duration");
    }
}
