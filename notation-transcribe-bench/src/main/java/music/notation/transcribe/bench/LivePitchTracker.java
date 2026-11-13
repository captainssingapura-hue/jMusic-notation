package music.notation.transcribe.bench;

import music.notation.input.PitchEstimate;
import music.notation.input.YinPitchDetector;
import music.notation.transcribe.PitchDetectorSpec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * YIN on audio as it is recorded, for the live monitor. Blocks of samples
 * go in from the capture thread; one {@link PitchEstimate} comes out per
 * hop, stamped with the time of its window's centre from the start of the
 * take — the same time base as the saved file, so live dots line up with
 * the offline transcription afterwards.
 *
 * <p>Runs at the capture rate (no resampling): a 2048-sample window at
 * 44.1 kHz is 46 ms, so a frame is ready about 23 ms after its centre
 * was sung. Frames are kept for the whole take; reads copy only what is
 * asked for. Thread-safe: one writer (capture), any readers (UI).</p>
 */
final class LivePitchTracker {

    static final int WINDOW = 2048;
    static final double HOP_MS = 10;
    private static final double LEVEL_MS = 20;

    private final int rate, hop, levelHalf;
    private final double minRms;
    private final YinPitchDetector yin;
    private final float[] ring = new float[WINDOW];
    private final float[] window = new float[WINDOW];
    private long written;                       // samples received so far
    private long nextFrameEnd = WINDOW;         // sample count at which the next frame is complete

    private final List<PitchEstimate> frames = new ArrayList<>();
    private double peakRms;

    /** YIN settings from {@code spec} (threshold, pitch range, gate); window and hop are fixed for live use. */
    LivePitchTracker(int rate, PitchDetectorSpec.Yin spec) {
        this.rate = rate;
        this.hop = Math.max(1, (int) Math.round(rate * HOP_MS / 1000));
        this.levelHalf = (int) Math.round(rate * LEVEL_MS / 2000);
        this.minRms = spec.minRms();
        double minHz = Math.max(spec.minHz(), 2.0 * rate / WINDOW);
        this.yin = new YinPitchDetector(rate, WINDOW, spec.threshold(), minHz, spec.maxHz());
    }

    /** Capture thread: append samples, analysing every complete hop. */
    void accept(float[] block) {
        accept(block, block.length);
    }

    void accept(float[] block, int n) {
        for (int i = 0; i < n; i++) {
            ring[(int) (written % WINDOW)] = block[i];
            written++;
            if (written == nextFrameEnd) {
                analyse();
                nextFrameEnd += hop;
            }
        }
    }

    private void analyse() {
        int start = (int) (written % WINDOW);                 // oldest sample in the ring
        System.arraycopy(ring, start, window, 0, WINDOW - start);
        System.arraycopy(ring, 0, window, WINDOW - start, start);
        double s = 0;
        int c = WINDOW / 2;
        for (int k = c - levelHalf; k < c + levelHalf; k++) s += window[k] * window[k];
        double rms = Math.sqrt(s / (2.0 * levelHalf));
        long tick = Math.round((written - WINDOW / 2.0) * 1000.0 / rate);
        PitchEstimate f;
        if (rms < minRms) {
            f = PitchEstimate.silent(tick, rms);
        } else {
            YinPitchDetector.Result r = yin.detect(window);
            f = r.hasPitch() ? new PitchEstimate(tick, r.midiFloat(), r.confidence(), rms) : PitchEstimate.silent(tick, rms);
        }
        synchronized (frames) {
            frames.add(f);
            peakRms = Math.max(peakRms, rms);
        }
    }

    /** Frames whose time lies in {@code [fromMs, toMs]}, oldest first. */
    List<PitchEstimate> between(double fromMs, double toMs) {
        synchronized (frames) {
            int a = firstAtOrAfter(fromMs);
            int b = a;
            while (b < frames.size() && frames.get(b).tickMs() <= toMs) b++;
            return new ArrayList<>(frames.subList(a, b));
        }
    }

    private int firstAtOrAfter(double ms) {
        int lo = 0, hi = frames.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (frames.get(mid).tickMs() < ms) lo = mid + 1; else hi = mid;
        }
        return lo;
    }

    /**
     * The pitch being sung now: median of the voiced frames in the last
     * {@code spanMs} (steadies vibrato for a readout), or {@code NaN}.
     */
    double currentMidi(double spanMs) {
        synchronized (frames) {
            if (frames.isEmpty()) return Double.NaN;
            double to = frames.getLast().tickMs();
            double[] v = frames.subList(firstAtOrAfter(to - spanMs), frames.size()).stream()
                    .filter(PitchEstimate::hasPitch).mapToDouble(PitchEstimate::midiFloat).toArray();
            if (v.length == 0 || v.length * HOP_MS < spanMs / 2) return Double.NaN;    // mostly unvoiced
            Arrays.sort(v);
            return v[v.length / 2];
        }
    }

    /** Level of the latest frame (0–1). */
    double level() {
        synchronized (frames) { return frames.isEmpty() ? 0 : frames.getLast().rms(); }
    }

    /** Loudest frame so far, for scaling a level display. */
    double peakRms() {
        synchronized (frames) { return peakRms; }
    }

    /** Everything tracked so far, as a pitch track in take time. */
    music.notation.transcribe.PitchTrack toTrack() {
        synchronized (frames) { return new music.notation.transcribe.PitchTrack(hopMs(), new ArrayList<>(frames)); }
    }

    int size() {
        synchronized (frames) { return frames.size(); }
    }

    double hopMs() { return 1000.0 * hop / rate; }
}
