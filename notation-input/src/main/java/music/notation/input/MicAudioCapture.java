package music.notation.input;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Lightweight wrapper around {@link TargetDataLine} that captures mic
 * audio in fixed-size windows and hands them as {@code float[]} frames
 * (normalised to {@code [-1, 1]}) to a consumer.
 *
 * <h2>Why a separate class</h2>
 *
 * <p>The mic-plumbing is naturally separable from the pitch analysis.
 * Tests for the pitch detector and segmenter run synthetic input
 * without any audio hardware; this class is the only piece that
 * actually opens a sound-card device. That makes the unit tests CI-safe
 * and lets us swap in alternative capture sources (audio files, network
 * streams) without touching the analysis side.</p>
 *
 * <h2>Threading</h2>
 *
 * <p>{@link #start} spawns a daemon worker thread that loops reading
 * bytes from the line and dispatching frames to the consumer. The
 * consumer runs on the worker thread; downstream code must marshal
 * back to its own thread (e.g. JavaFX UI) as needed. {@link #stop}
 * signals the loop and blocks briefly for the thread to exit.</p>
 *
 * <h2>Sliding window with overlap</h2>
 *
 * <p>For pitch detection, a sliding window with overlap gives smoother
 * pitch tracking than non-overlapping frames. Construct with a window
 * size W and a hop size H; the consumer receives a new W-sample window
 * every H samples. Typical values: W=2048, H=1024 (50% overlap) at
 * 44.1 kHz → ~43 windows per second, each spanning ~46 ms.</p>
 */
public final class MicAudioCapture implements AutoCloseable {

    private final int sampleRate;
    private final int windowSize;
    private final int hopSize;
    private final Mixer.Info deviceInfo;     // null = system default
    private final Consumer<float[]> windowConsumer;

    private TargetDataLine line;
    private Thread worker;
    private volatile boolean running;

    /**
     * Create a capture using the system default mic at 44.1 kHz with
     * 2048-sample windows hopping 1024 samples (50% overlap).
     *
     * @param windowConsumer invoked on the worker thread with each new
     *                       full window of samples ({@code length == windowSize})
     */
    public MicAudioCapture(Consumer<float[]> windowConsumer) {
        this(44100, 2048, 1024, null, windowConsumer);
    }

    /**
     * @param sampleRate     audio sample rate in Hz (e.g. 44100, 48000)
     * @param windowSize     samples per analysis window (e.g. 2048)
     * @param hopSize        samples between consecutive window starts.
     *                       Equal to windowSize = no overlap; half of
     *                       windowSize = 50% overlap.
     * @param deviceInfo     specific mixer to open (or {@code null} for
     *                       the system default capture device). Discover
     *                       via {@link #listInputDevices()}.
     * @param windowConsumer invoked on the worker thread with each full
     *                       window of samples
     */
    public MicAudioCapture(int sampleRate, int windowSize, int hopSize,
                           Mixer.Info deviceInfo, Consumer<float[]> windowConsumer) {
        if (sampleRate <= 0) throw new IllegalArgumentException("sampleRate must be > 0");
        if (windowSize <= 0) throw new IllegalArgumentException("windowSize must be > 0");
        if (hopSize <= 0 || hopSize > windowSize)
            throw new IllegalArgumentException("require 0 < hopSize <= windowSize");
        if (windowConsumer == null) throw new IllegalArgumentException("windowConsumer required");
        this.sampleRate = sampleRate;
        this.windowSize = windowSize;
        this.hopSize = hopSize;
        this.deviceInfo = deviceInfo;
        this.windowConsumer = windowConsumer;
    }

    // ── Public API ────────────────────────────────────────────────────

    /** Open the device and start the capture loop. */
    public void start() throws LineUnavailableException {
        if (running) return;
        AudioFormat fmt = new AudioFormat(
                /*sampleRate=*/ (float) sampleRate,
                /*sampleSizeInBits=*/ 16,
                /*channels=*/ 1,
                /*signed=*/ true,
                /*bigEndian=*/ false);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, fmt);
        if (deviceInfo != null) {
            line = (TargetDataLine) AudioSystem.getMixer(deviceInfo).getLine(info);
        } else {
            line = (TargetDataLine) AudioSystem.getLine(info);
        }
        line.open(fmt, /*internalBuffer=*/ Math.max(windowSize * 4, hopSize * 8));
        line.start();
        running = true;
        worker = new Thread(this::captureLoop, "MicAudioCapture-worker");
        worker.setDaemon(true);
        worker.start();
    }

    /** Stop the capture loop and release the device. */
    public void stop() {
        running = false;
        if (worker != null) {
            try { worker.join(500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            worker = null;
        }
        if (line != null) {
            try { line.stop(); } catch (Exception ignored) {}
            try { line.close(); } catch (Exception ignored) {}
            line = null;
        }
    }

    @Override
    public void close() {
        stop();
    }

    public boolean isRunning() { return running; }
    public int sampleRate()    { return sampleRate; }
    public int windowSize()    { return windowSize; }
    public int hopSize()       { return hopSize; }

    // ── Worker loop ───────────────────────────────────────────────────

    private void captureLoop() {
        int bytesPerSample = 2;   // 16-bit
        int hopBytes = hopSize * bytesPerSample;
        byte[]  readBuf = new byte[hopBytes];
        float[] window  = new float[windowSize];
        float[] tail    = new float[windowSize - hopSize];   // residue from previous window

        // Initial fill: read enough to fill the first full window.
        int filled = 0;
        while (running && filled < windowSize) {
            int want = (windowSize - filled) * bytesPerSample;
            int got = line.read(readBuf, 0, Math.min(want, hopBytes));
            if (got <= 0) continue;
            int samples = got / bytesPerSample;
            decodeInt16ToFloat(readBuf, 0, samples, window, filled);
            filled += samples;
        }
        if (!running) return;
        windowConsumer.accept(copyOf(window));

        // Steady-state: stash the tail, read one hop, slide, dispatch.
        System.arraycopy(window, hopSize, tail, 0, tail.length);
        while (running) {
            int got = line.read(readBuf, 0, hopBytes);
            if (got <= 0) continue;
            if (got < hopBytes) {
                // Partial read — pad rest with zeros to keep window aligned.
                java.util.Arrays.fill(readBuf, got, hopBytes, (byte) 0);
            }
            // Slide window: [tail | new hop].
            System.arraycopy(tail, 0, window, 0, tail.length);
            decodeInt16ToFloat(readBuf, 0, hopSize, window, tail.length);
            windowConsumer.accept(copyOf(window));
            // Update tail for next round.
            System.arraycopy(window, hopSize, tail, 0, tail.length);
        }
    }

    private static float[] copyOf(float[] src) {
        float[] dst = new float[src.length];
        System.arraycopy(src, 0, dst, 0, src.length);
        return dst;
    }

    /**
     * Decode {@code samples} 16-bit little-endian signed PCM samples
     * from {@code src} (starting at byte 0) into normalised
     * {@code [-1, 1]} floats in {@code dst} starting at {@code dstOff}.
     */
    static void decodeInt16ToFloat(byte[] src, int srcByteOff, int samples,
                                   float[] dst, int dstOff) {
        for (int i = 0; i < samples; i++) {
            int lo = src[srcByteOff + i * 2]     & 0xFF;
            int hi = src[srcByteOff + i * 2 + 1] & 0xFF;
            int v = (hi << 8) | lo;
            if (v >= 0x8000) v -= 0x10000;
            dst[dstOff + i] = v / 32768.0f;
        }
    }

    // ── Device discovery ──────────────────────────────────────────────

    /**
     * Enumerate all mixers that can supply a 16-bit mono input line.
     * Returns names in user-facing form; pass the corresponding
     * {@link Mixer.Info} (obtained via {@link AudioSystem#getMixerInfo}
     * and filtering by name) to the constructor.
     */
    public static List<Mixer.Info> listInputDevices() {
        List<Mixer.Info> result = new ArrayList<>();
        for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
            Mixer mixer = AudioSystem.getMixer(mi);
            // Only count mixers that expose at least one TargetDataLine —
            // capture sources, not render sinks.
            if (mixer.getTargetLineInfo().length > 0) {
                result.add(mi);
            }
        }
        return result;
    }
}
