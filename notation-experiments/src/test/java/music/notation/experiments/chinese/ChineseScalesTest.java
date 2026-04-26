package music.notation.experiments.chinese;

import music.notation.core.model.transformation.Transformer;
import music.notation.experiments.chinese.gong.GongConcretizer;
import music.notation.experiments.chinese.gong.GongDegree;
import music.notation.experiments.chinese.gong.GongNote;
import music.notation.experiments.chinese.jue.JueConcretizer;
import music.notation.experiments.chinese.jue.JueDegree;
import music.notation.experiments.chinese.jue.JueNote;
import music.notation.experiments.chinese.shang.ShangConcretizer;
import music.notation.experiments.chinese.shang.ShangDegree;
import music.notation.experiments.chinese.shang.ShangNote;
import music.notation.experiments.chinese.yu.YuConcretizer;
import music.notation.experiments.chinese.yu.YuDegree;
import music.notation.experiments.chinese.yu.YuNote;
import music.notation.experiments.chinese.zhi.ZhiConcretizer;
import music.notation.experiments.chinese.zhi.ZhiDegree;
import music.notation.experiments.chinese.zhi.ZhiNote;
import music.notation.experiments.hirajoshi.HirajoshiDegree;
import music.notation.experiments.hirajoshi.HirajoshiNote;
import music.notation.experiments.transform.ScaleTranspose;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Each of the five traditional Chinese pentatonic modes, in C at octave 4,
 * should concretize to its canonical MIDI sequence. Also verifies
 * cross-family transposition (Japanese ↔ Chinese) works via the generic
 * {@link ScaleTranspose}.
 */
class ChineseScalesTest {

    @Test
    void gong_inC_octave4() {
        // C D E G A  = 60 62 64 67 69  (major pentatonic)
        var concretizer = GongConcretizer.inC();
        var expected = List.of(60, 62, 64, 67, 69);
        for (int i = 0; i < GongDegree.values().length; i++) {
            assertEquals(expected.get(i), concretizer.midi(GongNote.ofIndex(i, 4)));
        }
    }

    @Test
    void shang_inC_octave4() {
        // C D F G B♭ = 60 62 65 67 70
        var concretizer = ShangConcretizer.inC();
        var expected = List.of(60, 62, 65, 67, 70);
        for (int i = 0; i < ShangDegree.values().length; i++) {
            assertEquals(expected.get(i), concretizer.midi(ShangNote.ofIndex(i, 4)));
        }
    }

    @Test
    void jue_inC_octave4() {
        // C E♭ F A♭ B♭ = 60 63 65 68 70
        var concretizer = JueConcretizer.inC();
        var expected = List.of(60, 63, 65, 68, 70);
        for (int i = 0; i < JueDegree.values().length; i++) {
            assertEquals(expected.get(i), concretizer.midi(JueNote.ofIndex(i, 4)));
        }
    }

    @Test
    void zhi_inC_octave4() {
        // C D F G A = 60 62 65 67 69 (Egyptian pentatonic)
        var concretizer = ZhiConcretizer.inC();
        var expected = List.of(60, 62, 65, 67, 69);
        for (int i = 0; i < ZhiDegree.values().length; i++) {
            assertEquals(expected.get(i), concretizer.midi(ZhiNote.ofIndex(i, 4)));
        }
    }

    @Test
    void yu_inC_octave4() {
        // C E♭ F G B♭ = 60 63 65 67 70 (minor pentatonic)
        var concretizer = YuConcretizer.inC();
        var expected = List.of(60, 63, 65, 67, 70);
        for (int i = 0; i < YuDegree.values().length; i++) {
            assertEquals(expected.get(i), concretizer.midi(YuNote.ofIndex(i, 4)));
        }
    }

    @Test
    void allChineseModesHaveFiveDegrees() {
        assertEquals(5, GongDegree.values().length);
        assertEquals(5, ShangDegree.values().length);
        assertEquals(5, JueDegree.values().length);
        assertEquals(5, ZhiDegree.values().length);
        assertEquals(5, YuDegree.values().length);
    }

