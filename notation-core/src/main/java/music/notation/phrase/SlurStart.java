package music.notation.phrase;

/** Zero-duration marker: opens a slur region. Same-pitch notes across
 *  a slur are merged into a single sustained note during phrase construction;
 *  different-pitch slurs propagate to the playback layer for legato rendering. */
public record SlurStart() implements PhraseNode {}
