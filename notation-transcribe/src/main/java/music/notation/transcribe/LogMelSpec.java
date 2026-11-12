package music.notation.transcribe;

/**
 * Parameters of a log-mel spectrogram, chosen to match what a neural
 * pitch model was trained on. A model only behaves as published when its
 * input is computed exactly this way, so the values are fixed per model
 * rather than tuned.
 *
 * <ul>
 *   <li>STFT: periodic Hann window of {@code nFft} samples, centred
 *       frames with reflect padding of {@code nFft/2} (the
 *       {@code torch.stft(center=True)} convention), magnitude
 *       {@code √(re² + im²)}.</li>
 *   <li>Mel filterbank: HTK mel scale, Slaney area normalisation
 *       ({@code librosa.filters.mel(htk=True)}).</li>
 *   <li>Log: {@code ln(max(mel, clampMin))}.</li>
 * </ul>
 */
public record LogMelSpec(int sampleRate, int nFft, int hopLength, int nMels,
                         double fMin, double fMax, double clampMin) {

    /** RMVPE's front end (RVC {@code rmvpe.py}): 16 kHz, 1024/160, 128 mels, 30 Hz – 8 kHz. */
    public static final LogMelSpec RMVPE = new LogMelSpec(16_000, 1024, 160, 128, 30, 8000, 1e-5);

    public LogMelSpec {
        if (sampleRate <= 0) throw new IllegalArgumentException("sampleRate must be > 0");
        if (!Fft.isPowerOfTwo(nFft)) throw new IllegalArgumentException("nFft must be a power of two: " + nFft);
        if (hopLength <= 0) throw new IllegalArgumentException("hopLength must be > 0");
        if (nMels <= 0) throw new IllegalArgumentException("nMels must be > 0");
        if (!(fMin >= 0 && fMax > fMin && fMax <= sampleRate / 2.0))
            throw new IllegalArgumentException("require 0 <= fMin < fMax <= Nyquist: " + fMin + ", " + fMax);
        if (!(clampMin > 0)) throw new IllegalArgumentException("clampMin must be > 0");
    }

    public int bins() { return nFft / 2 + 1; }

    public double hopMs() { return 1000.0 * hopLength / sampleRate; }

    /** Frames produced for {@code samples} input samples with centred framing. */
    public int frameCount(int samples) { return 1 + samples / hopLength; }
}
