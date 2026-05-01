package music.notation.experiments.scale;

import music.notation.experiments.hirajoshi.HirajoshiConcretizer;
import music.notation.experiments.hirajoshi.HirajoshiDegree;
import music.notation.experiments.hirajoshi.HirajoshiNote;
import music.notation.experiments.insen.InsenConcretizer;
import music.notation.experiments.insen.InsenDegree;
import music.notation.experiments.insen.InsenNote;
import music.notation.experiments.iwato.IwatoConcretizer;
import music.notation.experiments.iwato.IwatoDegree;
import music.notation.experiments.iwato.IwatoNote;
import music.notation.experiments.ryukyu.RyukyuConcretizer;
import music.notation.experiments.ryukyu.RyukyuDegree;
import music.notation.experiments.ryukyu.RyukyuNote;
import music.notation.experiments.yo.YoConcretizer;
import music.notation.experiments.yo.YoDegree;
import music.notation.experiments.yo.YoNote;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Each of the five Japanese pentatonic scales, in C at octave 4, should
 * concretize to its canonical MIDI sequence.
 */
class JapaneseScalesTest {

    @Test
    void hirajoshi_inC_octave4() {
        // C, D, E♭, G, A♭ = 60, 62, 63, 67, 68
        var concretizer = HirajoshiConcretizer.inC();
        var expected = List.of(60, 62, 63, 67, 68);
        for (int i = 0; i < HirajoshiDegree.values().length; i++) {
            var note = HirajoshiNote.ofIndex(i, 4);
            assertEquals(expected.get(i), concretizer.midi(note));
        }
    }

    @Test
    void yo_inC_octave4() {
        // C, D, F, G, A = 60, 62, 65, 67, 69
        var concretizer = YoConcretizer.inC();
        var expected = List.of(60, 62, 65, 67, 69);
        for (int i = 0; i < YoDegree.values().length; i++) {
            var note = YoNote.ofIndex(i, 4);
            assertEquals(expected.get(i), concretizer.midi(note));
        }
    }

    @Test
    void insen_inC_octave4() {
        // C, D♭, F, G, B♭ = 60, 61, 65, 67, 70
        var concretizer = InsenConcretizer.inC();
        var expected = List.of(60, 61, 65, 67, 70);
        for (int i = 0; i < InsenDegree.values().length; i++) {
            var note = InsenNote.ofIndex(i, 4);
            assertEquals(expected.get(i), concretizer.midi(note));
        }
    }

    @Test
    void iwato_inC_octave4() {
        // C, D♭, F, G♭, B♭ = 60, 61, 65, 66, 70
        var concretizer = IwatoConcretizer.inC();
        var expected = List.of(60, 61, 65, 66, 70);
        for (int i = 0; i < IwatoDegree.values().length; i++) {
            var note = IwatoNote.ofIndex(i, 4);
            assertEquals(expected.get(i), concretizer.midi(note));
        }
    }

    @Test
    void ryukyu_inC_octave4() {
        // C, E, F, G, B = 60, 64, 65, 67, 71
        var concretizer = RyukyuConcretizer.inC();
        var expected = List.of(60, 64, 65, 67, 71);
        for (int i = 0; i < RyukyuDegree.values().length; i++) {
            var note = RyukyuNote.ofIndex(i, 4);
            assertEquals(expected.get(i), concretizer.midi(note));
        }
    }

    @Test
    void allScalesShareFiveDegrees() {
        assertEquals(5, HirajoshiDegree.values().length);
        assertEquals(5, YoDegree.values().length);
        assertEquals(5, InsenDegree.values().length);
        assertEquals(5, IwatoDegree.values().length);
        assertEquals(5, RyukyuDegree.values().length);

        // And the ScaleNote view reports it consistently.
        assertEquals(5, HirajoshiNote.ofIndex(0, 4).degreeCount());
        assertEquals(5, YoNote.ofIndex(0, 4).degreeCount());
        assertEquals(5, InsenNote.ofIndex(0, 4).degreeCount());
        assertEquals(5, IwatoNote.ofIndex(0, 4).degreeCount());
        assertEquals(5, RyukyuNote.ofIndex(0, 4).degreeCount());
    }
}
