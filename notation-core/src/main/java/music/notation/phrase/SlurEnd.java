package music.notation.phrase;

/** Zero-duration marker: closes a slur region opened by {@link SlurStart}. */
public record SlurEnd() implements PhraseNode {}
