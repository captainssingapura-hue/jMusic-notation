package music.notation.input;

import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;

/**
 * Live vocal pitch capture: microphone → YIN pitch detection → note
 * segmentation → {@link NoteInputListener} events.
 *
 * <h2>Wiring</h2>
 *
 * <pre>{@code
 *   MicAudioCapture        ──windows of float[]─▶  YinPitchDetector
 *                                                         │
 *                                                         ▼
 *                                                  PitchEstimate
 *                                                         │
 *                                                         ▼
 *                                              PitchedNoteSegmenter
 *                                                         │
 *                                                         ▼
 *                                              NoteInputListener
 *                                          (onPitchEstimate / onNoteStart /
 *                                              onNoteCompleted)
 * }</pre>
 *
 * <h2>Timing</h2>
 *
 * <p>The {@code tickMs} on every emitted event is measured from
 * {@link #start()} returning. The first window arrives roughly
 * {@code windowSize / sampleRate * 1000} ms after start (one full
 * window must accumulate before the first analysis); subsequent
 * windows arrive every {@code hopSize / sampleRate * 1000} ms.</p>
 *
 * <h2>Default configuration</h2>
 *
 * <ul>
 *   <li>System default mic, 44.1 kHz mono.</li>
 *   <li>2048-sample window, 1024-sample hop (50% overlap, ~46 ms/window).</li>
 *   <li>YIN threshold 0.15, vocal range 50–2000 Hz.</li>
 *   <li>Segmenter: ≥2 stable frames (~100 ms) to declare a note;
 *       ±50 cents pitch tolerance; ≥2 silent frames to end a note.</li>
 * </ul>
 *
 * <p>All of these are tuneable via the {@link Config} record.</p>
 */
public final class VocalMicInputSource implements NoteInputSource {

    /**
     * Tuneable configuration. Most users want {@link #defaults()}.
     */
    public record Config(
            int sampleRate,
            int windowSize,
            int hopSize,
            Mixer.Info device,                     // null = system default
            double yinThreshold,
            double minFreqHz,
            double maxFreqHz,
            PitchedNoteSegmenter.Config segmenter) {

        public static Config defaults() {
            return new Config(
                    /*sampleRate=*/ 44100,
                    /*windowSize=*/ 2048,
                    /*hopSize=*/    1024,
                    /*device=*/     null,
                    /*yinThreshold=*/ YinPitchDetector.DEFAULT_THRESHOLD,
                    /*minFreqHz=*/  50.0,
                    /*maxFreqHz=*/  2000.0,
                    /*segmenter=*/  PitchedNoteSegmenter.Config.defaults());
        }
    }

    private final Config config;
    private final YinPitchDetector detector;
    private MicAudioCapture capture;
    private PitchedNoteSegmenter segmenter;
    private volatile NoteInputListener listener = new NoteInputListener() {
        @Override public void onNoteCompleted(music.notation.performance.PitchedNote n, int v) {}
    };

    private volatile long sessionStartNanos;
    private long samplesProcessed;   // count of windows × hopSize since start
    private volatile boolean running;

    public VocalMicInputSource() {
        this(Config.defaults());
    }

    public VocalMicInputSource(Config config) {
        this.config = config;
        this.detector = new YinPitchDetector(
                config.sampleRate(), config.windowSize(),
                config.yinThreshold(), config.minFreqHz(), config.maxFreqHz());
    }

    // ── NoteInputSource ───────────────────────────────────────────────

    @Override
    public synchronized void start() throws LineUnavailableException {
        if (running) return;
        // Re-create the segmenter on each start so a new session begins
        // with a clean state machine (prevents stale "held note" from a
        // previous session).
        segmenter = new PitchedNoteSegmenter(config.segmenter(), new ForwardingListener());
        samplesProcessed = 0;
        sessionStartNanos = System.nanoTime();

        capture = new MicAudioCapture(
                config.sampleRate(), config.windowSize(), config.hopSize(),
                config.device(), this::onWindow);
        capture.start();
        running = true;
    }

    @Override
    public synchronized void stop() {
        if (!running) return;
        running = false;
        if (capture != null) {
            capture.stop();
            capture = null;
        }
        if (segmenter != null) {
            // Flush any held note with the current timestamp.
            segmenter.flush(currentSessionTickMs());
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

    public Config config() { return config; }

    // ── Internals ─────────────────────────────────────────────────────

    private void onWindow(float[] window) {
        // Compute RMS in the same window (for energy gating + velocity).
        double sumSq = 0.0;
        for (float v : window) sumSq += v * v;
        double rms = Math.sqrt(sumSq / window.length);

        // Run YIN.
        YinPitchDetector.Result r = detector.detect(window);

        // The window represents audio centred at samplesProcessed + hopSize/2,
        // but for simplicity we tag the estimate with the start of the
        // current hop. The drift is sub-window-size and consistent across
        // all events, so segmenter timings stay coherent.
        long tickMs = currentSessionTickMs();

        PitchEstimate est = r.hasPitch()
                ? new PitchEstimate(tickMs, r.midiFloat(), r.confidence(), rms)
                : PitchEstimate.silent(tickMs, rms);

        segmenter.onFrame(est);
        samplesProcessed += config.hopSize();
    }

    private long currentSessionTickMs() {
        // Use sample-count clock: deterministic, jitter-free, doesn't
        // drift if the capture thread is briefly preempted. Falls back
        // to wall-clock only before any samples have been processed.
        if (samplesProcessed == 0) {
            return Math.max(0, (System.nanoTime() - sessionStartNanos) / 1_000_000);
        }
        return samplesProcessed * 1000L / config.sampleRate();
    }

    private static NoteInputListener noopListener() {
        return new NoteInputListener() {
            @Override public void onNoteCompleted(music.notation.performance.PitchedNote n, int v) {}
        };
    }

    /** Bounces segmenter events out to the current external listener. */
    private final class ForwardingListener implements NoteInputListener {
        @Override public void onPitchEstimate(PitchEstimate e) { listener.onPitchEstimate(e); }
        @Override public void onNoteStart(long tickMs, int midi, int velocity) {
            listener.onNoteStart(tickMs, midi, velocity);
        }
        @Override public void onNoteCompleted(music.notation.performance.PitchedNote n, int velocity) {
            listener.onNoteCompleted(n, velocity);
        }
    }
}
