package music.notation.input;

import javax.sound.midi.MidiMessage;
import javax.sound.midi.Receiver;
import java.util.List;
import java.util.Objects;

/**
 * {@link Receiver} that forwards every incoming MIDI message to a fixed
 * list of downstream receivers. Used by the recorder to route one
 * device's events to both a note-capture path
 * ({@link MidiToNoteReceiver}) and an audition path (a
 * {@link javax.sound.midi.Synthesizer}'s receiver) without opening the
 * device's transmitter twice — which most hardware doesn't allow.
 *
 * <h2>Behaviour</h2>
 *
 * <ul>
 *   <li>{@link #send} dispatches to every wrapped receiver in
 *       construction order. An exception from one receiver does not stop
 *       the others — it's caught and ignored, since one failing receiver
 *       (e.g. a closed synth) shouldn't break note capture.</li>
 *   <li>{@link #close} closes every wrapped receiver, in the order they
 *       were supplied.</li>
 * </ul>
 *
 * <p>Thread-safety: thread-confined. The MIDI subsystem dispatches all
 * events to one thread per receiver; a {@code FanOutReceiver} fans out
 * synchronously on that same thread.</p>
 */
public final class FanOutReceiver implements Receiver {

    private final List<Receiver> receivers;

    public FanOutReceiver(Receiver... receivers) {
        Objects.requireNonNull(receivers, "receivers");
        if (receivers.length == 0) {
            throw new IllegalArgumentException("at least one receiver required");
        }
        for (Receiver r : receivers) Objects.requireNonNull(r, "null receiver in fan-out");
        this.receivers = List.of(receivers);
    }

    @Override
    public void send(MidiMessage msg, long timeStamp) {
        for (Receiver r : receivers) {
            try {
                r.send(msg, timeStamp);
            } catch (Exception ignored) {
                // One bad receiver mustn't take down the others — keeps
                // note capture alive if audition synth crashes / closes.
            }
        }
    }

    @Override
    public void close() {
        for (Receiver r : receivers) {
            try { r.close(); } catch (Exception ignored) {}
        }
    }

    public List<Receiver> receivers() { return receivers; }
}
