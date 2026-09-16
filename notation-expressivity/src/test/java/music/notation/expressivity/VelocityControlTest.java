package music.notation.expressivity;

import music.notation.duration.Duration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VelocityControlTest {

    private static final double EPS = 1e-9;

    /** Convenience: a Duration at "N quarters from start". */
    private static Duration qq(long quarters) { return Duration.of(quarters, 4); }

    @Test
    void emptyControlReturnsDefaultForAnyQuery() {
        VelocityControl ctrl = VelocityControl.empty();
        assertEquals(VelocityControl.DEFAULT_LEVEL, ctrl.levelAt(Duration.zero()), EPS);
        assertEquals(VelocityControl.DEFAULT_LEVEL, ctrl.levelAt(qq(1000)), EPS);
    }

    @Test
    void levelBeforeFirstChangeIsDefault() {
        VelocityControl ctrl = new VelocityControl(List.of(
                new VelocityChange(qq(4), 0.63)));
        assertEquals(VelocityControl.DEFAULT_LEVEL, ctrl.levelAt(Duration.zero()), EPS);
        assertEquals(VelocityControl.DEFAULT_LEVEL, ctrl.levelAt(qq(3)), EPS);
        assertEquals(0.63, ctrl.levelAt(qq(4)),  EPS);
        assertEquals(0.63, ctrl.levelAt(qq(20)), EPS);
    }

    @Test
    void stepFunctionLookup() {
        VelocityControl ctrl = new VelocityControl(List.of(
                new VelocityChange(qq(0),  0.50),   // mp at start
                new VelocityChange(qq(8),  0.76),   // f  at bar 3
                new VelocityChange(qq(16), 0.39))); // p  at bar 5
        assertEquals(0.50, ctrl.levelAt(qq(0)),  EPS);
        assertEquals(0.50, ctrl.levelAt(qq(7)),  EPS);
        assertEquals(0.76, ctrl.levelAt(qq(8)),  EPS);
        assertEquals(0.76, ctrl.levelAt(qq(15)), EPS);
        assertEquals(0.39, ctrl.levelAt(qq(16)), EPS);
        assertEquals(0.39, ctrl.levelAt(qq(40)), EPS);
    }

    @Test
    void consecutiveSameLevelIsDeduped() {
        VelocityControl ctrl = new VelocityControl(List.of(
                new VelocityChange(qq(0),  0.63),
                new VelocityChange(qq(4),  0.63),  // duplicate — drop
                new VelocityChange(qq(8),  0.63),  // duplicate — drop
                new VelocityChange(qq(12), 0.76)));
        assertEquals(2, ctrl.changes().size());
        assertEquals(0.63, ctrl.changes().get(0).level(), EPS);
        assertEquals(0.76, ctrl.changes().get(1).level(), EPS);
    }

    @Test
    void unsortedInputIsSorted() {
        VelocityControl ctrl = new VelocityControl(List.of(
                new VelocityChange(qq(8), 0.76),
                new VelocityChange(qq(0), 0.50),
                new VelocityChange(qq(4), 0.63)));
        var changes = ctrl.changes();
        assertTrue(qq(0).equalsDuration(changes.get(0).at()));
        assertTrue(qq(4).equalsDuration(changes.get(1).at()));
        assertTrue(qq(8).equalsDuration(changes.get(2).at()));
    }

    @Test
    void levelZeroIsAllowed() {
        // silence-attack is legal in the model; the codec floors to MIDI 1.
        assertDoesNotThrow(() -> new VelocityChange(Duration.zero(), 0.0));
    }

    @Test
    void levelAboveOneRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new VelocityChange(Duration.zero(), 1.0001));
    }

    @Test
    void levelBelowZeroRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new VelocityChange(Duration.zero(), -0.0001));
    }

    @Test
    void levelNaNRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new VelocityChange(Duration.zero(), Double.NaN));
    }

    @Test
    void nullAtRejected() {
        assertThrows(NullPointerException.class,
                () -> new VelocityChange(null, 0.63));
    }

    @Test
    void constantHelperPinsFlatLevel() {
        VelocityControl ctrl = VelocityControl.constant(0.85);
        assertEquals(1, ctrl.changes().size());
        assertEquals(0.85, ctrl.levelAt(Duration.zero()), EPS);
        assertEquals(0.85, ctrl.levelAt(qq(200)),         EPS);
    }
}
