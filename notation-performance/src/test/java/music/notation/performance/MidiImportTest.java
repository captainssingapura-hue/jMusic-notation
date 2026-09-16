package music.notation.performance;

import music.notation.duration.Duration;
import music.notation.expressivity.*;

import music.notation.structure.Mode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke test for {@link MidiCodec#fromMidiWithMeta}: builds a small
 * Performance, encodes it to MIDI bytes, re-reads via the convenience
 * entry, and verifies the imported wrapper carries plausible meta.
 */
class MidiImportTest {

    @Test
    void roundTripPerformanceThroughImport() {
        var noteOn = new PitchedNote(Duration.zero(), Duration.of(1, 4), 60, false);
        var track = new Track(new TrackId("piano"), TrackKind.PITCHED, List.<ConcreteNote>of(noteOn));
        var score = new Score(List.of(track));
        var perf = new Performance(score, TempoTrack.constant(120),
                Instrumentation.empty(), Volume.empty(), Articulations.empty());

        byte[] bytes = MidiCodec.toMidi(perf);
        MidiImport imp = MidiCodec.fromMidiWithMeta(bytes, "test");

        assertEquals("test", imp.displayName());
        assertEquals(1, imp.performance().score().tracks().size());
        assertEquals(120, imp.initialBpm());
        // Defaults applied — toMidi doesn't write 0x58/0x59 today.
        assertEquals(4, imp.timeSig().beats());
        assertEquals(4, imp.timeSig().beatValue());
        assertEquals(Mode.MAJOR, imp.key().mode());
    }

    @Test
    void totalMsReflectsLastNoteEnd() {
        var n1 = new PitchedNote(Duration.zero(), Duration.of(1, 4), 60, false);
        var n2 = new PitchedNote(Duration.of(1, 4), Duration.of(1, 2), 62, false);
        var track = new Track(new TrackId("piano"), TrackKind.PITCHED, List.<ConcreteNote>of(n1, n2));
        var perf = new Performance(new Score(List.of(track)),
                TempoTrack.constant(120), Instrumentation.empty(),
                Volume.empty(), Articulations.empty());
        byte[] bytes = MidiCodec.toMidi(perf);
        MidiImport imp = MidiCodec.fromMidiWithMeta(bytes, "demo");
        // quarter + half = 3/4 whole = 1500 ms at 120 bpm.
        assertEquals(1500, imp.totalMs());
    }
}
