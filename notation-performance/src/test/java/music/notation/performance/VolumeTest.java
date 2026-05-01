package music.notation.performance;

import org.junit.jupiter.api.Test;

import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the {@link Volume} side-channel: type validation, codec
 * emission of MIDI CC #7, and JSON round-trip.
 */
class VolumeTest {

    private static final TrackId LEAD = new TrackId("lead");

    // ── VolumeChange validation ────────────────────────────────────

    @Test
    void volumeChangeRejectsNegativeTick() {
        assertThrows(IllegalArgumentException.class, () -> new VolumeChange(-1, 100));
    }

    @Test
    void volumeChangeRejectsLevelOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> new VolumeChange(0, -1));
        assertThrows(IllegalArgumentException.class, () -> new VolumeChange(0, 128));
    }

    @Test
    void volumeChangeAccepts0And127() {
        assertDoesNotThrow(() -> new VolumeChange(0, 0));
        assertDoesNotThrow(() -> new VolumeChange(0, 127));
    }

    // ── VolumeControl canonicalisation ─────────────────────────────

    @Test
    void volumeControlSortsAndDedupesConsecutiveSameLevels() {
        var vc = new VolumeControl(List.of(
                new VolumeChange(1000, 80),
                new VolumeChange(0, 100),
                new VolumeChange(500, 100), // dedupe — same as previous
                new VolumeChange(1500, 80)  // dedupe — same as 1000ms
        ));
        assertEquals(2, vc.changes().size(), "consecutive same-level entries dedupe");
        assertEquals(0, vc.changes().get(0).tickMs());
        assertEquals(100, vc.changes().get(0).level());
        assertEquals(1000, vc.changes().get(1).tickMs());
        assertEquals(80, vc.changes().get(1).level());
    }

    @Test
    void volumeControlConstantHelper() {
        var vc = VolumeControl.constant(64);
        assertEquals(1, vc.changes().size());
        assertEquals(0, vc.changes().get(0).tickMs());
        assertEquals(64, vc.changes().get(0).level());
    }

    // ── Volume map drops empty controls ────────────────────────────

    @Test
    void volumeDropsTrackWithEmptyControl() {
        var v = new Volume(Map.of(LEAD, VolumeControl.empty()));
        assertTrue(v.byTrack().isEmpty(), "empty controls are filtered out");
    }

    // ── Performance validation ─────────────────────────────────────

    @Test
    void performanceRejectsVolumeForUnknownTrack() {
        var unknown = new TrackId("unknown");
        var score = Score.of(new Track(LEAD, TrackKind.PITCHED,
                List.of(new PitchedNote(0, 500, 60))));
        var v = Volume.single(unknown, 80);
        assertThrows(IllegalArgumentException.class,
                () -> new Performance(score, TempoTrack.empty(),
                        Instrumentation.empty(), v, Articulations.empty()));
    }

    // ── Codec emits CC #7 ──────────────────────────────────────────

    @Test
    void codecEmitsCC7ForVolumeEntries() {
        var perf = new Performance(
                Score.of(new Track(LEAD, TrackKind.PITCHED,
                        List.of(new PitchedNote(0, 500, 60)))),
                TempoTrack.empty(),
                Instrumentation.empty(),
                Volume.single(LEAD, 80),
                Articulations.empty());

        byte[] bytes = MidiCodec.toMidi(perf);
        try {
            Sequence seq = MidiSystem.getSequence(new ByteArrayInputStream(bytes));
            int cc7Count = 0;
            int cc7Level = -1;
            for (var track : seq.getTracks()) {
                for (int i = 0; i < track.size(); i++) {
                    var msg = track.get(i).getMessage();
                    if (msg instanceof ShortMessage sm
                            && sm.getCommand() == ShortMessage.CONTROL_CHANGE
                            && sm.getData1() == 7) {
                        cc7Count++;
                        cc7Level = sm.getData2();
                    }
                }
            }
            assertEquals(1, cc7Count, "one CC #7 emitted");
            assertEquals(80, cc7Level);
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    void codecEmitsZeroCC7ForEmptyVolume() {
        var perf = Performance.of(Score.of(
                new Track(LEAD, TrackKind.PITCHED,
                        List.of(new PitchedNote(0, 500, 60)))));

        byte[] bytes = MidiCodec.toMidi(perf);
        try {
            Sequence seq = MidiSystem.getSequence(new ByteArrayInputStream(bytes));
            int cc7Count = 0;
            for (var track : seq.getTracks()) {
                for (int i = 0; i < track.size(); i++) {
                    var msg = track.get(i).getMessage();
                    if (msg instanceof ShortMessage sm
                            && sm.getCommand() == ShortMessage.CONTROL_CHANGE
                            && sm.getData1() == 7) {
                        cc7Count++;
                    }
                }
            }
            assertEquals(0, cc7Count, "empty Volume side-channel emits no CC #7");
        } catch (Exception e) {
            fail(e);
        }
    }

    // ── Volume is dropped on read (per import doctrine) ───────────

    @Test
    void volumeIsDroppedOnFromMidi() {
        var perf = new Performance(
                Score.of(new Track(LEAD, TrackKind.PITCHED,
                        List.of(new PitchedNote(0, 500, 60)))),
                TempoTrack.empty(),
                Instrumentation.empty(),
                Volume.single(LEAD, 80),
                Articulations.empty());

        byte[] bytes = MidiCodec.toMidi(perf);
        Performance reread = MidiCodec.fromMidi(bytes);

        assertTrue(reread.volume().byTrack().isEmpty(),
                "Volume is write-only: CC events are dropped on read per the import doctrine");
    }

    // ── JSON round-trip preserves Volume ───────────────────────────

    @Test
    void volumeJsonRoundTrip() {
        var perf = new Performance(
                Score.of(new Track(LEAD, TrackKind.PITCHED,
                        List.of(new PitchedNote(0, 500, 60)))),
                TempoTrack.empty(),
                Instrumentation.empty(),
                Volume.single(LEAD, 64),
                Articulations.empty());

        Performance reread = PerformanceJson.fromJson(PerformanceJson.toJson(perf));
        assertEquals(perf, reread);
    }
}
