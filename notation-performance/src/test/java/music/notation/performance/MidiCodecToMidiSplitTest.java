package music.notation.performance;

import music.notation.expressivity.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Coverage for {@link MidiCodec#toMidiSplit(Performance)} — Phase 1 of
 * the multi-synth fan-out work.
 *
 * <p>Phase-1 contract: always returns a singleton list whose element
 * is byte-identical to legacy {@link MidiCodec#toMidi(Performance)}.
 * Phase 2 will start partitioning into multiple byte[]s; this test
 * pins the singleton contract so the migration is observable.</p>
 */
class MidiCodecToMidiSplitTest {

    private static Performance simplePianoPiece() {
        var notes = List.<ConcreteNote>of(
                new PitchedNote(0,    500, 60),
                new PitchedNote(500,  500, 62),
                new PitchedNote(1000, 500, 64));
        var track = new Track(new TrackId("Piano"), TrackKind.PITCHED, notes);
        return new Performance(
                new Score(List.of(track)),
                TempoTrack.constant(120),
                Instrumentation.empty(), Volume.empty(),
                Articulations.empty(), Pedaling.empty(),
                Velocities.empty());
    }

    private static Performance pianoPlusDrumPiece() {
        var pitched = new Track(
                new TrackId("Piano"), TrackKind.PITCHED,
                List.of(new PitchedNote(0,    500, 60),
                        new PitchedNote(500,  500, 62)));
        var drums = new Track(
                new TrackId("Drums"), TrackKind.DRUM,
                List.of(new DrumNote(0,    100, 36),
                        new DrumNote(500,  100, 38)));
        return new Performance(
                new Score(List.of(pitched, drums)),
                TempoTrack.constant(120),
                Instrumentation.empty(), Volume.empty(),
                Articulations.empty(), Pedaling.empty(),
                Velocities.empty());
    }

    @Test
    void splitReturnsSingletonForSimplePiece() {
        Performance perf = simplePianoPiece();
        List<byte[]> split = MidiCodec.toMidiSplit(perf);
        assertEquals(1, split.size(),
                "Phase 1 always returns a singleton list");
    }

    @Test
    void splitElementMatchesLegacyToMidiByteForByte() {
        Performance perf = simplePianoPiece();
        byte[] legacy = MidiCodec.toMidi(perf);
        byte[] split0 = MidiCodec.toMidiSplit(perf).get(0);
        assertArrayEquals(legacy, split0,
                "toMidiSplit's only element must equal legacy toMidi byte-for-byte");
    }

    @Test
    void splitMatchesLegacyForPianoPlusDrumPiece() {
        Performance perf = pianoPlusDrumPiece();
        byte[] legacy = MidiCodec.toMidi(perf);
        byte[] split0 = MidiCodec.toMidiSplit(perf).get(0);
        assertArrayEquals(legacy, split0);
    }

    @Test
    void legacyToMidiStillWorks() {
        // The bridge from toMidi → toMidiSplit must not break legacy
        // callers — produces a non-empty byte array deserialisable by
        // the standard MidiSystem.
        byte[] bytes = MidiCodec.toMidi(simplePianoPiece());
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);
    }

    @Test
    void splitAndLegacyAgreeOnEmptyScore() {
        Performance empty = Performance.of(Score.empty());
        byte[] legacy = MidiCodec.toMidi(empty);
        List<byte[]> split = MidiCodec.toMidiSplit(empty);
        assertEquals(1, split.size());
        assertArrayEquals(legacy, split.get(0));
    }

    @Test
    void roundTripParityHolds() {
        // The critical existing invariant: fromMidi(toMidi(p)).equals(p).
        // toMidiSplit must preserve it through its singleton element.
        Performance perf = simplePianoPiece();
        Performance roundTripped = MidiCodec.fromMidi(
                MidiCodec.toMidiSplit(perf).get(0));
        // Score round-trips; side-channels are dropped per the codec's
        // documented round-trip contract (pedaling/volume/velocities
        // can't be perfectly reconstructed).
        assertEquals(perf.score(), roundTripped.score());
    }
}
