package music.notation.input;

import org.junit.jupiter.api.Test;

import javax.sound.midi.MidiMessage;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link FanOutReceiver}. No hardware involved — just
 * synthetic ShortMessages dispatched to spy receivers.
 */
class FanOutReceiverTest {

    @Test
    void forwardsToEveryReceiver() throws Exception {
        SpyReceiver a = new SpyReceiver();
        SpyReceiver b = new SpyReceiver();
        SpyReceiver c = new SpyReceiver();

        FanOutReceiver fan = new FanOutReceiver(a, b, c);
        fan.send(noteOn(0, 60, 80), -1);
        fan.send(noteOff(0, 60), -1);

        assertEquals(2, a.received.size());
        assertEquals(2, b.received.size());
        assertEquals(2, c.received.size());
    }

    @Test
    void oneFailingReceiverDoesNotBlockOthers() throws Exception {
        SpyReceiver good1 = new SpyReceiver();
        Receiver bad = new Receiver() {
            @Override public void send(MidiMessage m, long ts) { throw new RuntimeException("kaboom"); }
            @Override public void close() {}
        };
        SpyReceiver good2 = new SpyReceiver();

        FanOutReceiver fan = new FanOutReceiver(good1, bad, good2);
        fan.send(noteOn(0, 60, 80), -1);

        assertEquals(1, good1.received.size(),
                "first receiver still gets the event despite the second throwing");
        assertEquals(1, good2.received.size(),
                "later receivers still get the event despite an earlier one throwing");
    }

    @Test
    void closeClosesEveryReceiver() {
        SpyReceiver a = new SpyReceiver();
        SpyReceiver b = new SpyReceiver();
        FanOutReceiver fan = new FanOutReceiver(a, b);

        fan.close();
        assertTrue(a.closed);
        assertTrue(b.closed);
    }

    @Test
    void rejectsEmptyReceiverList() {
        assertThrows(IllegalArgumentException.class, () -> new FanOutReceiver());
    }

    @Test
    void rejectsNullReceiverInList() {
        SpyReceiver good = new SpyReceiver();
        assertThrows(NullPointerException.class, () -> new FanOutReceiver(good, null));
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private static ShortMessage noteOn(int channel, int pitch, int velocity) throws Exception {
        ShortMessage sm = new ShortMessage();
        sm.setMessage(ShortMessage.NOTE_ON, channel, pitch, velocity);
        return sm;
    }

    private static ShortMessage noteOff(int channel, int pitch) throws Exception {
        ShortMessage sm = new ShortMessage();
        sm.setMessage(ShortMessage.NOTE_OFF, channel, pitch, 0);
        return sm;
    }

    private static final class SpyReceiver implements Receiver {
        final List<MidiMessage> received = new ArrayList<>();
        boolean closed = false;
        @Override public void send(MidiMessage m, long ts) { received.add(m); }
        @Override public void close() { closed = true; }
    }
}
