package music.notation.transcribe;

import music.notation.input.PitchEstimate;
import music.notation.input.YinPitchDetector;

import java.util.ArrayList;
import java.util.Objects;

/**
 * Runs {@link YinPitchDetector} over a whole clip with centred frames:
 * frame {@code i}'s window is centred on sample {@code i × hop}, zero
 * padded at the edges — the same framing as the spectrogram front end,
 * so YIN and a neural model produce frames at the same times. Loudness
 * comes from {@link FrameEnergy}'s shorter window, not the YIN window.
 */
public final class YinTrackDetector implements PitchDetector {

    private final PitchDetectorSpec.Yin spec;

    public YinTrackDetector(PitchDetectorSpec.Yin spec) {
        this.spec = Objects.requireNonNull(spec, "spec");
    }

    @Override
    public PitchTrack detect(AudioClip clip) {
        int rate = clip.sampleRate();
        int w = spec.windowSize();
        int hop = Math.max(1, (int) Math.round(spec.hopMs() * rate / 1000.0));
        double hopMs = 1000.0 * hop / rate;
        var yin = new YinPitchDetector(rate, w, spec.threshold(), spec.minHz(), spec.maxHz());
        float[] x = clip.samples();
        int frames = x.length == 0 ? 0 : 1 + x.length / hop;
        float[] window = new float[w];
        var out = new ArrayList<PitchEstimate>(frames);
        double offsetMs = clip.offsetSec() * 1000.0;
        for (int i = 0; i < frames; i++) {
            int start = i * hop - w / 2;
            for (int k = 0; k < w; k++) {
                int idx = start + k;
                window[k] = idx >= 0 && idx < x.length ? x[idx] : 0f;
            }
            double rms = FrameEnergy.rms(x, i * hop, rate);
            long tick = Math.round(offsetMs + i * hopMs);
            if (rms < spec.minRms()) { out.add(PitchEstimate.silent(tick, rms)); continue; }
            YinPitchDetector.Result r = yin.detect(window);
            out.add(r.hasPitch() ? new PitchEstimate(tick, r.midiFloat(), r.confidence(), rms)
                                 : PitchEstimate.silent(tick, rms));
        }
        return new PitchTrack(hopMs, out);
    }
}
