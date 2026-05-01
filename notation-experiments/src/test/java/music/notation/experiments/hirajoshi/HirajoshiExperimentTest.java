package music.notation.experiments.hirajoshi;

import music.notation.core.model.transformation.Transformer;
import music.notation.experiments.hirajoshi.transformer.ShiftOctave;
import music.notation.experiments.hirajoshi.transformer.TransposeDegree;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Experiment exercising the abstract → concrete pipeline:
 * <ol>
 *   <li>Describe a melody in Hirajoshi scale terms (abstract).</li>
 *   <li>Apply typed {@code Transformer}s at the abstract level.</li>
 *   <li>Concretize to MIDI pitches with a chosen tonic.</li>
 *   <li>Verify reversibility and reproducibility along the way.</li>
 * </ol>
 */
class HirajoshiExperimentTest {

    // ─── Abstract layer: basic correctness ────────────────────────────────

    @Test
    void concretizeInC_producesExpectedPitches() {
        // Hirajoshi in C, octave 4: C4 D4 E♭4 G4 A♭4
        final var concretizer = HirajoshiConcretizer.inC();
        final var expectedMidi = List.of(60, 62, 63, 67, 68);

        for (int i = 0; i < HirajoshiDegree.values().length; i++) {
            var abstractNote = HirajoshiNote.of(HirajoshiDegree.values()[i], 4);
            assertEquals(expectedMidi.get(i), concretizer.midi(abstractNote),
                    "degree " + HirajoshiDegree.values()[i]);
        }
    }

    @Test
    void concretizeInD_producesShamisenHirajoshi() {
        // D-tonic Hirajoshi (traditional): D4 E4 F4 A4 B♭4 = MIDI 62 64 65 69 70
        final var concretizer = HirajoshiConcretizer.inD();
        final var expectedMidi = List.of(62, 64, 65, 69, 70);

        for (int i = 0; i < HirajoshiDegree.values().length; i++) {
            var abstractNote = HirajoshiNote.of(HirajoshiDegree.values()[i], 4);
            assertEquals(expectedMidi.get(i), concretizer.midi(abstractNote));
        }
    }

    // ─── Transformer reversibility (the core law) ─────────────────────────

    @Test
    void transposeDegree_roundTripsToOriginal() {
        final Transformer<HirajoshiNote, HirajoshiNote> t = new TransposeDegree(3);
        final var source = HirajoshiNote.of(HirajoshiDegree.II, 4);

        final var shifted = t.forward(source);
        final var restored = t.reverse(shifted);

        assertEquals(source, restored,
                "forward ∘ reverse must be identity (reversibility law)");
    }

    @Test
    void shiftOctave_roundTripsToOriginal() {
        final Transformer<HirajoshiNote, HirajoshiNote> t = new ShiftOctave(-2);
        final var source = HirajoshiNote.of(HirajoshiDegree.V, 4);

        assertEquals(source, t.reverse(t.forward(source)));
    }

    @Test
    void transposeDegree_wrapsAroundIntoHigherOctave() {
        final var t = new TransposeDegree(1);
        // VI + 1 step → wraps to I of the next octave.
        final var source = HirajoshiNote.of(HirajoshiDegree.VI, 4);
        final var shifted = t.forward(source);

        assertEquals(HirajoshiDegree.I, shifted.degree());
        assertEquals(5, shifted.octave(), "wrap must carry into the next octave");
        assertEquals(source, t.reverse(shifted));
    }

    @Test
    void transposeDegree_wrapsBackwardIntoLowerOctave() {
        final var t = new TransposeDegree(-1);
        final var source = HirajoshiNote.of(HirajoshiDegree.I, 4);
        final var shifted = t.forward(source);

        assertEquals(HirajoshiDegree.VI, shifted.degree());
        assertEquals(3, shifted.octave());
        assertEquals(source, t.reverse(shifted));
    }

    // ─── Chained transformers ─────────────────────────────────────────────

    @Test
    void chainedTransforms_composeAndReverseCleanly() {
        // up 2 degrees then up 1 octave.
        final Transformer<HirajoshiNote, HirajoshiNote> chain =
                new TransposeDegree(2).andThen(new ShiftOctave(1));

        final var source = HirajoshiNote.of(HirajoshiDegree.I, 3);
        final var shifted = chain.forward(source);

        // I + 2 steps = III (no wrap), octave 3 → 4.
        assertEquals(HirajoshiDegree.III, shifted.degree());
        assertEquals(4, shifted.octave());

        // Full-chain reversibility.
        assertEquals(source, chain.reverse(shifted));
    }

    @Test
    void identityTransform_isNoOp() {
        final Transformer<HirajoshiNote, HirajoshiNote> id = Transformer.identity();
        final var source = HirajoshiNote.of(HirajoshiDegree.III, 4);

        assertEquals(source, id.forward(source));
        assertEquals(source, id.reverse(source));
    }

    // ─── End-to-end: abstract transform → concretize ──────────────────────

    @Test
    void endToEnd_transformThenConcretize_yieldsExpectedMidi() {
        // Start with a motif in Hirajoshi: I, III, V, VI, I(+1) at octave 4.
        final List<HirajoshiNote> motif = List.of(
                HirajoshiNote.of(HirajoshiDegree.I,  4),
                HirajoshiNote.of(HirajoshiDegree.III, 4),
                HirajoshiNote.of(HirajoshiDegree.V,   4),
                HirajoshiNote.of(HirajoshiDegree.VI,  4),
                HirajoshiNote.of(HirajoshiDegree.I,   5));

        // Abstract-level transform: shift up two scale degrees then up one octave.
        final Transformer<HirajoshiNote, HirajoshiNote> chain =
                new TransposeDegree(2).andThen(new ShiftOctave(1));

        final var transformed = motif.stream().map(chain::forward).toList();

        // Concretize in C.
        final var concretizer = HirajoshiConcretizer.inC();
        final var midi = transformed.stream()
                .map(concretizer::midi)
                .toList();

        // Apply TransposeDegree(+2) then ShiftOctave(+1), then concretize in C:
        //   (I,4)   → (III,4) → (III,5)  → MIDI 12*6 + 3  = 75  (E♭5)
        //   (III,4) → (VI,4)  → (VI,5)   → MIDI 12*6 + 8  = 80  (A♭5)
        //   (V,4)   → (I,5)   → (I,6)    → MIDI 12*7 + 0  = 84  (C6)
        //   (VI,4)  → (II,5)  → (II,6)   → MIDI 12*7 + 2  = 86  (D6)
        //   (I,5)   → (III,5) → (III,6)  → MIDI 12*7 + 3  = 87  (E♭6)
        assertEquals(List.of(75, 80, 84, 86, 87), midi);

        // End-to-end reversibility of the abstract chain.
        final var roundTripped = transformed.stream().map(chain::reverse).toList();
        assertEquals(motif, roundTripped);
    }
}
