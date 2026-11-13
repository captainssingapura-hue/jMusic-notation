package music.notation.transcribe.bench;

import music.notation.input.PitchEstimate;
import music.notation.transcribe.PitchDetectorSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class LivePitchTrackerTest {

    private static final int RATE = 44_100;

    /** A sung-like tone: harmonics 1–6, at {@code midi}, between {@code fromSec} and {@code toSec}. */
    private static float[] tone(double totalSec, double fromSec, double toSec, double midi) {
        float[] x = new float[(int) (totalSec * RATE)];
        double f = 440 * Math.pow(2, (midi - 69) / 12.0);
        for (int i = (int) (fromSec * RATE); i < Math.min(x.length, (int) (toSec * RATE)); i++) {
            double t = (double) i / RATE, s = 0;
            for (int k = 1; k <= 6; k++) s += Math.sin(2 * Math.PI * f * k * t) / k;
            x[i] = (float) (0.2 * s);
        }
        return x;
    }

    /** Feed {@code x} in blocks of the given sizes, cycling. */
    private static LivePitchTracker feed(float[] x, int... blockSizes) {
        var t = new LivePitchTracker(RATE, PitchDetectorSpec.Yin.DEFAULT);
        int at = 0, b = 0;
        while (at < x.length) {
            int n = Math.min(blockSizes[b++ % blockSizes.length], x.length - at);
            float[] block = new float[n];
            System.arraycopy(x, at, block, 0, n);
            t.accept(block);
            at += n;
        }
        return t;
    }

    @Test
    void framesEvery10MsStampedAtTheirWindowCentre() {
        var t = feed(tone(2.0, 0, 2.0, 69), 882);
        List<PitchEstimate> all = t.between(0, 10_000);
        assertEquals(10.0, t.hopMs(), 1e-9);
        // First frame needs one whole window; its centre is 1024 samples in (≈ 23 ms).
        assertEquals(23, all.getFirst().tickMs());
        assertEquals(10, all.get(1).tickMs() - all.get(0).tickMs());
        assertEquals(1 + (2 * RATE - LivePitchTracker.WINDOW) / 441, all.size());
        double median = all.stream().filter(PitchEstimate::hasPitch).mapToDouble(PitchEstimate::midiFloat)
                .sorted().toArray()[all.size() / 2];
        assertEquals(69, median, 0.05);
    }

    @Test
    void blockSizeDoesNotChangeTheResult() {
        float[] x = tone(1.5, 0.2, 1.3, 62);
        var a = feed(x, 882).between(0, 10_000);
        var b = feed(x, 17, 4000, 333, 1).between(0, 10_000);
        assertEquals(a, b);
    }

    @Test
    void readoutFollowsTheVoiceAndGoesBlankInSilence() {
        // A4 for 1 s, then E5 for 1 s, then 0.5 s of near-silence.
        float[] x = new float[(int) (2.5 * RATE)];
        float[] a4 = tone(2.5, 0, 1.0, 69), e5 = tone(2.5, 1.0, 2.0, 76);
        Random rnd = new Random(1);
        for (int i = 0; i < x.length; i++) x[i] = a4[i] + e5[i] + (float) (0.0005 * rnd.nextGaussian());

        var t = new LivePitchTracker(RATE, PitchDetectorSpec.Yin.DEFAULT);
        float[] block = new float[882];
        double atA4 = Double.NaN, atE5 = Double.NaN;
        for (int at = 0; at + block.length <= x.length; at += block.length) {
            System.arraycopy(x, at, block, 0, block.length);
            t.accept(block);
            double sec = (at + block.length) / (double) RATE;
            if (Math.abs(sec - 0.8) < 0.011) atA4 = t.currentMidi(120);
            if (Math.abs(sec - 1.8) < 0.011) atE5 = t.currentMidi(120);
        }
        assertEquals(69, atA4, 0.1);
        assertEquals(76, atE5, 0.1);
        assertTrue(Double.isNaN(t.currentMidi(120)), "silence at the end reads as no pitch");
        assertTrue(t.level() < 0.003);
        assertTrue(t.peakRms() > 0.05);
    }

    @Test
    void betweenReturnsOnlyTheAskedSpan() {
        var t = feed(tone(3.0, 0, 3.0, 60), 882);
        var span = t.between(1000, 1500);
        assertFalse(span.isEmpty());
        assertTrue(span.getFirst().tickMs() >= 1000 && span.getLast().tickMs() <= 1500);
        assertEquals(50, span.size(), "frames at 1003, 1013, … 1493 ms");
        assertTrue(t.between(5000, 6000).isEmpty());
    }
}
