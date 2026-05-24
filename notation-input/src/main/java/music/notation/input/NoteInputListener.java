package music.notation.input;

import music.notation.performance.PitchedNote;

/**
 * Sink for events emitted by a {@link NoteInputSource}.
 *
 * <h2>Threading</h2>
 *
 * <p>Listener methods are invoked on the input source's worker thread
 * (typically the audio capture thread for {@code VocalMicInputSource},
 * or the MIDI receiver thread for a keyboard input). Implementations
 * that update UI state must marshal back to the UI thread themselves
 * ({@code Platform.runLater} for JavaFX). Listener methods must not
 * block — heavy work belongs on a separate queue/executor.</p>
 *
 * <h2>Event ordering</h2>
 *
 * <p>For any single note: zero or more {@link #onPitchEstimate} calls
 * (live frames), followed by exactly one {@link #onNoteStart} (when the
 * segmenter is confident a note has begun), followed by exactly one
 * {@link #onNoteCompleted} (when the note ends). The
 * {@code onPitchEstimate} stream may continue between notes carrying
 * silence or transient noise — consumers filter as needed via
 * {@link PitchEstimate#hasPitch()}.</p>
 */
public interface NoteInputListener {

    /**
     * Real-time pitch reading for a single audio window. Fires at the
     * detector's frame rate (typically every 20–50 ms) regardless of
     * whether a note is being held. Useful for live UI feedback (a
     * needle that follows the singer's pitch); not required to be
     * acted on for note capture — segmentation runs internally.
     *
     * <p>Default no-op so consumers who only care about completed
     * notes don't need to override.</p>
     */
    default void onPitchEstimate(PitchEstimate estimate) {}

    /**
     * Segmenter has detected a stable pitch and declared a note start.
     * The note's duration is not yet known.
     *
     * @param tickMs    onset time of the note, in ms from session start
     * @param midi      detected MIDI pitch (rounded from the stable
     *                  window's median)
     * @param velocity  initial velocity in {@code [1, 127]}, derived
     *                  from the onset window's RMS amplitude
     */
    default void onNoteStart(long tickMs, int midi, int velocity) {}

    /**
     * A previously-started note has ended. The {@code note} carries
     * tickMs / durationMs / midi; the segmenter's velocity is supplied
     * separately so the listener can route it into a {@code Velocities}
     * side-channel without re-shaping the note record.
     */
    void onNoteCompleted(PitchedNote note, int velocity);
}
