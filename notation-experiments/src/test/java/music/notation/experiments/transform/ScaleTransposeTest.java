package music.notation.experiments.transform;

import music.notation.core.model.transformation.Transformer;
import music.notation.experiments.hirajoshi.HirajoshiConcretizer;
import music.notation.experiments.hirajoshi.HirajoshiDegree;
import music.notation.experiments.hirajoshi.HirajoshiNote;
import music.notation.experiments.insen.InsenConcretizer;
import music.notation.experiments.insen.InsenNote;
import music.notation.experiments.iwato.IwatoConcretizer;
import music.notation.experiments.iwato.IwatoNote;
import music.notation.experiments.ryukyu.RyukyuConcretizer;
import music.notation.experiments.ryukyu.RyukyuNote;
import music.notation.experiments.yo.YoConcretizer;
import music.notation.experiments.yo.YoNote;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that cross-scale transposition preserves degree index + octave
 * (reversibility law holds) and that the concrete MIDI output reflects the
 * intended scale change.
 */
class ScaleTransposeTest {

    @Test
    void hirajoshiToYo_preservesDegreeIndexAndOctave() {
        Transformer<HirajoshiNote, YoNote> t =
                new ScaleTranspose<>(HirajoshiNote::ofIndex, YoNote::ofIndex);

        var source = HirajoshiNote.of(HirajoshiDegree.III, 4);
        var target = t.forward(source);

        assertEquals(source.degreeIndex(), target.degreeIndex());
        assertEquals(source.octave(), target.octave());
        assertEquals("Yo", target.scaleName());
    }

    @Test
    void roundTripThroughEveryCrossScaleTransformIsIdentity() {
        var source = HirajoshiNote.of(HirajoshiDegree.III, 4);

        // Hirajoshi → Yo → Hirajoshi
        Transformer<HirajoshiNote, YoNote> hy =
                new ScaleTranspose<>(HirajoshiNote::ofIndex, YoNote::ofIndex);
        assertEquals(source, hy.reverse(hy.forward(source)));

        // Hirajoshi → Insen
        Transformer<HirajoshiNote, InsenNote> hi =
                new ScaleTranspose<>(HirajoshiNote::ofIndex, InsenNote::ofIndex);
        assertEquals(source, hi.reverse(hi.forward(source)));

        // Hirajoshi → Iwato
        Transformer<HirajoshiNote, IwatoNote> hw =
                new ScaleTranspose<>(HirajoshiNote::ofIndex, IwatoNote::ofIndex);
        assertEquals(source, hw.reverse(hw.forward(source)));

        // Hirajoshi → Ryukyu
        Transformer<HirajoshiNote, RyukyuNote> hr =
                new ScaleTranspose<>(HirajoshiNote::ofIndex, RyukyuNote::ofIndex);
        assertEquals(source, hr.reverse(hr.forward(source)));
    }

    @Test
    void concretizedOutput_differsBetweenScales() {
        // Same abstract degree index (2 = third position), same octave (4),
        // same tonic (C). The *scale-through* concretization must differ.
        int degreeIndex = 2;
        int midiHirajoshi = HirajoshiConcretizer.inC()
                .midi(HirajoshiNote.ofIndex(degreeIndex, 4));
        int midiYo = YoConcretizer.inC()
                .midi(YoNote.ofIndex(degreeIndex, 4));
        int midiInsen = InsenConcretizer.inC()
                .midi(InsenNote.ofIndex(degreeIndex, 4));
        int midiIwato = IwatoConcretizer.inC()
                .midi(IwatoNote.ofIndex(degreeIndex, 4));
        int midiRyukyu = RyukyuConcretizer.inC()
                .midi(RyukyuNote.ofIndex(degreeIndex, 4));

        // 3rd degree concrete pitches in C, each scale:
        //   Hirajoshi   E♭4 = 63
        //   Yo          F4  = 65
        //   Insen       F4  = 65
        //   Iwato       F4  = 65
        //   Ryukyu      F4  = 65
        assertEquals(63, midiHirajoshi);
        assertEquals(65, midiYo);
        assertEquals(65, midiInsen);
        assertEquals(65, midiIwato);
        assertEquals(65, midiRyukyu);
    }

    @Test
    void melodyThroughScaleTransposeAndConcretization() {
        // A five-note motif at degrees 0 1 2 3 4 (octave 4) in Hirajoshi.
        var motif = List.of(
                HirajoshiNote.ofIndex(0, 4),
                HirajoshiNote.ofIndex(1, 4),
                HirajoshiNote.ofIndex(2, 4),
                HirajoshiNote.ofIndex(3, 4),
                HirajoshiNote.ofIndex(4, 4));

        // Transpose to Ryukyu, then concretize in C.
        Transformer<HirajoshiNote, RyukyuNote> toRyukyu =
                new ScaleTranspose<>(HirajoshiNote::ofIndex, RyukyuNote::ofIndex);
        var rConc = RyukyuConcretizer.inC();

        var midi = motif.stream()
                .map(toRyukyu::forward)
                .map(rConc::midi)
                .toList();

        // Ryukyu in C at octave 4: C E F G B = 60 64 65 67 71
        assertEquals(List.of(60, 64, 65, 67, 71), midi);
    }

    @Test
    void chainedCrossScaleTransposes_composeAndReverseCleanly() {
        // Hirajoshi → Yo → Insen.
        Transformer<HirajoshiNote, YoNote> hy =
                new ScaleTranspose<>(HirajoshiNote::ofIndex, YoNote::ofIndex);
        Transformer<YoNote, InsenNote> yi =
                new ScaleTranspose<>(YoNote::ofIndex, InsenNote::ofIndex);
        Transformer<HirajoshiNote, InsenNote> chain = hy.andThen(yi);

        var source = HirajoshiNote.of(HirajoshiDegree.V, 4);
        var afterChain = chain.forward(source);

        assertEquals(source.degreeIndex(), afterChain.degreeIndex());
        assertEquals(source.octave(), afterChain.octave());
        assertEquals("Insen", afterChain.scaleName());
        assertEquals(source, chain.reverse(afterChain));
    }
}
