package music.notation.performance;

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
        var p = perfOfOneTrack(LEAD, new PitchedNote(0, 500, 60));
        assertEquals(p, MidiCodec.fromMidi(MidiCodec.toMidi(p)));
    }

    @Test
    void blockChord_roundTrips() {
        // Three simultaneous notes — tick 0, all 1 second long.
        var p = perfOfOneTrack(LEAD,
                new PitchedNote(0, 1000, 60),
                new PitchedNote(0, 1000, 64),
                new PitchedNote(0, 1000, 67));
        assertEquals(p, MidiCodec.fromMidi(MidiCodec.toMidi(p)));
    }

    @Test
    void arpeggio_roundTrips() {
        var p = perfOfOneTrack(LEAD,
                new PitchedNote(0,    333, 60),
                new PitchedNote(333,  666, 64),
                new PitchedNote(666, 1000, 67));
        assertEquals(p, MidiCodec.fromMidi(MidiCodec.toMidi(p)));
    }

    @Test
    void programChangeAndTempo_roundTrip() {
        var track = new Track(LEAD, TrackKind.PITCHED, List.of(
                new PitchedNote(0,   500, 60),
                new PitchedNote(500, 1000, 62)));
        var p = new Performance(
                new Score(List.of(track)),
                TempoTrack.constant(120),
                Instrumentation.single(LEAD, 107),    // Koto
                Articulations.empty());
        assertEquals(p, MidiCodec.fromMidi(MidiCodec.toMidi(p)));
    }

    @Test
    void multiVoice_roundTrips() {
        // Two tracks playing different pitches simultaneously.
        var p = new Performance(
                new Score(List.of(
                        new Track(LEAD,    TrackKind.PITCHED, List.of(new PitchedNote(0, 500, 60))),
                        new Track(HARMONY, TrackKind.PITCHED, List.of(new PitchedNote(0, 500, 72)))
                )),
                TempoTrack.empty(), Instrumentation.empty(), Articulations.empty());
        assertEquals(p, MidiCodec.fromMidi(MidiCodec.toMidi(p)));
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private static Performance perfOfOneTrack(TrackId id, PitchedNote... notes) {
        return new Performance(
                new Score(List.of(new Track(id, TrackKind.PITCHED, List.of(notes)))),
                TempoTrack.empty(), Instrumentation.empty(), Articulations.empty());
    }
}
