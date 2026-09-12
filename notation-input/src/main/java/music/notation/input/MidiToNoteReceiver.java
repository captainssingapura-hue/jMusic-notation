package music.notation.input;

import music.notation.performance.PitchedNote;

import javax.sound.midi.MidiMessage;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import java.util.HashMap;
import java.util.Map;

/**
 * {@link Receiver} that converts a live stream of MIDI events into
 * {@link NoteInputListener} callbacks. Pairs every NOTE_ON with the
 * matching NOTE_OFF, computes the held duration in milliseconds, and
 * emits a {@link PitchedNote} on the off-event.
 *
 * <h2>What it handles</h2>
 *
 * <ul>
 *   <li><b>NOTE_ON (velocity &gt; 0)</b> — records the onset; fires
 *       {@link NoteInputListener#onNoteStart}. If the same (channel,
 *       pitch) is already held, the previous note is auto-closed first
 *       — same as how most keyboards treat double-tapping.</li>
 *   <li><b>NOTE_ON (velocity == 0)</b> — treated as NOTE_OFF per MIDI
 *       convention.</li>
 *   <li><b>NOTE_OFF</b> — closes the held note, fires
 *       {@link NoteInputListener#onNoteCompleted}.</li>
 *   <li>Other messages (CC, pitch bend, aftertouch, program change,
 *       sysex) are silently ignored in v1.</li>
 * </ul>
 *
 * <h2>Tick timing</h2>
 *
 * <p>The {@code timeStamp} passed by Java's MIDI subsystem is in
 * microseconds relative to the device's start, but is often {@code -1}
 * for hardware controllers (the device doesn't time-stamp events). We
 * use a session-relative wall-clock instead: a {@code long sessionStartNanos}
 * captured at construction, with all emitted ticks in milliseconds
 * from that point.</p>
 *
 * <h2>Threading</h2>
 *
 * <p>{@link #send} is invoked on the MIDI subsystem's worker thread.
 * The held-notes map is accessed from a single thread (the MIDI thread)
 * and from {@link #flush} (typically the UI thread on stop). Access is
 * {@code synchronized} on the map; for keyboard event rates this has
 * zero practical contention but keeps the invariant clear.</p>
 */
public final class MidiToNoteReceiver implements Receiver {

    private final NoteInputListener listener;
    private final long sessionStartNanos;
    /** Key = (channel << 8) | pitch ; value = onset record. */
    private final Map<Integer, OpenNote> openNotes = new HashMap<>();
    private volatile boolean closed;

    public MidiToNoteReceiver(NoteInputListener listener) {
        this(listener, System.nanoTime());
    }

    /** Constructor variant for tests that want to control the session origin. */
    MidiToNoteReceiver(NoteInputListener listener, long sessionStartNanos) {
        this.listener = (listener == null) ? noopListener() : listener;
        this.sessionStartNanos = sessionStartNanos;
    }

    @Override
    public void send(MidiMessage msg, long timeStamp) {
        if (closed || !(msg instanceof ShortMessage sm)) return;
        long tickMs = currentTickMs();
        int cmd = sm.getCommand();
        int channel = sm.getChannel();
        int pitch = sm.getData1();
        int velocity = sm.getData2();

        switch (cmd) {
            case ShortMessage.NOTE_ON -> {
                if (velocity > 0) noteStart(channel, pitch, velocity, tickMs);
                else              noteEnd(channel, pitch, tickMs);
            }
            case ShortMessage.NOTE_OFF -> noteEnd(channel, pitch, tickMs);
            default -> { /* ignore CC, pitch bend, aftertouch, program change, sysex */ }
        }
    }

    /**
     * Close any notes still held by the source (e.g. when the user
     * releases the device while playing). Each held note is emitted
     * with the supplied end-tick. Idempotent.
     */
    public void flush() {
        flush(currentTickMs());
    }

    /** Flush variant with a caller-supplied end tick — useful for tests. */
    public void flush(long endTickMs) {
        synchronized (openNotes) {
            for (OpenNote open : openNotes.values()) {
                emitCompleted(open, endTickMs);
            }
            openNotes.clear();
        }
    }

    @Override
    public void close() {
        closed = true;
        flush();
    }

    /** Session-relative tick in ms since this receiver was constructed. */
    long currentTickMs() {
        return Math.max(0, (System.nanoTime() - sessionStartNanos) / 1_000_000);
    }

    // ── Internals ─────────────────────────────────────────────────────

    private void noteStart(int channel, int pitch, int velocity, long tickMs) {
        int key = (channel << 8) | pitch;
        OpenNote prev;
        synchronized (openNotes) {
            prev = openNotes.put(key, new OpenNote(tickMs, pitch, velocity));
        }
        if (prev != null) {
            // Same (channel, pitch) hit again without a release — emit the
            // previous note with the new onset as its end, matching how
            // most keyboards behave.
            emitCompleted(prev, tickMs);
        }
        listener.onNoteStart(tickMs, pitch, velocity);
    }

    private void noteEnd(int channel, int pitch, long tickMs) {
        int key = (channel << 8) | pitch;
        OpenNote open;
        synchronized (openNotes) {
            open = openNotes.remove(key);
        }
        if (open != null) emitCompleted(open, tickMs);
    }

    private void emitCompleted(OpenNote open, long endTickMs) {
        long durationMs = Math.max(1, endTickMs - open.startTickMs);
        // TODO (post ms→Duration redesign): live MIDI input is ms-native;
        // a proper quantizer would snap these onsets to a musical grid
        // against a reference BPM. For now we project at 120 bpm
        // (ms × 1/2000 of a whole note) so the model stays Duration-only.
        music.notation.duration.Duration at =
                music.notation.duration.Duration.of(open.startTickMs, 2000);
        music.notation.duration.Duration noteDuration =
                music.notation.duration.Duration.of(durationMs, 2000);
        listener.onNoteCompleted(
                new PitchedNote(at, noteDuration, open.pitch),
                open.velocity);
    }

    private record OpenNote(long startTickMs, int pitch, int velocity) {}

    private static NoteInputListener noopListener() {
        return new NoteInputListener() {
            @Override public void onNoteCompleted(PitchedNote n, int v) {}
        };
    }
}
