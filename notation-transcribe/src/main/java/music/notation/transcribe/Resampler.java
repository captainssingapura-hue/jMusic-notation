package music.notation.transcribe;

/**
 * Band-limited sample-rate conversion by Kaiser-windowed sinc
 * interpolation.
 *
 * <p>Why not just pick every n-th sample: anything above the new
 * Nyquist frequency (8 kHz at 16 kHz) would fold back down into the
 * vocal range as a false tone. The low-pass here starts rolling off at
 * {@value #ROLLOFF} of the lower Nyquist and is well down (about −80 dB)
 * by the Nyquist itself.</p>
 *
 * <p>The kernel is tabulated once per conversion and linearly
 * interpolated, so the cost is a multiply-add per tap — about 190 taps
 * per output sample for 44.1 kHz → 16 kHz.</p>
 */
public final class Resampler {

    static final double ROLLOFF = 0.9;
    private static final int ZERO_CROSSINGS = 32;
    private static final double KAISER_BETA = 8.6;
    private static final int TABLE_STEPS = 512;           // per input sample

    private Resampler() {}

    public static AudioClip resample(AudioClip in, int targetRate) {
        if (targetRate <= 0) throw new IllegalArgumentException("targetRate must be > 0");
        if (in.sampleRate() == targetRate) return in;
        return new AudioClip(targetRate, resample(in.samples(), in.sampleRate(), targetRate), in.offsetSec());
    }

    public static float[] resample(float[] x, int inRate, int outRate) {
        double ratio = (double) outRate / inRate;
        // Cut-off as a fraction of the input sample rate.
        double fc = 0.5 * Math.min(1.0, ratio) * ROLLOFF;
        int half = (int) Math.ceil(ZERO_CROSSINGS / (2 * fc));     // kernel half-width in input samples
        double[] table = kernelTable(fc, half);

        int outLen = (int) Math.floor((long) x.length * ratio);
        float[] y = new float[outLen];
        double step = (double) inRate / outRate;
        for (int n = 0; n < outLen; n++) {
            double t = n * step;
            int centre = (int) Math.floor(t);
            int k0 = Math.max(0, centre - half + 1);
            int k1 = Math.min(x.length - 1, centre + half);
            double acc = 0;
            for (int k = k0; k <= k1; k++) {
                double d = Math.abs(t - k) * TABLE_STEPS;
                int i = (int) d;
                if (i >= table.length - 1) continue;
                double w = table[i] + (d - i) * (table[i + 1] - table[i]);
                acc += x[k] * w;
            }
            y[n] = (float) acc;
        }
        return y;
    }

    /** h(d) for d = 0 … half input samples, at TABLE_STEPS points per sample. */
    private static double[] kernelTable(double fc, int half) {
        double[] t = new double[half * TABLE_STEPS + 2];
        double i0b = besselI0(KAISER_BETA);
        for (int i = 0; i < t.length; i++) {
            double d = (double) i / TABLE_STEPS;
            double r = d / half;
            if (r > 1) { t[i] = 0; continue; }
            double sinc = d == 0 ? 1.0 : Math.sin(2 * Math.PI * fc * d) / (2 * Math.PI * fc * d);
            double win = besselI0(KAISER_BETA * Math.sqrt(1 - r * r)) / i0b;
            t[i] = 2 * fc * sinc * win;
        }
        return t;
    }

    /** Modified Bessel function of the first kind, order 0 (power series). */
    static double besselI0(double x) {
        double sum = 1, term = 1, q = x * x / 4;
        for (int k = 1; k < 200; k++) {
            term *= q / ((double) k * k);
            sum += term;
            if (term < sum * 1e-17) break;
        }
        return sum;
    }
}
