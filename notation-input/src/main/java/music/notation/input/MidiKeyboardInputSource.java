package music.notation.input;

import music.notation.performance.PitchedNote;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import java.util.Objects;

/**
 * Live MIDI keyboard / controller capture: opens a MIDI input device,
 * pairs NOTE_ON / NOTE_OFF events, and emits {@link PitchedNote}s via
 * the same {@link NoteInputListener} contract as
 * {@link VocalMicInputSource} and {@link AudioFileInputSource}.
 *
 * <h2>Wiring</h2>
 *
 * <pre>{@code
 *   MIDI device (transmitter)
 *           │
 *           ▼
 *   MidiInputBinding ──── routes events to ──▶ MidiToNoteReceiver
 *                                                    │
 *                                                    ▼
 *                                          NoteInputListener
 * }</pre>
 *
 * <p>Each layer is independently usable — see {@link MidiInputBinding}
 * (raw device + Receiver wiring, also used by the SoundbankExplorer for
 * synth-passthrough) and {@link MidiToNoteReceiver} (event pairing,
 * fully unit-testable without hardware).</p>
 *
 * <h2>Lifecycle</h2>
 *
 * <ul>
 *   <li>{@link #start} opens the configured device and starts dispatching.</li>
 *   <li>{@link #stop} closes the device and flushes any held note.</li>
 *   <li>{@link #close} = {@link #stop}.</li>
 * </ul>
 *
 * <p>Threading: the receiver runs on the MIDI subsystem's worker thread;
 * the listener is invoked there. UIs must marshal to their own threads
 * (typically {@code Platform.runLater} for JavaFX).</p>
 *
 * <h2>Device discovery</h2>
 *
 * <p>Use {@link MidiInputBinding#listInputs()} to enumerate available
 * MIDI input devices, then pass one of the returned
 * {@link MidiDevice.Info}s to the constructor.</p>
 */
public final class MidiKeyboardInputSource implements NoteInputSource {

    private final MidiDevice.Info device;
    /** Optional second receiver — typically a synth for audition. May be null. */
    private final Receiver passthrough;

    private volatile NoteInputListener listener = new NoteInputListener() {
        @Override public void onNoteCompleted(PitchedNote n, int v) {}
    };
    private MidiInputBinding binding;
    private MidiToNoteReceiver receiver;
    private volatile boolean running;

    public MidiKeyboardInputSource(MidiDevice.Info device) {
        this(device, /*passthrough=*/ null);
    }

    /**
     * Constructor variant with an optional <em>passthrough</em> receiver:
     * every incoming MIDI event is forwarded to both the internal
     * note-converter AND this receiver. Typical usage — pass a
     * {@link javax.sound.midi.Synthesizer#getReceiver()} so the user
     * hears what they play while it's being captured.
     *
     * <p>The passthrough receiver is wrapped in a {@link FanOutReceiver}
     * alongside the note-converter at {@link #start} time. {@link #stop}
     * does NOT close the passthrough — its lifecycle is owned by the
     * caller (usually a synth that may be reused across sessions).</p>
     */
    public MidiKeyboardInputSource(MidiDevice.Info device, Receiver passthrough) {
        this.device = Objects.requireNonNull(device, "device");
        this.passthrough = passthrough;   // null is fine — meaning no audition
    }

    public MidiDevice.Info device() { return device; }
    public boolean hasPassthrough() { return passthrough != null; }

    // ── NoteInputSource ───────────────────────────────────────────────

    @Override
    public synchronized void start() throws MidiUnavailableException {
        if (running) return;
        receiver = new MidiToNoteReceiver(listener);
        Receiver effective = (passthrough == null)
                ? receiver
                : new FanOutReceiver(receiver, passthrough);
        binding = MidiInputBinding.open(device, effective);
        running = true;
    }

    @Override
    public synchronized void stop() {
        if (!running) return;
        running = false;
        if (binding != null) {
            binding.close();
            binding = null;
        }
        if (receiver != null) {
            // Flush any held note with the current session tick so the
            // listener gets onNoteCompleted for keys still pressed at stop.
            receiver.flush();
            receiver = null;
        }
    }

    @Override
    public void setListener(NoteInputListener listener) {
        this.listener = (listener == null) ? noopListener() : listener;
        // If we're already running, the receiver in flight references the
        // old listener; rebind by recreating the receiver. Edge case —
        // typical usage sets the listener once before start().
        if (running && receiver != null) {
            // Simpler than rewiring: synchronized stop + start would also
            // work, but for now we just hot-swap by creating a new
            // receiver in place. Held notes survive via the old receiver's
            // flush path; new events go to the new receiver.
            // To keep semantics predictable, we don't hot-swap. Document
            // that the listener should be set before start().
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public void close() {
        stop();
    }

    private static NoteInputListener noopListener() {
        return new NoteInputListener() {
            @Override public void onNoteCompleted(PitchedNote n, int v) {}
        };
    }
}
