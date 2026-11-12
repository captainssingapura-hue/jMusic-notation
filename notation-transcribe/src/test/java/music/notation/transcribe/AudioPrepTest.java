package music.notation.transcribe;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Decoding, down-mixing, windowing and resampling. */
class AudioPrepTest {

    @Test
    void passbandToneSurvivesDownsamplingAtFullLevel() {
        float[] x = Synth.sine(44_100, 3000, 1.0, 0.8);
        float[] y = Resampler.resample(x, 44_100, 16_000);
        assertEquals(16_000, y.length);
        double a = Synth.amplitudeAt(y, 16_000, 3000, 2000, 14_000);
        assertEquals(0.8, a, 0.008, "3 kHz should pass within 1%");
    }

    @Test
    void toneAboveTheNewNyquistDoesNotFoldDown() {
        // 10 kHz at 44.1 kHz would alias to 6 kHz at 16 kHz.
        float[] x = Synth.sine(44_100, 10_000, 1.0, 0.8);
        float[] y = Resampler.resample(x, 44_100, 16_000);
        double alias = Synth.amplitudeAt(y, 16_000, 6000, 2000, 14_000);
        assertTrue(alias < 0.8e-3, "alias at 6 kHz should be below −60 dB: " + alias);
        assertTrue(Synth.rms(y, 2000, 14_000) < 1e-3);
    }

    @Test
    void upsamplingKeepsPitchAndLevel() {
        float[] x = Synth.sine(16_000, 440, 0.5, 0.5);
        float[] y = Resampler.resample(x, 16_000, 48_000);
        assertEquals(24_000, y.length);
        assertEquals(0.5, Synth.amplitudeAt(y, 48_000, 440, 4000, 20_000), 0.005);
    }

    @Test
    void sameRateIsANoOp() {
        var clip = new AudioClip(16_000, new float[]{1, 2, 3});
        assertSame(clip, Resampler.resample(clip, 16_000));
    }

    @Test
    void stereoWavIsReadAsTheAverageOfItsChannels(@TempDir Path dir) throws Exception {
        float[] left = Synth.sine(22_050, 440, 0.2, 0.5);
        float[] right = new float[left.length];
        for (int i = 0; i < right.length; i++) right[i] = -left[i] * 0.5f;       // half, inverted
        Path wav = dir.resolve("st.wav");
        Synth.writeWav(wav, 22_050, left, right);
        AudioClip clip = AudioReader.read(wav);
        assertEquals(22_050, clip.sampleRate());
        assertEquals(left.length, clip.length());
        for (int i = 0; i < left.length; i += 97) assertEquals(left[i] * 0.25, clip.samples()[i], 1e-4);
    }

    @Test
    void slicingKeepsFileTime() {
        var clip = new AudioClip(1000, new float[5000]);
        AudioClip s = clip.slice(new TimeWindow(1.5, 3.0));
        assertEquals(1500, s.length());
        assertEquals(1.5, s.offsetSec(), 1e-9);
        AudioClip again = s.slice(new TimeWindow(2.0, 0));                     // to the end
        assertEquals(1000, again.length());
        assertEquals(2.0, again.offsetSec(), 1e-9);
        assertEquals(2.0, Resampler.resample(again, 16_000).offsetSec(), 1e-9);
    }

    @Test
    void besselMatchesKnownValues() {
        assertEquals(1.0, Resampler.besselI0(0), 1e-15);
        assertEquals(2.2795853023360673, Resampler.besselI0(2), 1e-12);
        assertEquals(27.239871823604442, Resampler.besselI0(5), 1e-10);
    }
}
