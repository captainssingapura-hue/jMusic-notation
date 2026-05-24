package music.notation.input;

import music.notation.performance.PitchedNote;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

/**
 * Reads an audio file end-to-end and emits {@link PitchedNote} events
 * via the same {@link NoteInputListener} contract as
 * {@link VocalMicInputSource}. Useful for offline analysis of
 * pre-recorded singing, instrument lines, voice memos from a phone, or
 * test fixtures — exactly the same YIN + segmenter pipeline, just fed
 * from a file instead of a live mic.
 *
 * <h2>Supported formats</h2>
 *
 * <p>This class loads via {@link AudioFileLoader}, which means:</p>
 *
 * <ul>
 *   <li><b>WAV / AIFF / AU</b> — read natively by the JDK, no external tool needed.</li>
 *   <li><b>.m4a / .mp3 / .ogg / .flac / .aac / .opus / ...</b> — converted
 *       to a temp WAV via the system {@code ffmpeg} binary. The user
 *       must have ffmpeg on their PATH; absence yields a clear error
 *       message at {@link #start} time.</li>
 *   <li>Internally, analysis runs on signed 16/24/32-bit PCM, mono or
 *       stereo (stereo is downmixed before pitch detection). The
 *       ffmpeg path produces 16-bit mono 44.1 kHz output for consistency.</li>
 * </ul>
 *
 * <p>8-bit PCM and IEEE-float WAVs are rejected at validation; the
 * worker logs and exits cleanly.</p>
 *
 * <h2>Lifecycle</h2>
 *
 * <p>{@link #start} returns immediately and spawns a daemon worker that
 * loads + streams the file, runs YIN on each window, and feeds the
 * segmenter. Processing is faster than realtime; for a 60-second file
 * expect completion in well under a second. {@link #stop} signals the
 * worker to exit early. When EOF is reached normally, the segmenter is
 * {@link PitchedNoteSegmenter#flush flushed} so any in-progress note
 * is delivered before the source goes idle.</p>
 *
 * <h2>Tick timing</h2>
 *
 * <p>All emitted {@code tickMs} values are measured from the start of
 * the audio file (sample 0 = tick 0), not from {@link #start} wall-clock
 * time. Notes from imported files always begin at 0 ms — the file's
 * intrinsic timeline.</p>
 */
public final class AudioFileInputSource implements NoteInputSource {

    /** Tuneable parameters; defaults mirror {@link VocalMicInputSource}. */
    public record Config(
            int windowSize,
            int hopSize,
            double yinThreshold,
            double minFreqHz,
            double maxFreqHz,
            PitchedNoteSegmenter.Config segmenter) {

        public static Config defaults() {
            return new Config(
                    /*windowSize=*/ 2048,
                    /*hopSize=*/    1024,
                    /*yinThreshold=*/ YinPitchDetector.DEFAULT_THRESHOLD,
                    /*minFreqHz=*/  50.0,
                    /*maxFreqHz=*/  2000.0,
                    /*segmenter=*/  PitchedNoteSegmenter.Config.defaults());
        }
    }

    private final File file;
    private final Config config;

    private volatile NoteInputListener listener = new NoteInputListener() {
        @Override public void onNoteCompleted(PitchedNote n, int v) {}
    };
    private Thread worker;
    private volatile boolean running;
    /** Lets the recorder UI know when offline analysis finishes (for status updates). */
    private volatile Runnable onFinished = () -> {};

    public AudioFileInputSource(File file) {
        this(file, Config.defaults());
    }

    public AudioFileInputSource(File file, Config config) {
        this.file = Objects.requireNonNull(file, "file");
        this.config = Objects.requireNonNull(config, "config");
    }

    // ── NoteInputSource ───────────────────────────────────────────────

