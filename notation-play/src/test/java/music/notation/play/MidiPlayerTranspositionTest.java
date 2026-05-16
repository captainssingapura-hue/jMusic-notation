package music.notation.play;

import music.notation.performance.TransposeTransform;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * State-management tests for {@link MidiPlayer}'s transposition surface
 * (T.2 of the transposition work).
 *
 * <p>Verifies the staging semantics — {@code setTransposition},
 * {@code getTransposition}, and the null-resets-to-NONE convention.
 * The actual pitch-shift behaviour of the transform itself is covered
 * by {@code TransposeTransformTest} in notation-performance; here we
 * only check that {@link MidiPlayer} mirrors the same staging
 * convention used for {@code HumanizerSetup}, {@code Pedaling}, etc.</p>
 */
class MidiPlayerTranspositionTest {

    @Test
    void defaultTranspositionIsNone() {
        MidiPlayer player = new MidiPlayer();
        assertSame(TransposeTransform.Params.NONE, player.getTransposition(),
                "fresh MidiPlayer starts with NONE — no transposition");
    }

    @Test
    void setTranspositionStoresValue() {
        MidiPlayer player = new MidiPlayer();
        var up5 = TransposeTransform.Params.of(5);
        player.setTransposition(up5);
        assertEquals(up5, player.getTransposition());
        assertEquals(5, player.getTransposition().semitoneShift());
    }

    @Test
    void setTranspositionNullResetsToNone() {
        MidiPlayer player = new MidiPlayer();
        player.setTransposition(TransposeTransform.Params.of(7));
        assertEquals(7, player.getTransposition().semitoneShift());

        player.setTransposition(null);
        assertSame(TransposeTransform.Params.NONE, player.getTransposition(),
                "null params resets back to NONE (matches setPedaling/setHumanizer convention)");
    }

    @Test
    void setTranspositionAcceptsNegativeShifts() {
        MidiPlayer player = new MidiPlayer();
        player.setTransposition(TransposeTransform.Params.of(-7));
        assertEquals(-7, player.getTransposition().semitoneShift());
    }

    @Test
    void setTranspositionAcceptsZero() {
        MidiPlayer player = new MidiPlayer();
        player.setTransposition(TransposeTransform.Params.of(5));
        player.setTransposition(TransposeTransform.Params.of(0));
        assertEquals(0, player.getTransposition().semitoneShift());
        assertTrue(player.getTransposition().isOff());
    }

    @Test
    void applyTranspositionWhenNotRunningIsNoOpButStages() throws Exception {
        // No sequencer running → applyTransposition only stages the params,
        // doesn't try to restart anything. Mirrors applyHumanizer's behaviour.
        MidiPlayer player = new MidiPlayer();
        player.applyTransposition(TransposeTransform.Params.of(3), 0L);
        assertEquals(3, player.getTransposition().semitoneShift(),
                "applyTransposition stages even when not running");
    }

    @Test
    void applyTranspositionNullParamsIsNoOp() throws Exception {
        // Convention: applyHumanizer ignores null. applyTransposition should too —
        // otherwise an accidental null mid-playback would silently reset to NONE.
        MidiPlayer player = new MidiPlayer();
        player.setTransposition(TransposeTransform.Params.of(5));
        player.applyTransposition(null, 0L);
        assertEquals(5, player.getTransposition().semitoneShift(),
                "null params to applyTransposition is no-op, preserves prior value");
    }
}
