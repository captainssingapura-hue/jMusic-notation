package music.notation.play;

import org.junit.jupiter.api.Test;

import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Humanizer applies Gaussian-distributed tick jitter to drum-channel
 * note events, preserving note duration (NOTE_ON / matching NOTE_OFF
 * shift together) and leaving non-drum traffic untouched.
 */
class HumanizerSetupTest {

    private static final int PPQ = 480;

    /** Build a minimal drum-channel sequence: 8 kicks at evenly-spaced ticks. */
    private static Sequence drumSequence() throws Exception {
        Sequence seq = new Sequence(Sequence.PPQ, PPQ);
        Track t = seq.createTrack();
        for (int i = 0; i < 8; i++) {
            long tick = i * PPQ;   // one quarter apart
            t.add(noteOn(tick, 9, 36, 100));    // kick
            t.add(noteOff(tick + 50, 9, 36));   // 50-tick duration
        }
        return seq;
    }

    /** Sequence with both drum and non-drum events. */
    private static Sequence mixedSequence() throws Exception {
        Sequence seq = new Sequence(Sequence.PPQ, PPQ);
        Track t = seq.createTrack();
        t.add(noteOn(0,        9, 36, 100));    // kick
        t.add(noteOff(50,      9, 36));
        t.add(noteOn(0,        0, 60, 100));    // melody C4 on channel 0
        t.add(noteOff(PPQ,     0, 60));
        return seq;
    }

    private static MidiEvent noteOn(long tick, int channel, int note, int vel) throws Exception {
        ShortMessage m = new ShortMessage();
        m.setMessage(ShortMessage.NOTE_ON, channel, note, vel);
        return new MidiEvent(m, tick);
    }

    private static MidiEvent noteOff(long tick, int channel, int note) throws Exception {
        ShortMessage m = new ShortMessage();
        m.setMessage(ShortMessage.NOTE_OFF, channel, note, 0);
        return new MidiEvent(m, tick);
    }

    // ── isOff identity ──────────────────────────────────────────────────

    @Test
    void offIsIdentity() throws Exception {
        Sequence src = drumSequence();
        Sequence out = HumanizerSetup.OFF.apply(src);
        assertSame(src, out, "OFF must return the input Sequence by reference");
    }

    // ── Determinism with fixed seed ─────────────────────────────────────

    @Test
    void sameSeedYieldsSameTicks() throws Exception {
        var setup = new HumanizerSetup(20, true, 12345L);
        Sequence a = setup.apply(drumSequence());
        Sequence b = setup.apply(drumSequence());
        Track ta = a.getTracks()[0];
        Track tb = b.getTracks()[0];
        assertEquals(ta.size(), tb.size());
        for (int i = 0; i < ta.size(); i++) {
            assertEquals(ta.get(i).getTick(), tb.get(i).getTick(),
                    "deterministic seed must produce identical ticks (event " + i + ")");
        }
    }

    // ── Note duration preservation ──────────────────────────────────────

    @Test
    void noteOnAndOffShiftByTheSameOffset() throws Exception {
        var setup = new HumanizerSetup(50, true, 7L);   // moderate jitter
        Sequence out = setup.apply(drumSequence());
        Track t = out.getTracks()[0];

        // Recover (note-on tick, note-off tick) pairs by note number.
        Map<Integer, Long> pendingOn = new HashMap<>();
        for (int i = 0; i < t.size(); i++) {
            MidiEvent ev = t.get(i);
            if (!(ev.getMessage() instanceof ShortMessage sm)) continue;  // skip end-of-track meta
            if (sm.getCommand() == ShortMessage.NOTE_ON && sm.getData2() > 0) {
                pendingOn.put(sm.getData1() << 4 | sm.getChannel(), ev.getTick());
            } else if (sm.getCommand() == ShortMessage.NOTE_OFF
                    || (sm.getCommand() == ShortMessage.NOTE_ON && sm.getData2() == 0)) {
                Long onTick = pendingOn.remove(sm.getData1() << 4 | sm.getChannel());
                if (onTick != null) {
                    assertEquals(50L, ev.getTick() - onTick,
                            "note-off must remain 50 ticks after its note-on (preserves duration)");
                }
            }
        }
    }

    // ── Jitter actually moves things ────────────────────────────────────

    @Test
    void looseJitterShiftsAtLeastOneNoteOn() throws Exception {
        // LOOSE = ±20ms; with PPQ=480 at 120bpm, 20ms ≈ 19 ticks. Over 8
        // hits, virtually impossible for every kick to land on its
        // original tick exactly. Catches a regression where jitter is
        // silently zero (e.g. wrong sigma calculation).
        Sequence original = drumSequence();
        Sequence jittered = new HumanizerSetup(20, true, 1L).apply(original);
        Track o = original.getTracks()[0];
        Track j = jittered.getTracks()[0];
        boolean any = false;
        for (int i = 0; i < o.size(); i++) {
            if (o.get(i).getTick() != j.get(i).getTick()) { any = true; break; }
        }
        assertTrue(any, "expected at least one event to be shifted by LOOSE jitter");
        assertNotSame(original, jittered);
    }

    // ── Non-drum events untouched ───────────────────────────────────────

    @Test
    void nonDrumEventsArePreserved() throws Exception {
        var setup = new HumanizerSetup(50, true, 1L);   // drumsOnly
        Sequence src = mixedSequence();
        Sequence out = setup.apply(src);
        Track outT = out.getTracks()[0];

        // Find melody events (channel 0). Their ticks must be unchanged.
        for (int i = 0; i < outT.size(); i++) {
            MidiEvent ev = outT.get(i);
            if (!(ev.getMessage() instanceof ShortMessage sm)) continue;
            if (sm.getChannel() == 0) {
                long expected = (sm.getCommand() == ShortMessage.NOTE_ON) ? 0L : (long) PPQ;
                assertEquals(expected, ev.getTick(),
                        "channel-0 (melody) event must be untouched");
            }
        }
    }

    // ── Tick clamped at zero ────────────────────────────────────────────

    @Test
    void ticksAreClampedAtZero() throws Exception {
        // First kick at tick 0. With LOOSE jitter, it should stay >= 0
        // even if the random offset is negative.
        var setup = new HumanizerSetup(20, true, 1L);
        Sequence out = setup.apply(drumSequence());
        for (int t = 0; t < out.getTracks()[0].size(); t++) {
            assertTrue(out.getTracks()[0].get(t).getTick() >= 0,
                    "all ticks must be ≥ 0 after clamping");
        }
    }

    // ── Different inputs produce different output (sanity) ──────────────

    @Test
    void identityDoesNotMutateInputSequence() throws Exception {
        Sequence src = drumSequence();
        long firstTickBefore = src.getTracks()[0].get(0).getTick();
        Sequence out = new HumanizerSetup(20, true, 1L).apply(src);
        long firstTickAfter = src.getTracks()[0].get(0).getTick();
        assertEquals(firstTickBefore, firstTickAfter,
                "input Sequence must not be mutated");
        assertNotEquals(src, out);
    }
}
