package music.notation.transcribe.bench;

import music.notation.duration.Duration;
import music.notation.structure.TimeSignature;
import music.notation.transcribe.PitchDetectorSpec;
import music.notation.transcribe.SegmentationSpec;
import music.notation.transcribe.SmoothingSpec;
import music.notation.transcribe.TimeWindow;
import music.notation.transcribe.TimingSpec;
import music.notation.transcribe.TranscriptionRequest;

/**
 * The request parameters the bench exposes as controls. Everything else
 * (detector parameters, the less common segmentation fields) is carried
 * over unchanged from a base request, so loading a request file, tweaking
 * a few fields and saving it again loses nothing.
 */
public record BenchSettings(int bpm, Double downbeatSec, TimeSignature timeSignature, String key, Duration grid,
                            double fromSec, double toSec,
                            double minNoteMs, double splitCents, double dipRatio, String detector,
                            SmoothingSpec smoothing) {

    public static final String YIN = "yin", RMVPE = "rmvpe";

    public static final BenchSettings DEFAULT = from(TranscriptionRequest.of("take.wav", TimingSpec.DEFAULT));

    /** Validates by building the parts; throws {@link IllegalArgumentException} on a bad value. */
    public BenchSettings {
        new TimingSpec(bpm, downbeatSec, timeSignature, key, grid);
        new TimeWindow(fromSec, toSec);
        withSegmentation(SegmentationSpec.DEFAULT, minNoteMs, splitCents, dipRatio);
        if (!YIN.equals(detector) && !RMVPE.equals(detector))
            throw new IllegalArgumentException("detector must be " + YIN + " or " + RMVPE + ": " + detector);
        java.util.Objects.requireNonNull(smoothing, "smoothing");
    }

    public static BenchSettings from(TranscriptionRequest r) {
        TimingSpec t = r.timing();
        SegmentationSpec s = r.segmentation();
        return new BenchSettings(t.bpm(), t.downbeatSec(), t.timeSignature(), t.key(), t.grid(),
                r.window().startSec(), r.window().endSec(), s.minNoteMs(), s.splitCents(), s.dipRatio(),
                r.detector() instanceof PitchDetectorSpec.Rmvpe ? RMVPE : YIN, r.smoothing());
    }

    /**
     * {@code base} with these settings applied and the given audio and title.
     * The detector keeps the base request's parameters when it is the same
     * kind; switching kind starts from that detector's defaults.
     */
    public TranscriptionRequest apply(TranscriptionRequest base, String audio, String title) {
        return new TranscriptionRequest(audio, new TimeWindow(fromSec, toSec), detectorFor(base.detector()),
                withSegmentation(base.segmentation(), minNoteMs, splitCents, dipRatio), smoothing,
                new TimingSpec(bpm, downbeatSec, timeSignature, key, grid), title);
    }

    public BenchSettings withDownbeat(Double sec) {
        return new BenchSettings(bpm, sec, timeSignature, key, grid, fromSec, toSec, minNoteMs, splitCents, dipRatio, detector, smoothing);
    }

    private PitchDetectorSpec detectorFor(PitchDetectorSpec base) {
        return switch (detector) {
            case RMVPE -> base instanceof PitchDetectorSpec.Rmvpe ? base : PitchDetectorSpec.Rmvpe.DEFAULT;
            default -> base instanceof PitchDetectorSpec.Yin ? base : PitchDetectorSpec.Yin.DEFAULT;
        };
    }

    private static SegmentationSpec withSegmentation(SegmentationSpec s, double minNoteMs, double splitCents, double dipRatio) {
        return new SegmentationSpec(s.relativeFloorDb(), s.medianMs(), minNoteMs, splitCents,
                s.bridgeGapMs(), s.glideRate(), s.glideMaxMs(), dipRatio, s.trimFraction());
    }
}
