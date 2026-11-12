package music.notation.transcribe;

/**
 * The spectrogram front end for neural pitch models — see
 * {@link LogMelSpec} for the conventions it reproduces. Pure Java; the
 * pieces ({@link #magnitudes}, {@link #melFilterbank}, {@link #hzToMel})
 * are exposed so each can be checked on its own.
 */
public final class LogMelSpectrogram {

    private LogMelSpectrogram() {}

    /** Log-mel spectrogram as {@code [nMels][frames]}. */
    public static float[][] compute(float[] samples, LogMelSpec spec) {
        double[][] mag = magnitudes(samples, spec);                  // [frames][bins]
        double[][] fb = melFilterbank(spec);                         // [mels][bins]
        int frames = mag.length;
        float[][] out = new float[spec.nMels()][frames];
        for (int m = 0; m < spec.nMels(); m++) {
            double[] w = fb[m];
            for (int t = 0; t < frames; t++) {
                double s = 0;
                double[] col = mag[t];
                for (int b = 0; b < w.length; b++) if (w[b] != 0) s += w[b] * col[b];
                out[m][t] = (float) Math.log(Math.max(s, spec.clampMin()));
            }
        }
        return out;
    }

    /** STFT magnitudes {@code [frames][nFft/2 + 1]}: centred, reflect-padded, periodic Hann. */
    public static double[][] magnitudes(float[] x, LogMelSpec spec) {
        int n = spec.nFft(), pad = n / 2, hop = spec.hopLength();
        double[] padded = reflectPad(x, pad);
        double[] window = hann(n);
        int frames = spec.frameCount(x.length);
        double[][] out = new double[frames][spec.bins()];
        double[] re = new double[n], im = new double[n];
        for (int t = 0; t < frames; t++) {
            int start = t * hop;
            for (int i = 0; i < n; i++) {
                int k = start + i;
                re[i] = k < padded.length ? padded[k] * window[i] : 0;
                im[i] = 0;
            }
            Fft.transform(re, im);
            for (int b = 0; b < spec.bins(); b++) out[t][b] = Math.hypot(re[b], im[b]);
        }
        return out;
    }

    /** Periodic Hann window ({@code torch.hann_window} default). */
    static double[] hann(int n) {
        double[] w = new double[n];
        for (int i = 0; i < n; i++) w[i] = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / n);
        return w;
    }

    /** numpy/torch "reflect": mirror without repeating the edge sample. Falls back to zeros if too short. */
    static double[] reflectPad(float[] x, int pad) {
        double[] p = new double[x.length + 2 * pad];
        for (int i = 0; i < x.length; i++) p[pad + i] = x[i];
        if (x.length > pad) {
            for (int i = 1; i <= pad; i++) {
                p[pad - i] = x[i];
                p[pad + x.length - 1 + i] = x[x.length - 1 - i];
            }
        }
        return p;
    }

    /** HTK mel scale. */
    public static double hzToMel(double hz) { return 2595.0 * Math.log10(1.0 + hz / 700.0); }

    public static double melToHz(double mel) { return 700.0 * (Math.pow(10.0, mel / 2595.0) - 1.0); }

    /**
     * Triangular mel filters {@code [nMels][nFft/2 + 1]}, as
     * {@code librosa.filters.mel(sr, n_fft, n_mels, fmin, fmax, htk=True)}
     * with its default Slaney normalisation: each triangle is scaled by
     * {@code 2 / (upper edge − lower edge)} so it has unit area in Hz.
     */
    public static double[][] melFilterbank(LogMelSpec spec) {
        int bins = spec.bins(), nMels = spec.nMels();
        double[] fftFreqs = new double[bins];
        for (int b = 0; b < bins; b++) fftFreqs[b] = (double) b * spec.sampleRate() / spec.nFft();
        double melMin = hzToMel(spec.fMin()), melMax = hzToMel(spec.fMax());
        double[] melF = new double[nMels + 2];
        for (int i = 0; i < melF.length; i++) melF[i] = melToHz(melMin + (melMax - melMin) * i / (nMels + 1));
        double[][] w = new double[nMels][bins];
        for (int m = 0; m < nMels; m++) {
            double lowerW = melF[m + 1] - melF[m], upperW = melF[m + 2] - melF[m + 1];
            double enorm = 2.0 / (melF[m + 2] - melF[m]);
            for (int b = 0; b < bins; b++) {
                double lower = (fftFreqs[b] - melF[m]) / lowerW;
                double upper = (melF[m + 2] - fftFreqs[b]) / upperW;
                w[m][b] = Math.max(0, Math.min(lower, upper)) * enorm;
            }
        }
        return w;
    }

    /** Centre frequency (Hz) of mel band {@code m}. */
    public static double bandCentreHz(LogMelSpec spec, int m) {
        double melMin = hzToMel(spec.fMin()), melMax = hzToMel(spec.fMax());
        return melToHz(melMin + (melMax - melMin) * (m + 1) / (spec.nMels() + 1));
    }
}
