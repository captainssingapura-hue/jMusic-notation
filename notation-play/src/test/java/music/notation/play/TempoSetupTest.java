package music.notation.play;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TempoSetupTest {

    @Test
    void unityIsOne() {
        assertEquals(1.0, TempoSetup.unity().bpmFactor(), 0.0001);
    }

    @Test
    void factorRoundTrip() {
        assertEquals(1.5, TempoSetup.factor(1.5).bpmFactor(), 0.0001);
    }

    @Test
    void atBpmDerivesFactor() {
        var t = TempoSetup.atBpm(144, 120);
        assertEquals(1.2, t.bpmFactor(), 0.0001);
    }

    @Test
    void zeroFactorRejected() {
        assertThrows(IllegalArgumentException.class, () -> new TempoSetup(0.0));
    }

    @Test
    void negativeFactorRejected() {
        assertThrows(IllegalArgumentException.class, () -> new TempoSetup(-1.0));
    }

    @Test
    void atBpmZeroAuthoredRejected() {
        assertThrows(IllegalArgumentException.class, () -> TempoSetup.atBpm(120, 0));
    }

    @Test
    void applyOnNullSequencerIsNoOp() {
        // Doesn't throw — graceful when the player isn't running yet.
        TempoSetup.unity().apply(null);
    }
}
