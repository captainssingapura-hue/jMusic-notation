package music.notation.input;

/**
 * Monophonic pitch detection via the YIN algorithm (de Cheveigné and
 * Kawahara, 2002). Pure Java, no external dependencies.
 *
 * <h2>How YIN works</h2>
 *
 * <ol>
 *   <li><b>Difference function</b> — squared difference of the window
 *       with a shifted copy of itself, for every candidate lag τ. A
 *       perfect periodic signal hits zero at τ = period.</li>
 *   <li><b>Cumulative mean normalisation</b> — divide each difference by
 *       the running average of differences up to that lag. This biases
 *       the search toward the fundamental and away from octave errors.</li>
 *   <li><b>Threshold pick</b> — return the smallest τ whose normalised
 *       difference falls below {@link #threshold} (typically 0.15) and
 *       which is a local minimum. If no τ qualifies, return the
 *       global minimum (with reduced confidence).</li>
 *   <li><b>Parabolic interpolation</b> — fit a parabola through the
 *       three samples around the chosen τ to get a sub-bin estimate.</li>
 *   <li><b>Convert</b> — period = τ_fine; frequency = sampleRate /
 *       period; MIDI = 69 + 12·log₂(freq / 440).</li>
 * </ol>
 *
 * <h2>Usage</h2>
 *
 * <p>Construct once per (sample-rate, window-size) configuration; reuse
 * across windows. {@link #detect} is allocation-free in the hot path
 * (internal buffers are reused). Not thread-safe — give each capture
 * thread its own instance.</p>
 *
 * <h2>Window sizing</h2>
 *
 * <p>YIN can only resolve pitches whose period fits twice within the
 * analysis window. For a 2048-sample window at 44.1 kHz, the lowest
 * detectable frequency is about 43 Hz (≈ F1). For typical vocal range
 * (E2 ≈ 82 Hz to C6 ≈ 1047 Hz), a 2048-sample window is comfortable.</p>
 */
public final class YinPitchDetector {

    /** Recommended YIN absolute threshold; values 0.10–0.20 are typical. */
    public static final double DEFAULT_THRESHOLD = 0.15;

    private final int sampleRate;
    private final int windowSize;
    private final double threshold;
    private final double minFreqHz;
    private final double maxFreqHz;

    /** Cumulative-mean-normalised difference function, length windowSize/2. */
    private final double[] yinBuffer;
    private final int tauMin;
    private final int tauMax;

    public YinPitchDetector(int sampleRate, int windowSize) {
        this(sampleRate, windowSize, DEFAULT_THRESHOLD, 50.0, 2000.0);
    }

    /**
     * @param sampleRate audio sample rate (Hz)
     * @param windowSize analysis window length (samples). Must be even
     *                   and at least 128. 1024–4096 is typical.
     * @param threshold  YIN's absolute threshold. Lower = stricter (more
     *                   confident estimates, more dropouts on noisy
     *                   signals). 0.10–0.20 is the usual range.
     * @param minFreqHz  lowest pitch to consider. Frequencies below
     *                   this map to "no pitch" with zero confidence.
     * @param maxFreqHz  highest pitch to consider.
     */
    public YinPitchDetector(int sampleRate, int windowSize,
                            double threshold, double minFreqHz, double maxFreqHz) {
        if (sampleRate <= 0) throw new IllegalArgumentException("sampleRate must be > 0");
        if (windowSize < 128 || (windowSize & 1) != 0)
            throw new IllegalArgumentException("windowSize must be even and ≥ 128");
        if (minFreqHz <= 0 || maxFreqHz <= minFreqHz)
            throw new IllegalArgumentException("require 0 < minFreqHz < maxFreqHz");
        this.sampleRate = sampleRate;
        this.windowSize = windowSize;
        this.threshold = threshold;
        this.minFreqHz = minFreqHz;
        this.maxFreqHz = maxFreqHz;
        this.yinBuffer = new double[windowSize / 2];
        // Pre-compute the tau range that fits our pitch limits. Smaller
        // tau = higher frequency.
        this.tauMax = (int) Math.min(windowSize / 2 - 1, Math.floor(sampleRate / minFreqHz));
        this.tauMin = (int) Math.max(2, Math.ceil(sampleRate / maxFreqHz));
        if (tauMax <= tauMin) {
            throw new IllegalArgumentException(
                    "window too small for the requested pitch range: "
                            + "windowSize=" + windowSize + ", tauMin=" + tauMin
                            + ", tauMax=" + tauMax);
        }
    }

