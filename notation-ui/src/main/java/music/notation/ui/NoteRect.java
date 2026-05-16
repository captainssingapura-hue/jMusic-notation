package music.notation.ui;

/**
 * A single note drawn on the piano-roll.
 *
 * @param midiNote      the displayed (effective) MIDI value — where the note
 *                      is drawn vertically on the pitch axis and what gets
 *                      played by the synth
 * @param originalMidi  the authored MIDI value <em>before</em> any whole-piece
 *                      transposition. Equal to {@code midiNote} for source-only
 *                      or drum notes; different for shifted notes produced by
 *                      {@code TransposeTransform}. The renderer uses this to
 *                      draw a "ghost" lane at the original pitch alongside the
 *                      primary lane at the shifted pitch.
 * @param trackKey      identity of the owning track for color/grouping
 * @param voice         0 = main track line; 1..N = {@code N}-th
 *                      {@link music.notation.phrase.VoiceOverlay} of the track.
 *                      Convenience: {@link #isAux()} returns {@code true} iff
 *                      {@code voice > 0}.
 * @param isDrum        true when this rectangle represents a drum hit; the
 *                      "midiNote" is then a GM kit selector, not a pitch.
 *                      Transposition skips drum notes entirely — shifting a
 *                      kick (MIDI 36) up 5 semitones would silently re-route
 *                      to a high-tom slot, which is never musically intended.
 */
record NoteRect(long startTick, long endTick, int midiNote, int originalMidi,
                String trackKey, int voice, boolean isDrum) {

    /** Backward-compat: pitched, originalMidi = midiNote. */
    NoteRect(long startTick, long endTick, int midiNote, String trackKey, int voice) {
        this(startTick, endTick, midiNote, midiNote, trackKey, voice, false);
    }

    /** Backward-compat: pitched, both midi fields provided. */
    NoteRect(long startTick, long endTick, int midiNote, int originalMidi,
             String trackKey, int voice) {
        this(startTick, endTick, midiNote, originalMidi, trackKey, voice, false);
    }

    /** True iff this note came from a voice overlay (not the main line). */
    boolean isAux() {
        return voice > 0;
    }

    /**
     * True iff this note is a transposed view — the displayed pitch
     * differs from the original. UI rendering uses this to enable the
     * ghost-original lane. Always false for drum notes (drums are never
     * shifted).
     */
    boolean isShifted() {
        return !isDrum && originalMidi != midiNote;
    }
}
