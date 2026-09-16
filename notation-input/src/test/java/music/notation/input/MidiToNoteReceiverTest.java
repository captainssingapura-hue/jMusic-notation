package music.notation.input;

import music.notation.duration.Duration;
import music.notation.performance.PitchedNote;
import org.junit.jupiter.api.Test;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.ShortMessage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure-logic tests for {@link MidiToNoteReceiver}. Feed it synthetic
 * {@link ShortMessage} streams and verify the emitted note events
 * — onsets, durations, velocities — match the expected sequence.
 *
 * <p>Tests drive the receiver synchronously (no MIDI device involved),
 * so they run on every platform without hardware.</p>
 */
class MidiToNoteReceiverTest {

    /**
     * The receiver projects session-relative ms onto a fixed 120 bpm grid
     * ({@code Duration.of(ms, 2000)}: one quarter = 500 ms). Mirror that
     * projection so wall-clock expectations stay readable in ms.
     */
    private static Duration ms(long ms) { return Duration.of(ms, 2000); }

    // ── Basic note pairing ───────────────────────────────────────────

    @Test
    void singleNoteOnOff_emitsOneCompletedNote() throws Exception {
        RecordingListener events = new RecordingListener();
        MidiToNoteReceiver rx = new MidiToNoteReceiver(events);

        rx.send(noteOn(0, 60, 90), -1);
        sleep(10);
        rx.send(noteOff(0, 60), -1);

        assertEquals(1, events.starts.size());
        assertEquals(60, events.starts.get(0).midi);
        assertEquals(90, events.starts.get(0).velocity);

        assertEquals(1, events.completed.size());
        PitchedNote n = events.completed.get(0);
        assertEquals(60, n.midi());
        assertTrue(n.duration().compareDuration(ms(1)) >= 0, "duration must be positive");
        assertEquals(90, events.completedVelocities.get(0));
    }

    @Test
    void noteOnVelocityZero_treatedAsNoteOff() throws Exception {
        RecordingListener events = new RecordingListener();
        MidiToNoteReceiver rx = new MidiToNoteReceiver(events);

        rx.send(noteOn(0, 64, 100), -1);
        sleep(5);
        // NOTE_ON with velocity 0 is the MIDI-1.0 convention for note-off
        // on running-status keyboards; must close the open note.
        rx.send(noteOn(0, 64, 0), -1);

        assertEquals(1, events.completed.size());
        assertEquals(64, events.completed.get(0).midi());
    }

    // ── Multi-note overlap ───────────────────────────────────────────

    @Test
    void simultaneousNotes_eachTrackedIndependently() throws Exception {
        RecordingListener events = new RecordingListener();
        MidiToNoteReceiver rx = new MidiToNoteReceiver(events);

        rx.send(noteOn(0, 60, 80), -1);
        rx.send(noteOn(0, 64, 80), -1);
        rx.send(noteOn(0, 67, 80), -1);
        sleep(5);
        rx.send(noteOff(0, 60), -1);
        rx.send(noteOff(0, 64), -1);
        rx.send(noteOff(0, 67), -1);

        assertEquals(3, events.completed.size());
        // All three midis should be present, regardless of completion order.
        var mids = events.completed.stream().map(PitchedNote::midi).sorted().toList();
        assertEquals(List.of(60, 64, 67), mids);
    }

    @Test
    void differentChannelsOnSamePitch_areIndependent() throws Exception {
        RecordingListener events = new RecordingListener();
        MidiToNoteReceiver rx = new MidiToNoteReceiver(events);

        rx.send(noteOn(0, 60, 80), -1);
        rx.send(noteOn(1, 60, 90), -1);
        sleep(5);
        rx.send(noteOff(0, 60), -1);
        rx.send(noteOff(1, 60), -1);

        assertEquals(2, events.completed.size(),
                "same pitch on different channels must be tracked independently");
    }

    // ── Re-strike of an already-held note ────────────────────────────

    @Test
    void doubleStrike_emitsPreviousAndStartsNew() throws Exception {
        RecordingListener events = new RecordingListener();
        MidiToNoteReceiver rx = new MidiToNoteReceiver(events);

        rx.send(noteOn(0, 60, 90), -1);
        sleep(5);
        // Same key hit again without release — typical sustain-style double-tap.
        rx.send(noteOn(0, 60, 100), -1);
        sleep(5);
        rx.send(noteOff(0, 60), -1);

        assertEquals(2, events.completed.size(),
                "re-strike of held note must close the previous one");
        assertEquals(90,  events.completedVelocities.get(0));
        assertEquals(100, events.completedVelocities.get(1));
    }

    // ── Stray events ─────────────────────────────────────────────────

    @Test
    void noteOffWithoutOn_isIgnored() throws Exception {
        RecordingListener events = new RecordingListener();
        MidiToNoteReceiver rx = new MidiToNoteReceiver(events);
        rx.send(noteOff(0, 60), -1);   // no matching on
        assertTrue(events.completed.isEmpty(),
                "lone note-off without prior on must not emit anything");
    }

