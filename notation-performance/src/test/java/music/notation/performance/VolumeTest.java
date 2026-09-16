package music.notation.performance;

import music.notation.duration.Duration;
import music.notation.expressivity.*;

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

    /** {@code n} quarter notes. */
    private static Duration q(long n) { return Duration.of(n, 4); }

    /** A MIDI CC byte expressed as a synth-agnostic level in [0, 1]. */
    private static double lvl(int midiByte) { return midiByte / 127.0; }

    // ── VolumeChange validation ────────────────────────────────────

    @Test
    void volumeChangeRejectsNegativePosition() {
        assertThrows(IllegalArgumentException.class, () -> new VolumeChange(Duration.of(-1, 4), lvl(100)));
    }

    @Test
    void volumeChangeRejectsLevelOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> new VolumeChange(q(0), -0.01));
        assertThrows(IllegalArgumentException.class, () -> new VolumeChange(q(0), 1.01));
    }

    @Test
    void volumeChangeAcceptsSilentAndFullLevels() {
        assertDoesNotThrow(() -> new VolumeChange(q(0), 0.0));
        assertDoesNotThrow(() -> new VolumeChange(q(0), 1.0));
    }

    // ── VolumeControl canonicalisation ─────────────────────────────

    @Test
    void volumeControlSortsAndDedupesConsecutiveSameLevels() {
        var vc = new VolumeControl(List.of(
                new VolumeChange(q(2), lvl(80)),
                new VolumeChange(q(0), lvl(100)),
                new VolumeChange(q(1), lvl(100)), // dedupe — same as previous
                new VolumeChange(q(3), lvl(80))   // dedupe — same as 2q
        ));
        assertEquals(2, vc.changes().size(), "consecutive same-level entries dedupe");
        assertTrue(vc.changes().get(0).at().isZero());
        assertEquals(lvl(100), vc.changes().get(0).level(), 1e-9);
        assertTrue(q(2).equalsDuration(vc.changes().get(1).at()));
        assertEquals(lvl(80), vc.changes().get(1).level(), 1e-9);
    }

    @Test
    void volumeControlConstantHelper() {
        var vc = VolumeControl.constant(lvl(64));
        assertEquals(1, vc.changes().size());
        assertTrue(vc.changes().get(0).at().isZero());
        assertEquals(lvl(64), vc.changes().get(0).level(), 1e-9);
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
                List.of(new PitchedNote(q(0), q(1), 60))));
        var v = Volume.single(unknown, lvl(80));
        assertThrows(IllegalArgumentException.class,
                () -> new Performance(score, TempoTrack.empty(),
                        Instrumentation.empty(), v, Articulations.empty()));
    }

    // ── Codec emits CC #7 ──────────────────────────────────────────

    @Test
    void codecEmitsCC7ForVolumeEntries() {
        var perf = new Performance(
                Score.of(new Track(LEAD, TrackKind.PITCHED,
                        List.of(new PitchedNote(q(0), q(1), 60)))),
                TempoTrack.empty(),
                Instrumentation.empty(),
                Volume.single(LEAD, lvl(80)),
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
                        List.of(new PitchedNote(q(0), q(1), 60)))));

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
                        List.of(new PitchedNote(q(0), q(1), 60)))),
                TempoTrack.empty(),
                Instrumentation.empty(),
                Volume.single(LEAD, lvl(80)),
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
                        List.of(new PitchedNote(q(0), q(1), 60)))),
                TempoTrack.empty(),
                Instrumentation.empty(),
                Volume.single(LEAD, lvl(64)),
                Articulations.empty());

        Performance reread = PerformanceJson.fromJson(PerformanceJson.toJson(perf));
        assertEquals(perf, reread);
    }
}
