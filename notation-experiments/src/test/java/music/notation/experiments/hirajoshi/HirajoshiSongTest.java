package music.notation.experiments.hirajoshi;

import music.notation.core.model.transformation.Transformer;
import music.notation.experiments.hirajoshi.transformer.ShiftOctave;
import music.notation.experiments.hirajoshi.transformer.TransposeDegree;
import music.notation.performance.ConcreteNote;
import music.notation.performance.Performance;
import music.notation.performance.PitchedNote;
import music.notation.experiments.scale.TimedNote;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the demo song concretizes to exactly the MIDI sequence we
 * expect. No actual audio playback - this is CI-safe.
 */
class HirajoshiSongTest {

    @Test
    void demoMelodyHasFourteenNotesAndCorrectTotalDuration() {
        var melody = HirajoshiSong.demo();

        assertEquals(14, melody.size());

        int total = melody.stream().mapToInt(TimedNote::durationMillis).sum();
        assertEquals(8000, total);
    }

    @Test
    void concretizeInC_producesExpectedMidiSequence() {
        var perf = HirajoshiSong.concretize(HirajoshiSong.demo(), HirajoshiConcretizer.inC());

        var expected = List.of(60, 63, 67, 63, 68, 67, 63, 62, 60, 62, 63, 67, 63, 60);

        assertEquals(expected, midiSequence(perf));
    }

    @Test
    void concretizeInD_producesExpectedMidiSequence() {
        var perf = HirajoshiSong.concretize(HirajoshiSong.demo(), HirajoshiConcretizer.inD());

        var expected = List.of(62, 65, 69, 65, 70, 69, 65, 64, 62, 64, 65, 69, 65, 62);
        assertEquals(expected, midiSequence(perf));
    }

    @Test
    void timingIsPreservedThroughConcretization() {
        var melody = HirajoshiSong.demo();
        var perf = HirajoshiSong.concretize(melody, HirajoshiConcretizer.inC());

        var notes = onlyNotes(perf);
        // Notes are sorted canonically by tickMs, which equals input order
        // since the melody has strictly-increasing onsets.
        for (int i = 0; i < melody.size(); i++) {
            assertEquals(
                    melody.get(i).durationMillis(),
                    (int) notes.get(i).durationMs(),
                    "note " + i + " duration must survive concretization");
        }
    }

    @Test
    void abstractTransformsComposeWithConcretization() {
        var melody = HirajoshiSong.demo();

        Transformer<HirajoshiNote, HirajoshiNote> chain = new ShiftOctave(1);
        var shifted = melody.stream()
                .map(t -> new TimedNote<>(chain.forward(t.note()), t.durationMillis()))
                .toList();

        var shiftedMidi = midiSequence(HirajoshiSong.concretize(shifted,  HirajoshiConcretizer.inC()));
        var baselineMidi = midiSequence(HirajoshiSong.concretize(melody, HirajoshiConcretizer.inC()));

        for (int i = 0; i < shiftedMidi.size(); i++) {
            assertEquals(baselineMidi.get(i) + 12, shiftedMidi.get(i));
        }
    }

    @Test
    void chainedAbstractTransforms_stillConcretizeCleanly() {
        var melody = HirajoshiSong.demo();

        Transformer<HirajoshiNote, HirajoshiNote> chain =
                new TransposeDegree(2).andThen(new ShiftOctave(1));

        var transformed = melody.stream()
                .map(t -> new TimedNote<>(chain.forward(t.note()), t.durationMillis()))
                .toList();
        var concrete = HirajoshiSong.concretize(transformed, HirajoshiConcretizer.inC());

        var reversed = transformed.stream()
                .map(t -> new TimedNote<>(chain.reverse(t.note()), t.durationMillis()))
                .toList();
        assertEquals(
                HirajoshiSong.concretize(melody, HirajoshiConcretizer.inC()),
                HirajoshiSong.concretize(reversed, HirajoshiConcretizer.inC()));

        assertEquals(14, onlyNotes(concrete).size());
    }

    // -- helpers --

    private static List<PitchedNote> onlyNotes(Performance perf) {
        var out = new ArrayList<PitchedNote>();
        perf.score().tracks().forEach(t -> {
            for (ConcreteNote n : t.notes()) {
                if (n instanceof PitchedNote pn) out.add(pn);
            }
        });
        return out;
    }

    private static List<Integer> midiSequence(Performance perf) {
        return onlyNotes(perf).stream().map(PitchedNote::midi).toList();
    }
}