    @Test
    void allChineseModesAreRotationsOfOnePentatonic() {
        // The 5 traditional modes are modal rotations of the same 5-pitch-class set.
        // Playing each mode "from its own tonic" should yield an identical pitch-class
        // multiset (after mod 12) if we choose tonics that align back to Gong's C.
        //
        //   Gong on C      → C D E G A
        //   Shang on D     → D E G A C      (same pitch classes)
        //   Jue on E       → E G A C D
        //   Zhi on G       → G A C D E
        //   Yu on A        → A C D E G
        final var pitchClasses = java.util.Set.of(0, 2, 4, 7, 9); // C D E G A

        assertEquals(pitchClasses, modePitchClasses(0,  // Gong tonic = C
                i -> GongConcretizer.inC().midi(GongNote.ofIndex(i, 4))));
        assertEquals(pitchClasses, modePitchClasses(2,  // Shang tonic = D
                i -> new ShangConcretizer(2).midi(ShangNote.ofIndex(i, 4))));
        assertEquals(pitchClasses, modePitchClasses(4,  // Jue tonic = E
                i -> new JueConcretizer(4).midi(JueNote.ofIndex(i, 4))));
        assertEquals(pitchClasses, modePitchClasses(7,  // Zhi tonic = G
                i -> new ZhiConcretizer(7).midi(ZhiNote.ofIndex(i, 4))));
        assertEquals(pitchClasses, modePitchClasses(9,  // Yu tonic = A
                i -> new YuConcretizer(9).midi(YuNote.ofIndex(i, 4))));
    }

    private static java.util.Set<Integer> modePitchClasses(
            int tonicPitchClass,
            java.util.function.IntUnaryOperator midiFor) {
        return java.util.stream.IntStream.range(0, 5)
                .map(midiFor::applyAsInt)
                .map(m -> Math.floorMod(m, 12))
                .boxed()
                .collect(java.util.stream.Collectors.toSet());
    }

    // ── Cross-family: Japanese ↔ Chinese ───────────────────────────────

    @Test
    void hirajoshiToGong_roundTrips() {
        Transformer<HirajoshiNote, GongNote> t =
                new ScaleTranspose<>(HirajoshiNote::ofIndex, GongNote::ofIndex);

        var source = HirajoshiNote.of(HirajoshiDegree.III, 4);
        assertEquals(source, t.reverse(t.forward(source)));
        assertEquals(source.degreeIndex(), t.forward(source).degreeIndex());
        assertEquals("Gong", t.forward(source).scaleName());
    }

    @Test
    void yuToJue_sameFamilyDifferentMode() {
        // Both minor-coloured Chinese modes, but Jue is Phrygian-like (♭VI),
        // Yu is natural minor pentatonic (V). Same degree index ⇒ different pitch.
        Transformer<YuNote, JueNote> t =
                new ScaleTranspose<>(YuNote::ofIndex, JueNote::ofIndex);

        var yu3 = YuNote.ofIndex(3, 4);      // Yu's 4th degree = V  = 7 semitones
        var jue3 = t.forward(yu3);           // Jue's 4th degree = ♭VI = 8 semitones

        int yuMidi = YuConcretizer.inC().midi(yu3);
        int jueMidi = JueConcretizer.inC().midi(jue3);

        assertEquals(67, yuMidi);   // G4
        assertEquals(68, jueMidi);  // A♭4
        assertEquals(yu3, t.reverse(jue3));
    }

    @Test
    void chainedCrossFamilyTranspose_composesAndReverses() {
        // Hirajoshi → Yu → Gong: three types, chained.
        Transformer<HirajoshiNote, YuNote> hy =
                new ScaleTranspose<>(HirajoshiNote::ofIndex, YuNote::ofIndex);
        Transformer<YuNote, GongNote> yg =
                new ScaleTranspose<>(YuNote::ofIndex, GongNote::ofIndex);
        Transformer<HirajoshiNote, GongNote> chain = hy.andThen(yg);

        var source = HirajoshiNote.of(HirajoshiDegree.V, 4);
        var result = chain.forward(source);

        assertEquals(source.degreeIndex(), result.degreeIndex());
        assertEquals(source.octave(), result.octave());
        assertEquals("Gong", result.scaleName());
        assertEquals(source, chain.reverse(result));
    }

}