    @Override
    public synchronized void start() {
        if (running) return;
        running = true;
        worker = new Thread(this::analyse, "AudioFileInputSource-worker");
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    public synchronized void stop() {
        if (!running) return;
        running = false;
        if (worker != null) {
            try { worker.join(2000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            worker = null;
        }
    }

    @Override
    public void setListener(NoteInputListener listener) {
        this.listener = (listener == null) ? noopListener() : listener;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public void close() {
        stop();
    }

    /**
     * Set a callback to fire when offline analysis completes (worker
     * exits its read loop naturally — either at EOF or after stop).
     * The callback runs on the worker thread; UI consumers must marshal
     * to their own thread. Useful for the recorder UI to flip its
     * status label from "Importing…" to "Done · N notes" without
     * polling {@link #isRunning}.
     */
    public void setOnFinished(Runnable callback) {
        this.onFinished = (callback == null) ? () -> {} : callback;
    }

    // ── Worker ────────────────────────────────────────────────────────

    private void analyse() {
        try (AudioFileLoader.LoadedAudio loaded = AudioFileLoader.load(file)) {
            AudioInputStream in = loaded.stream();
            AudioFormat fmt = in.getFormat();
            validateFormat(fmt);

            int sampleRate     = (int) fmt.getSampleRate();
            int channels       = fmt.getChannels();
            int bytesPerSample = fmt.getSampleSizeInBits() / 8;
            int frameSize      = fmt.getFrameSize();
            boolean be         = fmt.isBigEndian();

            YinPitchDetector detector = new YinPitchDetector(
                    sampleRate, config.windowSize(),
                    config.yinThreshold(), config.minFreqHz(), config.maxFreqHz());
            PitchedNoteSegmenter segmenter = new PitchedNoteSegmenter(
                    config.segmenter(), new ForwardingListener());

            float[] window = new float[config.windowSize()];
            float[] tail   = new float[config.windowSize() - config.hopSize()];
            byte[]  readBuf = new byte[config.hopSize() * frameSize];

            // Initial fill: read one full window's worth.
            int filled = readFloatFrames(in, window, 0, config.windowSize(),
                    bytesPerSample, channels, frameSize, be);
            if (filled == 0) {
                // Empty file — nothing to do.
                return;
            }
            if (filled < config.windowSize()) {
                // Tiny file; pad with silence so YIN has a full window.
                Arrays.fill(window, filled, config.windowSize(), 0f);
            }

            long samplesProcessed = 0;
            analyseWindow(window, samplesProcessed, sampleRate, detector, segmenter);
            System.arraycopy(window, config.hopSize(), tail, 0, tail.length);

            // Steady state: slide by hopSize, decode + analyse each window.
            while (running) {
                int got = readFloatFrames(in, window, tail.length, config.hopSize(),
                        bytesPerSample, channels, frameSize, be);
                if (got <= 0) break;
                System.arraycopy(tail, 0, window, 0, tail.length);
                samplesProcessed += config.hopSize();
                analyseWindow(window, samplesProcessed, sampleRate, detector, segmenter);
                System.arraycopy(window, config.hopSize(), tail, 0, tail.length);
                if (got < config.hopSize()) break;   // partial read → EOF
            }

            // Flush any held note. Use the next-hop tick as the end so
            // duration reflects analysis-window-position consistently.
            long endTickMs = (samplesProcessed + config.hopSize()) * 1000L / sampleRate;
            segmenter.flush(endTickMs);
        } catch (IOException | IllegalArgumentException e) {
            // AudioFileLoader already wraps codec-decode failures as
            // IOException; surface here with a logged message so the UI
            // status reflects the failure path. UIs can interrogate
            // {@link #isRunning} or the onFinished hook for completion.
            System.err.println("AudioFileInputSource analysis failed: " + e.getMessage());
        } finally {
            running = false;
            try { onFinished.run(); } catch (Exception ignored) {}
        }
    }

    private void analyseWindow(float[] window, long sampleIndex, int sampleRate,
                               YinPitchDetector detector, PitchedNoteSegmenter segmenter) {
        double sumSq = 0.0;
        for (float v : window) sumSq += v * v;
        double rms = Math.sqrt(sumSq / window.length);

        long tickMs = sampleIndex * 1000L / sampleRate;
        YinPitchDetector.Result r = detector.detect(window);
        PitchEstimate est = r.hasPitch()
                ? new PitchEstimate(tickMs, r.midiFloat(), r.confidence(), rms)
                : PitchEstimate.silent(tickMs, rms);
        segmenter.onFrame(est);
    }

    // ── Format validation ────────────────────────────────────────────

    private static void validateFormat(AudioFormat fmt) {
        if (fmt.getEncoding() != AudioFormat.Encoding.PCM_SIGNED) {
            throw new IllegalArgumentException(
                    "only PCM_SIGNED WAV supported, got encoding " + fmt.getEncoding());
        }
        int bits = fmt.getSampleSizeInBits();
        if (bits != 16 && bits != 24 && bits != 32) {
            throw new IllegalArgumentException(
                    "only 16/24/32-bit PCM supported, got " + bits + " bits");
        }
        int ch = fmt.getChannels();
        if (ch < 1 || ch > 2) {
            throw new IllegalArgumentException(
                    "only mono or stereo supported, got " + ch + " channels");
        }
    }

    // ── PCM → float helpers ──────────────────────────────────────────

    /**
     * Read up to {@code frameCount} frames from the WAV stream and
     * decode them into {@code dst} starting at {@code dstOff}, downmixing
     * stereo to mono. Returns the number of frames actually decoded
     * (0 = EOF before any data).
     */
    private static int readFloatFrames(AudioInputStream in, float[] dst, int dstOff,
                                       int frameCount, int bytesPerSample, int channels,
                                       int frameSize, boolean bigEndian) throws IOException {
        int wantBytes = frameCount * frameSize;
        byte[] buf = new byte[wantBytes];
        int gotBytes = 0;
        while (gotBytes < wantBytes) {
            int n = in.read(buf, gotBytes, wantBytes - gotBytes);
            if (n < 0) break;
            gotBytes += n;
        }
        if (gotBytes == 0) return 0;
        int framesGot = gotBytes / frameSize;
        for (int f = 0; f < framesGot; f++) {
            int off = f * frameSize;
            int sample;
            if (channels == 1) {
                sample = readSample(buf, off, bytesPerSample, bigEndian);
            } else {
                int l = readSample(buf, off,                  bytesPerSample, bigEndian);
                int r = readSample(buf, off + bytesPerSample, bytesPerSample, bigEndian);
                sample = (l + r) / 2;
            }
            dst[dstOff + f] = sampleToFloat(sample, bytesPerSample);
        }
        return framesGot;
    }

    /** Decode one signed PCM sample to its int representation (sign-extended). */
    private static int readSample(byte[] buf, int off, int bytesPerSample, boolean bigEndian) {
        return switch (bytesPerSample) {
            case 2 -> readInt16(buf, off, bigEndian);
            case 3 -> readInt24(buf, off, bigEndian);
            case 4 -> readInt32(buf, off, bigEndian);
            default -> throw new IllegalStateException("bytesPerSample = " + bytesPerSample);
        };
    }

    /** Normalise a signed PCM sample of the given width into {@code [-1, 1]}. */
    private static float sampleToFloat(int sample, int bytesPerSample) {
        // Divide by the full-scale value for the width. 16-bit → 32768, etc.
        int fullScale = 1 << (bytesPerSample * 8 - 1);
        return sample / (float) fullScale;
    }

    private static int readInt16(byte[] buf, int off, boolean bigEndian) {
        int lo, hi;
        if (bigEndian) { hi = buf[off] & 0xFF; lo = buf[off + 1] & 0xFF; }
        else            { lo = buf[off] & 0xFF; hi = buf[off + 1] & 0xFF; }
        int v = (hi << 8) | lo;
        return (v >= 0x8000) ? (v - 0x10000) : v;
    }

    private static int readInt24(byte[] buf, int off, boolean bigEndian) {
        int b0, b1, b2;
        if (bigEndian) { b0 = buf[off] & 0xFF; b1 = buf[off + 1] & 0xFF; b2 = buf[off + 2] & 0xFF; }
        else            { b2 = buf[off] & 0xFF; b1 = buf[off + 1] & 0xFF; b0 = buf[off + 2] & 0xFF; }
        int v = (b0 << 16) | (b1 << 8) | b2;
        return (v & 0x800000) != 0 ? (v | 0xFF000000) : v;
    }

    private static int readInt32(byte[] buf, int off, boolean bigEndian) {
        int b0, b1, b2, b3;
        if (bigEndian) {
            b0 = buf[off] & 0xFF; b1 = buf[off + 1] & 0xFF;
            b2 = buf[off + 2] & 0xFF; b3 = buf[off + 3] & 0xFF;
        } else {
            b3 = buf[off] & 0xFF; b2 = buf[off + 1] & 0xFF;
            b1 = buf[off + 2] & 0xFF; b0 = buf[off + 3] & 0xFF;
        }
        return (b0 << 24) | (b1 << 16) | (b2 << 8) | b3;
    }

    private static NoteInputListener noopListener() {
        return new NoteInputListener() {
            @Override public void onNoteCompleted(PitchedNote n, int v) {}
        };
    }

    private final class ForwardingListener implements NoteInputListener {
        @Override public void onPitchEstimate(PitchEstimate e) { listener.onPitchEstimate(e); }
        @Override public void onNoteStart(long tickMs, int midi, int velocity) {
            listener.onNoteStart(tickMs, midi, velocity);
        }
        @Override public void onNoteCompleted(PitchedNote n, int velocity) {
            listener.onNoteCompleted(n, velocity);
        }
    }

    public File   file()   { return file; }
    public Config config() { return config; }
}