    @Test
    void controlChangeAndPitchBend_areIgnored() throws Exception {
        RecordingListener events = new RecordingListener();
        MidiToNoteReceiver rx = new MidiToNoteReceiver(events);

        ShortMessage cc = new ShortMessage();
        cc.setMessage(ShortMessage.CONTROL_CHANGE, 0, /*controller=*/ 7, /*value=*/ 100);
        ShortMessage pb = new ShortMessage();
        pb.setMessage(ShortMessage.PITCH_BEND, 0, 0, 64);

        rx.send(cc, -1);
        rx.send(pb, -1);
        rx.send(noteOn(0, 60, 80), -1);
        sleep(5);
        rx.send(noteOff(0, 60), -1);

        // Only the note pair should produce events.
        assertEquals(1, events.completed.size());
        assertEquals(60, events.completed.get(0).midi());
    }

    // ── Flush behaviour ──────────────────────────────────────────────

    @Test
    void flushCompletesHeldNote() throws Exception {
        RecordingListener events = new RecordingListener();
        MidiToNoteReceiver rx = new MidiToNoteReceiver(events);

        rx.send(noteOn(0, 60, 80), -1);
        // No note-off — user pulls the cable.
        assertTrue(events.completed.isEmpty());
        rx.flush(500);
        assertEquals(1, events.completed.size());
        assertEquals(60, events.completed.get(0).midi());
    }

    @Test
    void closeAlsoFlushes() throws Exception {
        RecordingListener events = new RecordingListener();
        MidiToNoteReceiver rx = new MidiToNoteReceiver(events);

        rx.send(noteOn(0, 60, 80), -1);
        rx.close();
        assertEquals(1, events.completed.size(),
                "close() must flush held notes — no event left in limbo");
    }

    @Test
    void sendAfterCloseIsNoop() throws Exception {
        RecordingListener events = new RecordingListener();
        MidiToNoteReceiver rx = new MidiToNoteReceiver(events);

        rx.close();
        rx.send(noteOn(0, 60, 80), -1);
        rx.send(noteOff(0, 60), -1);

        assertTrue(events.completed.isEmpty(),
                "no events should reach the listener after close()");
    }

    // ── Tick timing ──────────────────────────────────────────────────

    @Test
    void ticksAreSessionRelative() throws Exception {
        // Construct with a known sessionStartNanos to make tick math deterministic.
        long start = System.nanoTime();
        RecordingListener events = new RecordingListener();
        MidiToNoteReceiver rx = new MidiToNoteReceiver(events, start);

        // Sleep > 50 ms so the on-tick is meaningfully > 0.
        sleep(50);
        rx.send(noteOn(0, 60, 80), -1);
        sleep(50);
        rx.send(noteOff(0, 60), -1);

        assertEquals(1, events.completed.size());
        PitchedNote n = events.completed.get(0);
        assertTrue(n.at().compareDuration(ms(40)) >= 0,
                "first event tick must be at least ~50ms after start; got " + n.at());
        assertTrue(n.duration().compareDuration(ms(40)) >= 0,
                "note duration must be at least ~50ms; got " + n.duration());
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private static ShortMessage noteOn(int channel, int pitch, int velocity) throws InvalidMidiDataException {
        ShortMessage sm = new ShortMessage();
        sm.setMessage(ShortMessage.NOTE_ON, channel, pitch, velocity);
        return sm;
    }

    private static ShortMessage noteOff(int channel, int pitch) throws InvalidMidiDataException {
        ShortMessage sm = new ShortMessage();
        sm.setMessage(ShortMessage.NOTE_OFF, channel, pitch, 0);
        return sm;
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }

    private record StartEvent(long tickMs, int midi, int velocity) {}

    private static final class RecordingListener implements NoteInputListener {
        final List<StartEvent>  starts    = new ArrayList<>();
        final List<PitchedNote> completed = new ArrayList<>();
        final List<Integer>     completedVelocities = new ArrayList<>();

        @Override public void onNoteStart(long tickMs, int midi, int velocity) {
            starts.add(new StartEvent(tickMs, midi, velocity));
        }
        @Override public void onNoteCompleted(PitchedNote n, int velocity) {
            completed.add(n);
            completedVelocities.add(velocity);
        }
    }

    // ── Audition / passthrough path ───────────────────────────────────

    @Test
    void fanOutDeliversToBothNoteReceiverAndPassthrough() throws Exception {
        // Wire MidiToNoteReceiver + a spy "audition" Receiver through
        // FanOutReceiver. Every event must reach both — that's exactly
        // how the recorder's audition path works.
        RecordingListener events = new RecordingListener();
        MidiToNoteReceiver noteRx = new MidiToNoteReceiver(events);
        List<ShortMessage> auditionLog = new ArrayList<>();
        javax.sound.midi.Receiver auditionRx = new javax.sound.midi.Receiver() {
            @Override public void send(javax.sound.midi.MidiMessage m, long ts) {
                if (m instanceof ShortMessage sm) auditionLog.add(sm);
            }
            @Override public void close() {}
        };
        FanOutReceiver fan = new FanOutReceiver(noteRx, auditionRx);

        fan.send(noteOn(0, 60, 100), -1);
        sleep(5);
        fan.send(noteOff(0, 60), -1);

        // Note path captured one PitchedNote.
        assertEquals(1, events.completed.size());
        assertEquals(60, events.completed.get(0).midi());
        // Audition path got both raw events.
        assertEquals(2, auditionLog.size(),
                "audition receiver must see every raw MIDI event");
    }
}
