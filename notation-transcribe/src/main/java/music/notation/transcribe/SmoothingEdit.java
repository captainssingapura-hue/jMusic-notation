package music.notation.transcribe;

/**
 * One change {@link NoteSmoother} made, kept so a report or a view can
 * show what was sung against what is written.
 *
 * @param kind     which pass made it
 * @param startMs  the affected note's span as sung, file time
 * @param endMs    …
 * @param fromMidi pitch the segmenter found
 * @param toMidi   pitch after smoothing (for {@link Kind#BLIP}, the
 *                 pitch of the note that absorbed it)
 */
public record SmoothingEdit(Kind kind, double startMs, double endMs, int fromMidi, int toMidi) {

    public enum Kind {
        /** Out-of-key note sung between semitones, moved to the in-key one. */
        SNAP,
        /** Short note an octave off from both neighbours, folded back. */
        OCTAVE,
        /** Glide fragment or flick, absorbed into a neighbour. */
        BLIP
    }
}
