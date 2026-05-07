package music.notation.play;

import music.notation.expressivity.PedalChange;
import music.notation.expressivity.PedalControl;
import music.notation.expressivity.PedalState;
import music.notation.expressivity.Pedaling;
import music.notation.performance.TempoChange;
import music.notation.performance.TempoTrack;
import music.notation.expressivity.TrackId;
import org.junit.jupiter.api.Test;

import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PedalInjectorTest {

    private static final int PPQ = 480;

    private static Sequence pianoSequence() throws Exception {
        Sequence seq = new Sequence(Sequence.PPQ, PPQ);
        Track t = seq.createTrack();
        // Channel 0 — piano. A few notes spread over a couple of beats.
        ShortMessage on = new ShortMessage();
        on.setMessage(ShortMessage.NOTE_ON, 0, 60, 100);
        t.add(new MidiEvent(on, 0));
        ShortMessage off = new ShortMessage();
        off.setMessage(ShortMessage.NOTE_OFF, 0, 60, 0);
        t.add(new MidiEvent(off, PPQ));
        return seq;
    }

    private static Pedaling pedalingWith(PedalChange... changes) {
        var ctrl = new PedalControl(List.of(changes));
        return new Pedaling(Map.of(new TrackId("Piano"), ctrl));
    }

    @Test
    void emptyPedalingReturnsInputUnchanged() throws Exception {
        Sequence src = pianoSequence();
        Sequence out = PedalInjector.inject(src, Pedaling.empty(), TempoTrack.constant(120));
        assertSame(src, out);
    }

    @Test
    void downAndUpProduceTwoCC64Events() throws Exception {
        var pedaling = pedalingWith(
                new PedalChange(0,    PedalState.DOWN),
                new PedalChange(500,  PedalState.UP));
        Sequence out = PedalInjector.inject(pianoSequence(), pedaling, TempoTrack.constant(120));

        long count = countCc64Events(out);
        assertEquals(2, count, "expected 1 DOWN + 1 UP CC#64 event");

        // First CC#64 at tick 0 with value 127 (DOWN); second at PPQ ticks (500ms at 120bpm = 1 quarter)
        // with value 0 (UP).
        var events = collectCc64(out);
        assertEquals(0L, events.get(0).getTick());
        assertEquals(127, ((ShortMessage) events.get(0).getMessage()).getData2());
        assertTrue(events.get(1).getTick() > 0);
        assertEquals(0,   ((ShortMessage) events.get(1).getMessage()).getData2());
    }

    @Test
    void changeStateProducesTwoEvents1TickApart() throws Exception {
        var pedaling = pedalingWith(new PedalChange(100, PedalState.CHANGE));
        Sequence out = PedalInjector.inject(pianoSequence(), pedaling, TempoTrack.constant(120));
        var events = collectCc64(out);
        // CHANGE → CC#64 = 0 then = 127 separated by 1 tick.
        assertEquals(2, events.size());
        assertEquals(0,   ((ShortMessage) events.get(0).getMessage()).getData2());
        assertEquals(127, ((ShortMessage) events.get(1).getMessage()).getData2());
        assertEquals(events.get(0).getTick() + 1, events.get(1).getTick());
    }

    @Test
    void drumChannelSkipsPedalInjection() throws Exception {
        // Build a sequence with NOTE_ON only on channel 9 (drums).
        Sequence seq = new Sequence(Sequence.PPQ, PPQ);
        Track t = seq.createTrack();
        ShortMessage kick = new ShortMessage();
        kick.setMessage(ShortMessage.NOTE_ON, 9, 36, 100);
        t.add(new MidiEvent(kick, 0));

        var pedaling = pedalingWith(new PedalChange(0, PedalState.DOWN));
        Sequence out = PedalInjector.inject(seq, pedaling, TempoTrack.constant(120));

        // No non-drum channel ever played → no injection target → output
        // is the rebuilt copy with no CC#64 events.
        assertEquals(0, countCc64Events(out));
    }

    @Test
    void pedalEventsRespectTempoChanges() throws Exception {
        // DOWN at 0ms, UP at 6000ms.
        // Tempo: 60 bpm for first 4000ms (= 4 quarters), then 120 bpm.
        // 6000ms → 4 quarters + (2000ms at 120bpm = 4 quarters) = 8 quarters
        //        → 8 * 480 = 3840 ticks at PPQ=480
        var pedaling = pedalingWith(
                new PedalChange(0,    PedalState.DOWN),
                new PedalChange(6000, PedalState.UP));
        var tempos = new TempoTrack(List.of(
                new TempoChange(0,    60),
                new TempoChange(4000, 120)));
        Sequence out = PedalInjector.inject(pianoSequence(), pedaling, tempos);
        var events = collectCc64(out);
        assertEquals(2, events.size());
        assertEquals(0L,    events.get(0).getTick());
        assertEquals(127,   ((ShortMessage) events.get(0).getMessage()).getData2());
        assertEquals(3840L, events.get(1).getTick());
        assertEquals(0,     ((ShortMessage) events.get(1).getMessage()).getData2());
    }

    @Test
    void originalEventsArePreserved() throws Exception {
        Sequence src = pianoSequence();
        var pedaling = pedalingWith(new PedalChange(0, PedalState.DOWN));
        Sequence out = PedalInjector.inject(src, pedaling, TempoTrack.constant(120));

        // Two original NOTE events still present.
        long noteEvents = 0;
        for (Track t : out.getTracks()) {
            for (int i = 0; i < t.size(); i++) {
                if (t.get(i).getMessage() instanceof ShortMessage sm
                        && (sm.getCommand() == ShortMessage.NOTE_ON
                                || sm.getCommand() == ShortMessage.NOTE_OFF)) {
                    noteEvents++;
                }
            }
        }
        assertEquals(2, noteEvents);
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private static long countCc64Events(Sequence seq) {
        return collectCc64(seq).size();
    }

    private static List<MidiEvent> collectCc64(Sequence seq) {
        var out = new java.util.ArrayList<MidiEvent>();
        for (Track t : seq.getTracks()) {
            for (int i = 0; i < t.size(); i++) {
                MidiEvent ev = t.get(i);
                if (ev.getMessage() instanceof ShortMessage sm
                        && sm.getCommand() == ShortMessage.CONTROL_CHANGE
                        && sm.getData1() == 64) {
                    out.add(ev);
                }
            }
        }
        out.sort((a, b) -> Long.compare(a.getTick(), b.getTick()));
        assertNotNull(out);
        return out;
    }
}
