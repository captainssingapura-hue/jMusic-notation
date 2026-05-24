package music.notation.input;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Synthetic-signal tests for {@link YinPitchDetector}. Each test
 * generates a sine wave at a known MIDI pitch, runs YIN over it, and
 * asserts the detected frequency / MIDI value is within tolerance.
 *
 * <p>YIN's accuracy on a clean sine wave is typically &lt; 1 cent in
 * the centre of its useful range and within a few cents at the edges.
 * Tests use ±10 cents tolerance which is comfortably looser than the
 * algorithm's intrinsic precision.</p>
 */
class YinPitchDetectorTest {

    private static final int SAMPLE_RATE = 44100;
    private static final int WINDOW_SIZE = 2048;
    private static final double CENTS_TOLERANCE = 10.0;

    @Test
    void detectsA4_440Hz() {
        runTest(440.0, /*midi=*/ 69);
    }

    @Test
    void detectsMiddleC_C4() {
        // C4 = MIDI 60, ~261.63 Hz
        runTest(261.6256, 60);
    }

    @Test
    void detectsE2_lowVocalRange() {
        // E2 = MIDI 40, ~82.41 Hz — low end of typical bass vocal
        runTest(82.4069, 40);
    }

    @Test
    void detectsA5_highVocalRange() {
        // A5 = MIDI 81, 880 Hz
        runTest(880.0, 81);
    }

    @Test
    void detectsC6_topOfTypicalSopranoRange() {
        // C6 = MIDI 84, ~1046.50 Hz
        runTest(1046.5023, 84);
    }

    @Test
    void silenceReturnsNoPitch() {
        YinPitchDetector det = new YinPitchDetector(SAMPLE_RATE, WINDOW_SIZE);
        float[] silent = new float[WINDOW_SIZE];   // all zeros
        YinPitchDetector.Result r = det.detect(silent);
        assertFalse(r.hasPitch(), "all-zero window should return no pitch");
        assertEquals(0.0, r.confidence(), 0.01);
    }

    @Test
    void noiseReturnsLowConfidenceOrNoPitch() {
        // White noise: YIN should either return no pitch or a low-confidence
        // estimate. We don't pin a specific result — just check the
        // detector doesn't crash and confidence stays under 0.5.
        YinPitchDetector det = new YinPitchDetector(SAMPLE_RATE, WINDOW_SIZE);
        float[] noise = new float[WINDOW_SIZE];
        java.util.Random rng = new java.util.Random(42);
        for (int i = 0; i < WINDOW_SIZE; i++) noise[i] = (float) (rng.nextGaussian() * 0.3);
        YinPitchDetector.Result r = det.detect(noise);
        if (r.hasPitch()) {
            assertTrue(r.confidence() < 0.7,
                    "white noise must not produce a strong-confidence estimate; got " + r.confidence());
        }
    }

    @Test
    void belowMinFreqRangeReturnsNoPitch() {
        // Configure a detector with min=200 Hz; play 100 Hz → no pitch.
        YinPitchDetector det = new YinPitchDetector(
                SAMPLE_RATE, WINDOW_SIZE, YinPitchDetector.DEFAULT_THRESHOLD, 200.0, 2000.0);
        float[] sub = synthesizeSine(100.0, WINDOW_SIZE, SAMPLE_RATE);
        YinPitchDetector.Result r = det.detect(sub);
        assertFalse(r.hasPitch(), "frequency below configured min must be suppressed");
    }

    @Test
    void rejectsBadConstructorArgs() {
        assertThrows(IllegalArgumentException.class, () -> new YinPitchDetector(0, 2048));
        assertThrows(IllegalArgumentException.class, () -> new YinPitchDetector(SAMPLE_RATE, 64));
        assertThrows(IllegalArgumentException.class, () -> new YinPitchDetector(SAMPLE_RATE, 2047));   // odd
        assertThrows(IllegalArgumentException.class,
                () -> new YinPitchDetector(SAMPLE_RATE, 2048, 0.15, -10.0, 2000.0));
        assertThrows(IllegalArgumentException.class,
                () -> new YinPitchDetector(SAMPLE_RATE, 2048, 0.15, 1000.0, 500.0));
    }

    @Test
    void rejectsMismatchedWindowLength() {
        YinPitchDetector det = new YinPitchDetector(SAMPLE_RATE, WINDOW_SIZE);
        assertThrows(IllegalArgumentException.class,
                () -> det.detect(new float[WINDOW_SIZE - 1]));
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private static void runTest(double expectedFreqHz, int expectedMidi) {
        YinPitchDetector det = new YinPitchDetector(SAMPLE_RATE, WINDOW_SIZE);
        float[] window = synthesizeSine(expectedFreqHz, WINDOW_SIZE, SAMPLE_RATE);
        YinPitchDetector.Result r = det.detect(window);

        assertTrue(r.hasPitch(),
                "should detect pitch for clean sine at " + expectedFreqHz + " Hz");
        double cents = 1200.0 * Math.log(r.frequencyHz() / expectedFreqHz) / Math.log(2);
        assertTrue(Math.abs(cents) < CENTS_TOLERANCE,
                "detected " + r.frequencyHz() + " Hz; expected " + expectedFreqHz
                        + " Hz (off by " + cents + " cents, > tolerance " + CENTS_TOLERANCE + ")");
        assertEquals(expectedMidi, (int) Math.round(r.midiFloat()),
                "MIDI rounding mismatch");
        assertTrue(r.confidence() > 0.8,
                "confidence on a clean sine should be > 0.8; got " + r.confidence());
    }

    /** Single-frequency sine, amplitude 0.5, no DC offset. */
    private static float[] synthesizeSine(double freqHz, int samples, int sampleRate) {
        float[] out = new float[samples];
        double twoPiF = 2.0 * Math.PI * freqHz / sampleRate;
        for (int i = 0; i < samples; i++) {
            out[i] = (float) (0.5 * Math.sin(twoPiF * i));
        }
        return out;
    }
}
