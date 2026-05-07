package music.notation.expressivity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VelocityControlTest {

    @Test
    void emptyControlReturnsDefaultForAnyQuery() {
        VelocityControl ctrl = VelocityControl.empty();
        assertEquals(VelocityControl.DEFAULT_VELOCITY, ctrl.velocityAt(0));
        assertEquals(VelocityControl.DEFAULT_VELOCITY, ctrl.velocityAt(1_000_000));
    }

    @Test
    void velocityBeforeFirstChangeIsDefault() {
        VelocityControl ctrl = new VelocityControl(List.of(
                new VelocityChange(1000, 80)));
        assertEquals(VelocityControl.DEFAULT_VELOCITY, ctrl.velocityAt(0));
        assertEquals(VelocityControl.DEFAULT_VELOCITY, ctrl.velocityAt(999));
        assertEquals(80, ctrl.velocityAt(1000));
        assertEquals(80, ctrl.velocityAt(5000));
    }

    @Test
    void stepFunctionLookup() {
        VelocityControl ctrl = new VelocityControl(List.of(
                new VelocityChange(0,    64),    // mp
                new VelocityChange(2000, 96),    // f
                new VelocityChange(4000, 49)));  // p
        assertEquals(64, ctrl.velocityAt(0));
        assertEquals(64, ctrl.velocityAt(1999));
        assertEquals(96, ctrl.velocityAt(2000));
        assertEquals(96, ctrl.velocityAt(3999));
        assertEquals(49, ctrl.velocityAt(4000));
        assertEquals(49, ctrl.velocityAt(10_000));
    }

    @Test
    void consecutiveSameVelocityIsDeduped() {
        VelocityControl ctrl = new VelocityControl(List.of(
                new VelocityChange(0,    80),
                new VelocityChange(1000, 80),  // duplicate — drop
                new VelocityChange(2000, 80),  // duplicate — drop
                new VelocityChange(3000, 96)));
        assertEquals(2, ctrl.changes().size());
        assertEquals(80, ctrl.changes().get(0).velocity());
        assertEquals(96, ctrl.changes().get(1).velocity());
    }

    @Test
    void unsortedInputIsSorted() {
        VelocityControl ctrl = new VelocityControl(List.of(
                new VelocityChange(2000, 96),
                new VelocityChange(0,    64),
                new VelocityChange(1000, 80)));
        var changes = ctrl.changes();
        assertEquals(0L,    changes.get(0).tickMs());
        assertEquals(1000L, changes.get(1).tickMs());
        assertEquals(2000L, changes.get(2).tickMs());
    }

    @Test
    void velocityZeroRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new VelocityChange(0, 0));
    }

    @Test
    void velocityOver127Rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new VelocityChange(0, 128));
    }

    @Test
    void negativeTickMsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new VelocityChange(-1, 80));
    }

    @Test
    void constantHelperPinsFlatVelocity() {
        VelocityControl ctrl = VelocityControl.constant(100);
        assertEquals(1, ctrl.changes().size());
        assertEquals(100, ctrl.velocityAt(0));
        assertEquals(100, ctrl.velocityAt(50_000));
    }
}
