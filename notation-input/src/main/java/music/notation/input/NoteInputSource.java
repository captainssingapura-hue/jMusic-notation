package music.notation.input;

/**
 * Streaming source of {@link music.notation.performance.PitchedNote}s
 * captured from the outside world — vocal mic, MIDI keyboard, audio
 * file, network feed.
 *
 * <h2>Lifecycle</h2>
 *
 * <pre>{@code
 * NoteInputSource src = new VocalMicInputSource(...);
 * src.setListener(myListener);
 * src.start();        // begin capturing on a worker thread
 *   ... events flow into the listener ...
 * src.stop();         // stop capturing; flush any pending note
 * src.close();        // release device handles
 * }</pre>
 *
 * <p>{@link #setListener} may be called before {@link #start} or while
 * the source is running. Setting it to {@code null} suppresses event
 * delivery.</p>
 *
 * <h2>Implementations</h2>
 *
 * <ul>
 *   <li>{@link VocalMicInputSource} — captures from the system
 *       microphone, detects pitch with YIN, segments into notes.</li>
 *   <li>{@code MidiKeyboardInputSource} (future) — translates MIDI
 *       NOTE_ON / NOTE_OFF into the same event stream.</li>
 * </ul>
 */
public interface NoteInputSource extends AutoCloseable {

    /** Begin capturing on the worker thread. Idempotent if already running. */
    void start() throws Exception;

    /**
     * Stop capturing. Any note currently being held is flushed to the
     * listener via {@link NoteInputListener#onNoteCompleted}. Idempotent.
     */
    void stop();

    /** Set or replace the event listener. May be called any time. */
    void setListener(NoteInputListener listener);

    /** True between successful {@link #start} and {@link #stop}. */
    boolean isRunning();

    /**
     * Release device handles. After {@code close}, the source cannot be
     * restarted — create a new instance. Implementations must be safe
     * to {@code close} multiple times.
     */
    @Override
    void close();
}
