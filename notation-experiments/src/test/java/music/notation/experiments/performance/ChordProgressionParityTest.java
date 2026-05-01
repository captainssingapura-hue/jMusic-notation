package music.notation.experiments.performance;

import music.notation.performance.Articulations;
import music.notation.performance.ConcreteNote;
import music.notation.performance.Instrumentation;
import music.notation.performance.MidiCodec;
import music.notation.performance.Performance;
import music.notation.performance.PitchedNote;
import music.notation.performance.Score;
import music.notation.performance.TempoTrack;
import music.notation.performance.Track;
import music.notation.performance.TrackId;
import music.notation.performance.TrackKind;
import music.notation.experiments.chinese.gong.GongConcretizer;
import music.notation.experiments.chord.ChordConcretizer;
import music.notation.experiments.chord.ChordProgression;
import music.notation.experiments.chord.ChordShape;
import music.notation.experiments.chord.ScaleChord;
import music.notation.experiments.chinese.gong.GongNote;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Demonstrates the parity pattern: compare {@link Performance}s by
 * structural equality, not MIDI byte hashes.
 *
 * <p>When a test fails, record {@code equals} produces a field-level
 * diff. The "golden" value is defined in code here as a hand-built
 * Performance, which keeps this test self-contained.</p>
 */
class ChordProgressionParityTest {

    private static final TrackId CHORD = new TrackId("chord");

    @Test
    void blockProgressionInCGong_matchesExpectedPerformance() {
        var actual = concretizeProgression(ChordShape.BLOCK);
        var expected = expectedBlock();
        assertEquals(expected, actual);
    }

    @Test
    void blockProgression_roundTripsThroughMidi() {
        var direct = concretizeProgression(ChordShape.BLOCK);
        var viaMidi = MidiCodec.fromMidi(MidiCodec.toMidi(direct));
        assertEquals(direct, viaMidi, "MIDI round-trip must preserve every note field");
    }

    @Test
    void arpeggioUpProgression_matchesExpectedPerformanceShape() {
        var actual = concretizeProgression(ChordShape.ARPEGGIO_UP);

        // Single track with all notes flattened across the 4-chord progression.
        assertEquals(1, actual.score().tracks().size());
        var notes = actual.score().tracks().get(0).notes().stream()
                .map(n -> (PitchedNote) n)
                .toList();

        assertEquals(12, notes.size(), "4 chords × 3 voices");

        long lastOff = notes.stream().mapToLong(PitchedNote::offTickMs).max().orElse(0);
        assertEquals(4000, lastOff, "4 chords × 1000 ms each");

        // First chord: three ascending onsets at 0 / 333 / 666.
        assertEquals(0L,   notes.get(0).tickMs());
        assertEquals(333L, notes.get(1).tickMs());
        assertEquals(666L, notes.get(2).tickMs());
    }

    // ── Builders ────────────────────────────────────────────────────

    private static Performance concretizeProgression(ChordShape shape) {
        var concretizer = new ChordConcretizer<>(GongConcretizer.inC(), CHORD);
        var notes = new ArrayList<ConcreteNote>();
        long cursor = 0;
        for (ScaleChord<GongNote> chord : ChordProgression.demoIn(shape)) {
            for (ConcreteNote n : concretizer.concretize(chord).notes()) {
                var pn = (PitchedNote) n;
                notes.add(new PitchedNote(pn.tickMs() + cursor, pn.durationMs(), pn.midi()));
            }
            cursor += chord.durationMs();
        }
        var track = new Track(CHORD, TrackKind.PITCHED, notes);
        return new Performance(
                new Score(List.of(track)),
                TempoTrack.empty(),
                Instrumentation.empty(),
                Articulations.empty());
    }

    /**
     * Hand-computed expected Performance for the block-chord progression
     * in C Gong. 4 chords of 1000 ms each; each chord has 3 voices
     * attacking simultaneously.
     *
     * <pre>
     *   chord 1  tonic triad   I   III V    = C4 E4 G4  = 60 64 67
     *   chord 2  "sus"         II  V   VI   = D4 G4 A4  = 62 67 69
     *   chord 3  vi            III VI  I^5  = E4 A4 C5  = 64 69 72
     *   chord 4  tonic return  I   III V    = C4 E4 G4  = 60 64 67
     * </pre>
     */
    private static Performance expectedBlock() {
        var track = new Track(CHORD, TrackKind.PITCHED, List.of(
                // chord 1 — tick 0 (ends at 1000)
                new PitchedNote(0, 1000, 60),
                new PitchedNote(0, 1000, 64),
                new PitchedNote(0, 1000, 67),
                // chord 2 — tick 1000 (ends at 2000)
                new PitchedNote(1000, 1000, 62),
                new PitchedNote(1000, 1000, 67),
                new PitchedNote(1000, 1000, 69),
                // chord 3 — tick 2000 (ends at 3000)
                new PitchedNote(2000, 1000, 64),
                new PitchedNote(2000, 1000, 69),
                new PitchedNote(2000, 1000, 72),
                // chord 4 — tick 3000 (ends at 4000)
                new PitchedNote(3000, 1000, 60),
                new PitchedNote(3000, 1000, 64),
                new PitchedNote(3000, 1000, 67)
        ));
        return new Performance(
                new Score(List.of(track)),
                TempoTrack.empty(),
                Instrumentation.empty(),
                Articulations.empty());
    }
}
