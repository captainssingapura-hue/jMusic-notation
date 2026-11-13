package music.notation.transcribe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The RMVPE front end, checked against the definitions it reproduces
 * (torch.stft centred/reflect/periodic Hann; librosa HTK mel with Slaney
 * normalisation). No reference data needed: each property follows from
 * the formulas.
 */
class LogMelSpectrogramTest {

    private static final LogMelSpec S = LogMelSpec.RMVPE;

    @Test
    void htkMelScale() {
        assertEquals(1000.0, LogMelSpectrogram.hzToMel(1000), 0.02);       // HTK: 1 kHz ≈ 1000 mel
        assertEquals(0.0, LogMelSpectrogram.hzToMel(0), 1e-12);
        for (double hz : new double[]{30, 440, 1234.5, 8000})
            assertEquals(hz, LogMelSpectrogram.melToHz(LogMelSpectrogram.hzToMel(hz)), 1e-9);
    }

    @Test
    void periodicHannAndReflectPadding() {
        double[] w = LogMelSpectrogram.hann(8);
        assertEquals(0.0, w[0], 1e-15);
        assertEquals(1.0, w[4], 1e-15);                                    // periodic: peak at N/2
        assertEquals(w[1], w[7], 1e-15);
        double[] p = LogMelSpectrogram.reflectPad(new float[]{1, 2, 3, 4, 5}, 2);
        assertArrayEquals(new double[]{3, 2, 1, 2, 3, 4, 5, 4, 3}, p, 0);
    }

    @Test
    void stftFrameCountAndASineOnABinCentre() {
        // 1 kHz is exactly bin 64 of a 1024-point FFT at 16 kHz.
        float[] x = Synth.sine(16_000, 1000, 1.0, 1.0);
        double[][] mag = LogMelSpectrogram.magnitudes(x, S);
        assertEquals(1 + 16_000 / 160, mag.length);
        assertEquals(513, mag[0].length);
        double[] mid = mag[50];
        int peak = 0;
        for (int b = 1; b < mid.length; b++) if (mid[b] > mid[peak]) peak = b;
        assertEquals(64, peak);
        assertEquals(1024 / 4.0, mid[64], 1.0, "|X| = A·N/4 with a Hann window");
        assertEquals(1024 / 8.0, mid[63], 1.0, "Hann leakage: half height in the neighbouring bins");
    }

    @Test
    void filterbankShapeAndSlaneyArea() {
        double[][] fb = LogMelSpectrogram.melFilterbank(S);
        assertEquals(128, fb.length);
        assertEquals(513, fb[0].length);
        double binHz = (double) S.sampleRate() / S.nFft();
        int prevPeak = -1;
        for (int m = 0; m < fb.length; m++) {
            int peak = 0, nonZero = 0;
            double area = 0;
            for (int b = 0; b < fb[m].length; b++) {
                assertTrue(fb[m][b] >= 0);
                if (fb[m][b] > 0) nonZero++;
                if (fb[m][b] > fb[m][peak]) peak = b;
                area += fb[m][b] * binHz;
            }
            if (nonZero > 0) {
                assertTrue(peak >= prevPeak, "filters climb in frequency");
                prevPeak = peak;
            }
            // Wide filters integrate to ~1 (Slaney normalisation); narrow low ones are under-sampled.
            if (nonZero >= 8) assertEquals(1.0, area, 0.03, "area of band " + m);
            // Nothing outside the band's edges.
            double lo = LogMelSpectrogram.melToHz(LogMelSpectrogram.hzToMel(S.fMin())
                    + (LogMelSpectrogram.hzToMel(S.fMax()) - LogMelSpectrogram.hzToMel(S.fMin())) * m / 129.0);
            for (int b = 0; b * binHz <= lo; b++) assertEquals(0.0, fb[m][b], 1e-12);
        }
        assertEquals(0.0, fb[127][512], 1e-12, "8 kHz is the top edge");
    }

    @Test
    void logMelPutsAToneInItsBandAndSilenceAtTheFloor() {
        float[] x = Synth.sine(16_000, 1000, 0.5, 0.5);
        float[][] lm = LogMelSpectrogram.compute(x, S);
        assertEquals(128, lm.length);
        assertEquals(S.frameCount(x.length), lm[0].length);
        int best = 0;
        for (int m = 1; m < 128; m++) if (lm[m][25] > lm[best][25]) best = m;
        double centre = LogMelSpectrogram.bandCentreHz(S, best);
        assertTrue(Math.abs(centre - 1000) < 40, "loudest band centred at " + centre + " Hz");

        float[][] silent = LogMelSpectrogram.compute(new float[1600], S);
        for (float[] band : silent) for (float v : band) assertEquals((float) Math.log(1e-5), v, 1e-6);
    }

    @Test
    void specValidation() {
        assertThrows(IllegalArgumentException.class, () -> new LogMelSpec(16_000, 1000, 160, 128, 30, 8000, 1e-5));
        assertThrows(IllegalArgumentException.class, () -> new LogMelSpec(16_000, 1024, 160, 128, 30, 9000, 1e-5));
        assertEquals(10.0, S.hopMs(), 1e-12);
    }
}
