package music.notation.input;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Transmitter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Reusable wrapper around the JDK's {@link MidiDevice} + {@link Transmitter}
 * → {@link Receiver} wiring for MIDI <em>input</em> devices (keyboards,
 * pad controllers, sequencers feeding their output to this app).
 *
 * <h2>Why this exists</h2>
 *
 * <p>Lifted out of {@code SoundbankExplorer} so two consumers — the
 * sound-bank explorer (which forwards events to a {@link
 * javax.sound.midi.Synthesizer}) and the recorder's
 * {@link MidiKeyboardInputSource} (which converts events to
 * {@link music.notation.performance.PitchedNote}s) — can share the
 * device-handling code instead of duplicating it.</p>
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * // Pick a device:
 * List<MidiDevice.Info> inputs = MidiInputBinding.listInputs();
 *
 * // Open one and route incoming events:
 * try (MidiInputBinding binding = MidiInputBinding.open(inputs.get(0), myReceiver)) {
 *     // ... events flow until close ...
 * }
 * }</pre>
 *
 * <p>The {@link Receiver} is the caller's choice — the binding doesn't
 * care what you do with the events. For note capture, pass a
 * {@link MidiToNoteReceiver}; for synth playthrough, pass a
 * {@link Synthesizer#getReceiver()}; for analysis, pass anything that
 * implements {@code Receiver}.</p>
 */
public final class MidiInputBinding implements AutoCloseable {

    private final MidiDevice.Info info;
    private final MidiDevice device;
    private final Transmitter transmitter;
    private final Receiver receiver;
    private volatile boolean open = true;

    private MidiInputBinding(MidiDevice.Info info, MidiDevice device,
                              Transmitter transmitter, Receiver receiver) {
        this.info = info;
        this.device = device;
        this.transmitter = transmitter;
        this.receiver = receiver;
    }

    /**
     * Enumerate every MIDI device on the system that can <em>transmit</em>
     * — i.e. real input devices the user could plug a keyboard into, plus
     * any virtual MIDI ports (loopMIDI, IAC, etc.).
     *
     * <p>Devices that can't transmit ({@link MidiDevice#getMaxTransmitters()
     * == 0}, e.g. pure-output synthesizers) are filtered out. Devices that
     * fail to open during probing are silently skipped — a noisy system
     * driver shouldn't block the whole list.</p>
     */
    public static List<MidiDevice.Info> listInputs() {
        List<MidiDevice.Info> out = new ArrayList<>();
        for (MidiDevice.Info info : MidiSystem.getMidiDeviceInfo()) {
            try {
                MidiDevice dev = MidiSystem.getMidiDevice(info);
                if (dev.getMaxTransmitters() != 0) out.add(info);
            } catch (Exception ignored) { /* skip unreachable devices */ }
        }
        return out;
    }

    /**
     * Open the chosen device, obtain a transmitter, and wire it to
     * {@code receiver}. The returned binding owns the device + transmitter
     * lifecycle; closing it releases both.
     *
     * @throws MidiUnavailableException if the device can't be opened or
     *                                  doesn't expose a transmitter
     */
    public static MidiInputBinding open(MidiDevice.Info info, Receiver receiver)
            throws MidiUnavailableException {
        Objects.requireNonNull(info, "info");
        Objects.requireNonNull(receiver, "receiver");
        MidiDevice device = MidiSystem.getMidiDevice(info);
        if (!device.isOpen()) device.open();
        Transmitter transmitter;
        try {
            transmitter = device.getTransmitter();
        } catch (MidiUnavailableException e) {
            try { device.close(); } catch (Exception ignored) {}
            throw e;
        }
        transmitter.setReceiver(receiver);
        return new MidiInputBinding(info, device, transmitter, receiver);
    }

    public MidiDevice.Info info()  { return info; }
    public Receiver receiver()     { return receiver; }
    public boolean isOpen()        { return open; }

    /**
     * Close the transmitter and the device. Idempotent — safe to call
     * twice. The receiver passed in at open time is not closed here;
     * receivers may be reused across bindings.
     */
    @Override
    public void close() {
        if (!open) return;
        open = false;
        try { transmitter.close(); } catch (Exception ignored) {}
        try { device.close(); }      catch (Exception ignored) {}
    }
}
