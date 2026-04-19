package music.notation.ui;

/**
 * A single note drawn on the piano-roll.
 *
 * @param voice 0 = main track line; 1..N = {@code N}-th
 *              {@link music.notation.phrase.VoiceOverlay} of the track.
 *              Convenience: {@link #isAux()} returns {@code true} iff {@code voice > 0}.
 */
record NoteRect(long startTick, long endTick, int midiNote, String trackKey, int voice) {
    /** True iff this note came from a voice overlay (not the main line). */
    boolean isAux() {
        return voice > 0;
    }
}