    /**
     * Analyse one window. Returns the fundamental frequency in Hz, or
     * {@link Double#NaN} when the detector has no confident reading.
     *
     * <p>{@code window} must contain exactly {@link #windowSize}
     * samples in normalised {@code [-1, 1]} range. The window is not
     * modified.</p>
     */
    public Result detect(float[] window) {
        if (window.length != windowSize) {
            throw new IllegalArgumentException(
                    "window length must equal configured windowSize: expected "
                            + windowSize + ", got " + window.length);
        }
        differenceFunction(window);
        cumulativeMeanNormalizedDifference();
        int tau = absoluteThreshold();
        if (tau == -1) {
            return Result.unconfident();
        }
        double betterTau = parabolicInterpolation(tau);
        double freq = sampleRate / betterTau;
        if (freq < minFreqHz || freq > maxFreqHz) {
            return Result.unconfident();
        }
        // Confidence: 1 - (yin value at chosen tau). Closer to 0 dip → more confident.
        double confidence = Math.max(0.0, Math.min(1.0, 1.0 - yinBuffer[tau]));
        return new Result(freq, confidence);
    }

    // ── YIN steps ─────────────────────────────────────────────────────

    private void differenceFunction(float[] x) {
        // d_t(tau) = Σ_{j=0}^{W-1} (x[j] - x[j+tau])²
        // Computed for tau in [0, W/2)
        int W = yinBuffer.length;
        yinBuffer[0] = 1.0;
        for (int tau = 1; tau < W; tau++) {
            double sum = 0.0;
            for (int j = 0; j < W; j++) {
                double delta = x[j] - x[j + tau];
                sum += delta * delta;
            }
            yinBuffer[tau] = sum;
        }
    }

    private void cumulativeMeanNormalizedDifference() {
        // d'(tau) = d(tau) / [(1/tau) Σ_{j=1}^{tau} d(j)]
        // Forces d'(0) = 1, suppresses very-small-tau false dips.
        double runningSum = 0.0;
        yinBuffer[0] = 1.0;
        for (int tau = 1; tau < yinBuffer.length; tau++) {
            runningSum += yinBuffer[tau];
            yinBuffer[tau] *= tau / runningSum;
        }
    }

    private int absoluteThreshold() {
        // Find the smallest tau in [tauMin, tauMax] whose value is below
        // threshold AND is a local minimum. Fall back to the global min
        // in [tauMin, tauMax] if no qualifying tau is found.
        int tau;
        for (tau = tauMin; tau <= tauMax; tau++) {
            if (yinBuffer[tau] < threshold) {
                // Walk forward while we keep going down — find the local minimum.
                while (tau + 1 <= tauMax && yinBuffer[tau + 1] < yinBuffer[tau]) {
                    tau++;
                }
                return tau;
            }
        }
        // No tau crossed threshold → low confidence.
        return -1;
    }

    private double parabolicInterpolation(int tau) {
        // Fit y = a(τ - τ₀)² + b through (tau-1, y-), (tau, y0), (tau+1, y+).
        // Vertex sits at τ₀ = tau + 0.5·(y- - y+) / (y- - 2·y0 + y+).
        double y0 = yinBuffer[tau];
        double yPrev = (tau > 0)                  ? yinBuffer[tau - 1] : y0;
        double yNext = (tau + 1 < yinBuffer.length) ? yinBuffer[tau + 1] : y0;
        double denom = (yPrev + yNext) - 2 * y0;
        if (denom == 0.0) return tau;
        return tau + 0.5 * (yPrev - yNext) / denom;
    }

    // ── Result ────────────────────────────────────────────────────────

    /**
     * @param frequencyHz detected pitch in Hz, or {@link Double#NaN} when
     *                    the detector has no confident reading
     * @param confidence  value in {@code [0, 1]} — higher means a stronger
     *                    periodic signal in the window
     */
    public record Result(double frequencyHz, double confidence) {
        public boolean hasPitch() { return !Double.isNaN(frequencyHz); }
        public double midiFloat() {
            return hasPitch() ? 69.0 + 12.0 * (Math.log(frequencyHz / 440.0) / Math.log(2)) : Double.NaN;
        }
        static Result unconfident() { return new Result(Double.NaN, 0.0); }
    }

    public int sampleRate() { return sampleRate; }
    public int windowSize() { return windowSize; }
}
