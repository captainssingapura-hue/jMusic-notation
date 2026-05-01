package music.notation.play;

import org.junit.jupiter.api.Test;

import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

import static org.junit.jupiter.api.Assertions.*;

class SwingSetupTest {

    private static final int PPQ = 480;

    @Test
    void offIsIdentity() {
        var s = SwingSetup.OFF;
        assertTrue(s.isOff());
        assertEquals(0L, s.mapTick(0, PPQ));
        assertEquals(123L, s.mapTick(123, PPQ));
        assertEquals(PPQ, s.mapTick(PPQ, PPQ));
    }

    @Test
    void beatBoundariesUnchanged() {
        var s = SwingSetup.TRIPLET;
        for (int beat = 0; beat < 8; beat++) {
            long t = (long) beat * PPQ;
            assertEquals(t, s.mapTick(t, PPQ),
                    "beat " + beat + " boundary should be stable");
        }
    }

    @Test
    void offBeatPushedLater() {
        var s = SwingSetup.TRIPLET; // ratio = 2/3
        // The off-beat 8th sits at PPQ/2 = 240; under triplet swing it moves to ~PPQ*2/3 = 320.
        long swung = s.mapTick(PPQ / 2, PPQ);
        assertEquals(Math.round(PPQ * (2.0 / 3.0)), swung);
    }

    @Test
    void mappingIsMonotonic() {
        var s = SwingSetup.MEDIUM;
        long prev = -1;
        for (long t = 0; t <= 4L * PPQ; t++) {
            long m = s.mapTick(t, PPQ);
            assertTrue(m >= prev, "non-monotonic at t=" + t);
            prev = m;
        }
    }

    @Test
    void straightHalfMapsToHalfWhenOff() {
        assertEquals(PPQ / 2, SwingSetup.OFF.mapTick(PPQ / 2, PPQ));
    }

    @Test
    void ratioClampedToPlausibleRange() {
        assertEquals(0.5,  new SwingSetup(0.0).ratio());
        assertEquals(0.85, new SwingSetup(2.0).ratio());
    }

    @Test
    void applyToSequencePreservesEventCountAndShiftsOffBeats() throws Exception {
        Sequence src = new Sequence(Sequence.PPQ, PPQ);
        Track t = src.createTrack();
        // Two notes: on-beat (tick 0) and off-beat (tick PPQ/2).
        ShortMessage on1 = new ShortMessage();
        on1.setMessage(ShortMessage.NOTE_ON, 0, 60, 100);
        ShortMessage on2 = new ShortMessage();
        on2.setMessage(ShortMessage.NOTE_ON, 0, 62, 100);
        t.add(new MidiEvent(on1, 0));
        t.add(new MidiEvent(on2, PPQ / 2));

        Sequence out = SwingSetup.TRIPLET.apply(src);
        assertEquals(1, out.getTracks().length);
        Track ot = out.getTracks()[0];
        // Track auto-adds a final EndOfTrack meta — count NOTE_ON only.
        int noteOns = 0;
        long offBeatTick = -1;
        for (int i = 0; i < ot.size(); i++) {
            MidiEvent ev = ot.get(i);
            if (ev.getMessage() instanceof ShortMessage sm
                    && sm.getCommand() == ShortMessage.NOTE_ON) {
                noteOns++;
                if (sm.getData1() == 62) offBeatTick = ev.getTick();
            }
        }
        assertEquals(2, noteOns);
        assertEquals(Math.round(PPQ * (2.0 / 3.0)), offBeatTick);
    }
}
