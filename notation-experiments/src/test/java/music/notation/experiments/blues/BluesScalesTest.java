package music.notation.experiments.blues;

import music.notation.core.model.transformation.Transformer;
import music.notation.experiments.blues.major.BluesMajorConcretizer;
import music.notation.experiments.blues.major.BluesMajorDegree;
import music.notation.experiments.blues.major.BluesMajorNote;
import music.notation.experiments.blues.minor.BluesMinorConcretizer;
import music.notation.experiments.blues.minor.BluesMinorDegree;
import music.notation.experiments.blues.minor.BluesMinorNote;
import music.notation.experiments.hirajoshi.HirajoshiNote;
import music.notation.experiments.scale.TimedNote;
import music.notation.experiments.transform.ScaleTranspose;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Blues Minor and Blues Major concretization + cross-transpose.
 */
class BluesScalesTest {

    // ── Per-scale concretization ──────────────────────────────────────

    @Test
    void minorBlues_inC_octave4() {
        // C E♭ F G♭ G B♭ = 60 63 65 66 67 70
        var c = BluesMinorConcretizer.inC();
        var expected = List.of(60, 63, 65, 66, 67, 70);
        for (int i = 0; i < BluesMinorDegree.values().length; i++) {
            assertEquals(expected.get(i), c.midi(BluesMinorNote.ofIndex(i, 4)));
        }
    }

    @Test
    void majorBlues_inC_octave4() {
        // C D E♭ E G A = 60 62 63 64 67 69
        var c = BluesMajorConcretizer.inC();
        var expected = List.of(60, 62, 63, 64, 67, 69);
        for (int i = 0; i < BluesMajorDegree.values().length; i++) {
            assertEquals(expected.get(i), c.midi(BluesMajorNote.ofIndex(i, 4)));
        }
    }

    @Test
    void bothBluesScales_haveSixDegrees() {
        assertEquals(6, BluesMinorDegree.values().length);
        assertEquals(6, BluesMajorDegree.values().length);
        assertEquals(6, BluesMinorNote.ofIndex(0, 4).degreeCount());
        assertEquals(6, BluesMajorNote.ofIndex(0, 4).degreeCount());
    }

    // ── Cross-scale transpose within the blues family ─────────────────

    @Test
    void minorToMajorBlues_preservesDegreeIndex_andIsReversible() {
        Transformer<BluesMinorNote, BluesMajorNote> t =
                new ScaleTranspose<>(BluesMinorNote::ofIndex, BluesMajorNote::ofIndex);

        for (int i = 0; i < 6; i++) {
            var src = BluesMinorNote.ofIndex(i, 4);
            var dst = t.forward(src);
            assertEquals(i, dst.degreeIndex(), "degree idx " + i);
            assertEquals(4, dst.octave());
            assertEquals("Blues Major", dst.scaleName());
            assertEquals(src, t.reverse(dst));
        }
    }

    @Test
    void blueNoteBecomesIII_whenMinorTransposesToMajor() {
        // The signature reveal: ♭V (blue note, index 3) in minor blues
        // maps to III (major third, index 3) in major blues — same index,
        // different colour. Concretized in C: G♭4 (66) → E4 (64).
        Transformer<BluesMinorNote, BluesMajorNote> t =
                new ScaleTranspose<>(BluesMinorNote::ofIndex, BluesMajorNote::ofIndex);

        var minorBlue = BluesMinorNote.of(BluesMinorDegree.V_FLAT, 4);
        var majorIII = t.forward(minorBlue);

        int minorMidi = BluesMinorConcretizer.inC().midi(minorBlue);
        int majorMidi = BluesMajorConcretizer.inC().midi(majorIII);

        assertEquals(66, minorMidi, "G♭4 = 66 (the minor-blues blue note)");
        assertEquals(64, majorMidi, "E4 = 64 (the major-blues major third)");
    }

    // ── Pentatonic ↔ hexatonic: the safety check ─────────────────────

    @Test
    void pentatonicToBlues_rejectedByDegreeCountCheck() {
        // Hirajoshi is 5 degrees; Blues Minor is 6. ScaleTranspose should refuse.
        assertThrows(IllegalArgumentException.class, () ->
                new ScaleTranspose<>(HirajoshiNote::ofIndex, BluesMinorNote::ofIndex));
    }

    @Test
    void bluesToPentatonic_rejectedByDegreeCountCheck() {
        assertThrows(IllegalArgumentException.class, () ->
                new ScaleTranspose<>(BluesMinorNote::ofIndex, HirajoshiNote::ofIndex));
    }

    // ── Motif-through-transform end to end ───────────────────────────

    @Test
    void demoMotif_usesAllSixDegrees() {
        var usedIndices = BluesSong.demo().stream()
                .map(t -> t.note().degreeIndex())
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of(0, 1, 2, 3, 4, 5), usedIndices,
                "Demo motif should exercise every degree of the hexatonic scale");
    }

    @Test
    void demoMotif_hasExpectedShapeAndDuration() {
        var motif = BluesSong.demo();
        assertEquals(29, motif.size(),
                "29 timed events across 8 bars (some bars use halves)");
        int total = motif.stream().mapToInt(TimedNote::durationMillis).sum();
        assertEquals(16_000, total, "8 bars of 4/4 at 120 BPM = 16 000 ms");
    }

    @Test
    void demoMotif_transposeToMajorAndConcretize() {
        Transformer<BluesMinorNote, BluesMajorNote> t =
                new ScaleTranspose<>(BluesMinorNote::ofIndex, BluesMajorNote::ofIndex);

        var midi = BluesSong.demo().stream()
                .map(tn -> t.forward(tn.note()))
                .map(BluesMajorConcretizer.inC()::midi)
                .toList();

        // First four notes (bar 1: I ♭III IV ♭V in minor blues)
        //   → under scale transpose (indices 0,1,2,3)
        //   → I II ♭III III in major blues
        //   → C D E♭ E in C major blues = 60 62 63 64.
        assertEquals(List.of(60, 62, 63, 64), midi.subList(0, 4));
    }
}
