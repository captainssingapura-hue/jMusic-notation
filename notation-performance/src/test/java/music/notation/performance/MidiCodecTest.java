package music.notation.performance;

import music.notation.duration.Duration;
import music.notation.expressivity.*;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round-trip checks for the concrete-layer ↔ MIDI bridge.
 *
 * <p>If {@code fromMidi(toMidi(p)).equals(p)} for a variety of Performances,
 * the codec is lossless for the parts the model represents — that's the
 * sole contract this layer claims. Velocity, articulation, and other
 * expressive parameters that the model intentionally doesn't carry are
 * normalised to defaults on read; round-trip stability follows from that
 * normalisation.</p>
 */
class MidiCodecTest {

    private static final TrackId LEAD = new TrackId("lead");
    private static final TrackId HARMONY = new TrackId("harmony");

    @Test
    void singleNote_roundTrips() {
        var p = perfOfOneTrack(LEAD, new PitchedNote(q(0), q(1), 60));
        assertEquals(p, MidiCodec.fromMidi(MidiCodec.toMidi(p)));
    }

    @Test
    void blockChord_roundTrips() {
        // Three simultaneous notes — position 0, all half notes.
        var p = perfOfOneTrack(LEAD,
                new PitchedNote(q(0), q(2), 60),
                new PitchedNote(q(0), q(2), 64),
                new PitchedNote(q(0), q(2), 67));
        assertEquals(p, MidiCodec.fromMidi(MidiCodec.toMidi(p)));
    }

    @Test
    void arpeggio_roundTrips() {
        var p = perfOfOneTrack(LEAD,
                new PitchedNote(Duration.zero(), Duration.of(1, 6), 60),
                new PitchedNote(Duration.of(1, 6), Duration.of(1, 3), 64),
                new PitchedNote(Duration.of(1, 3), Duration.of(1, 2), 67));
        assertEquals(p, MidiCodec.fromMidi(MidiCodec.toMidi(p)));
    }

    @Test
    void programChangeAndTempo_roundTrip() {
        var track = new Track(LEAD, TrackKind.PITCHED, List.of(
                new PitchedNote(q(0), q(1), 60),
                new PitchedNote(q(1), q(2), 62)));
        var p = new Performance(
                new Score(List.of(track)),
                TempoTrack.constant(120),
                Instrumentation.single(LEAD, 107),    // Koto
                Articulations.empty());
        assertEquals(p, MidiCodec.fromMidi(MidiCodec.toMidi(p)));
    }

    @Test
    void explicitVelocityRoundTripsAcrossNotes() {
        // Two notes at different velocities — non-default values, so the
        // round-trip should preserve the per-onset shape.
        var track = new Track(LEAD, TrackKind.PITCHED, List.of(
                new PitchedNote(q(0), q(1), 60),
                new PitchedNote(q(1), q(1), 64)));
        var velocities = Velocities.single(LEAD, new VelocityControl(List.of(
                new VelocityChange(q(0), 110 / 127.0),   // accent
                new VelocityChange(q(1), 50 / 127.0))));  // ghost
        var p = new Performance(
                new Score(List.of(track)),
                TempoTrack.empty(), Instrumentation.empty(),
                Volume.empty(), Articulations.empty(),
                Pedaling.empty(), velocities);

        Performance roundTripped = MidiCodec.fromMidi(MidiCodec.toMidi(p));
        var rtControl = roundTripped.velocities().byTrack().get(LEAD);
        assertNotNull(rtControl, "velocity control should survive round-trip");
        assertEquals(2, rtControl.changes().size());
        assertEquals(110 / 127.0, rtControl.levelAt(q(0)), 1e-9);
        assertEquals(50 / 127.0,  rtControl.levelAt(q(1)), 1e-9);
    }

    @Test
    void uniformDefaultVelocityRoundTripsToEmpty() {
        // A track whose every note plays at the codec-default velocity
        // (no explicit Velocities) round-trips to empty Velocities — the
        // shape isn't preserved (we don't reconstruct explicit defaults)
        // but the audible behaviour is identical.
        var p = perfOfOneTrack(LEAD,
                new PitchedNote(q(0), q(1), 60),
                new PitchedNote(q(1), q(1), 64));
        Performance roundTripped = MidiCodec.fromMidi(MidiCodec.toMidi(p));
        assertTrue(roundTripped.velocities().byTrack().isEmpty());
    }

    @Test
    void multiVoice_roundTrips() {
        // Two tracks playing different pitches simultaneously.
        var p = new Performance(
                new Score(List.of(
                        new Track(LEAD,    TrackKind.PITCHED, List.of(new PitchedNote(q(0), q(1), 60))),
                        new Track(HARMONY, TrackKind.PITCHED, List.of(new PitchedNote(q(0), q(1), 72)))
                )),
                TempoTrack.empty(), Instrumentation.empty(), Articulations.empty());
        assertEquals(p, MidiCodec.fromMidi(MidiCodec.toMidi(p)));
    }

    // ── Helpers ──────────────────────────────────────────────────────

    /** {@code n} quarter notes. */
    private static Duration q(long n) { return Duration.of(n, 4); }

    private static Performance perfOfOneTrack(TrackId id, PitchedNote... notes) {
        return new Performance(
                new Score(List.of(new Track(id, TrackKind.PITCHED, List.of(notes)))),
                TempoTrack.empty(), Instrumentation.empty(), Articulations.empty());
    }
}
