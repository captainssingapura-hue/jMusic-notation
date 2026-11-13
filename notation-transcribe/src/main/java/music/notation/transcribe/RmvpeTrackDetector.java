package music.notation.transcribe;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import music.notation.input.PitchEstimate;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

/**
 * RMVPE (Wei et al. 2023), a neural pitch tracker that is robust to
 * accompaniment, run through ONNX Runtime. Mirrors RVC's {@code rmvpe.py}:
 *
 * <ol>
 *   <li>{@link LogMelSpectrogram} with {@link LogMelSpec#RMVPE} → {@code [128][T]}
 *       — one frame per 10 ms, centred, so frames line up with YIN's;</li>
 *   <li>the model maps {@code [1, 128, T]} (T padded to a multiple of 32)
 *       to salience {@code [1, T, 360]}: bin {@code i} is
 *       {@code 1997.38 + 20·i} cents above 10 Hz (≈ 32 Hz – 2 kHz);</li>
 *   <li>{@link #decode}: a frame is voiced when its peak salience exceeds
 *       the threshold; its pitch is the salience-weighted mean of the
 *       nine bins around the peak.</li>
 * </ol>
 *
 * Each frame's {@code rms} is reported as the frame's RMS times its peak
 * salience — an estimate of how loud the <em>voice</em> is. Over an
 * accompaniment the plain RMS is mostly the backing and does not dip
 * between repeated notes; the salience does, so the segmenter's gate and
 * loudness-dip split keep working. ({@code minRms} still tests the plain RMS.)
 *
 * <p>Long clips run in chunks with context on both sides, so memory stays
 * bounded; each chunk's edges are discarded.
 */
public final class RmvpeTrackDetector implements PitchDetector {

    /** Frames per chunk kept (20.48 s) and context frames discarded on each side. */
    static final int CHUNK = 2048, CONTEXT = 128;
    static final int BINS = 360;
    static final double CENTS_BIN0 = 1997.3794084376191, CENTS_PER_BIN = 20;

    private final PitchDetectorSpec.Rmvpe spec;

    public RmvpeTrackDetector(PitchDetectorSpec.Rmvpe spec) {
        this.spec = Objects.requireNonNull(spec, "spec");
    }

    @Override
    public PitchTrack detect(AudioClip clip) {
        LogMelSpec mel = LogMelSpec.RMVPE;
        AudioClip in = clip.sampleRate() == mel.sampleRate() ? clip : Resampler.resample(clip, mel.sampleRate());
        float[] x = in.samples();
        float[][] spectrogram = LogMelSpectrogram.compute(x, mel);
        int frames = x.length == 0 ? 0 : spectrogram[0].length;
        float[][] salience = salience(RmvpeModel.session(spec.model()), spectrogram, frames);

        double hopMs = mel.hopMs();
        double offsetMs = in.offsetSec() * 1000.0;
        var out = new ArrayList<PitchEstimate>(frames);
        for (int t = 0; t < frames; t++) {
            long tick = Math.round(offsetMs + t * hopMs);
            double rms = FrameEnergy.rms(x, t * mel.hopLength(), in.sampleRate());
            float peak = peak(salience[t]);
            double level = rms * peak;
            double cents = decode(salience[t], spec.threshold());
            if (Double.isNaN(cents) || rms < spec.minRms()) { out.add(PitchEstimate.silent(tick, level)); continue; }
            double hz = 10.0 * Math.pow(2, cents / 1200.0);
            if (hz < spec.minHz() || hz > spec.maxHz()) { out.add(PitchEstimate.silent(tick, level)); continue; }
            double midi = 69 + 12 * Math.log(hz / 440.0) / Math.log(2);
            out.add(new PitchEstimate(tick, midi, peak, level));
        }
        return new PitchTrack(hopMs, out);
    }

    /** Model salience {@code [frames][360]}, run in overlapping chunks. */
    static float[][] salience(OrtSession session, float[][] mel, int frames) {
        float[][] out = new float[frames][];
        String input = session.getInputNames().iterator().next();
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        for (int start = 0; start < frames; start += CHUNK) {
            int end = Math.min(frames, start + CHUNK);
            int from = Math.max(0, start - CONTEXT), to = Math.min(frames, end + CONTEXT);
            float[] chunk = padToMultiple(mel, from, to, 32);
            int padded = chunk.length / mel.length;
            try (OnnxTensor tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(chunk), new long[]{1, mel.length, padded});
                 OrtSession.Result r = session.run(Map.of(input, tensor))) {
                float[][] rows = ((float[][][]) r.get(0).getValue())[0];
                for (int t = start; t < end; t++) out[t] = rows[t - from];
            } catch (OrtException ex) {
                throw new IllegalStateException("RMVPE inference failed: " + ex.getMessage(), ex);
            }
        }
        return out;
    }

    /**
     * Frames {@code [from, to)} of {@code mel} as a row-major {@code [mels][T']}
     * buffer, {@code T'} the next multiple of {@code m}; the extra frames
     * mirror the last ones (reflect, as RVC pads), or repeat the edge when
     * the chunk is too short to mirror.
     */
    static float[] padToMultiple(float[][] mel, int from, int to, int m) {
        int n = to - from;
        int padded = Math.max(m, ((n + m - 1) / m) * m);
        float[] buf = new float[mel.length * padded];
        for (int b = 0; b < mel.length; b++) {
            float[] row = mel[b];
            int base = b * padded;
            for (int t = 0; t < padded; t++) {
                int k;
                if (t < n) k = t;
                else {
                    k = 2 * (n - 1) - t;                     // reflect without repeating the edge
                    if (k < 0) k = n - 1;
                }
                buf[base + t] = row[from + k];
            }
        }
        return buf;
    }

    /**
     * Cents above 10 Hz for one frame of salience, or {@code NaN} when the
     * peak does not exceed {@code threshold} (unvoiced). RVC's
     * {@code to_local_average_cents}: weighted mean over the peak ± 4 bins.
     */
    static double decode(float[] s, double threshold) {
        int arg = 0;
        for (int i = 1; i < s.length; i++) if (s[i] > s[arg]) arg = i;
        if (!(s[arg] > threshold)) return Double.NaN;
        double num = 0, den = 0;
        for (int i = Math.max(0, arg - 4); i <= Math.min(s.length - 1, arg + 4); i++) {
            num += s[i] * (CENTS_BIN0 + CENTS_PER_BIN * i);
            den += s[i];
        }
        return num / den;
    }

    private static float peak(float[] s) {
        float m = 0;
        for (float v : s) m = Math.max(m, v);
        return m;
    }
}
