package music.notation.transcribe.bench;

import music.notation.duration.Duration;
import music.notation.structure.TimeSignature;
import music.notation.transcribe.SegmentationSpec;
import music.notation.transcribe.TimeWindow;
import music.notation.transcribe.TimingSpec;
import music.notation.transcribe.TranscriptionRequest;

/**
 * The request parameters the bench exposes as controls. Everything else
 * (detector settings, the less common segmentation fields) is carried
 * over unchanged from a base request, so loading a request file, tweaking
 * a few fields and saving it again loses nothing.
 */
public record BenchSettings(int bpm, Double downbeatSec, TimeSignature timeSignature, String key, Duration grid,
                            double fromSec, double toSec,
                            double minNoteMs, double splitCents, double dipRatio) {

    public static final BenchSettings DEFAULT = from(TranscriptionRequest.of("take.wav", TimingSpec.DEFAULT));

    /** Validates by building the parts; throws {@link IllegalArgumentException} on a bad value. */
    public BenchSettings {
        new TimingSpec(bpm, downbeatSec, timeSignature, key, grid);
        new TimeWindow(fromSec, toSec);
        withSegmentation(SegmentationSpec.DEFAULT, minNoteMs, splitCents, dipRatio);
    }

    public static BenchSettings from(TranscriptionRequest r) {
        TimingSpec t = r.timing();
        SegmentationSpec s = r.segmentation();
        return new BenchSettings(t.bpm(), t.downbeatSec(), t.timeSignature(), t.key(), t.grid(),
                r.window().startSec(), r.window().endSec(), s.minNoteMs(), s.splitCents(), s.dipRatio());
    }

    /** {@code base} with these settings applied and the given audio and title. */
    public TranscriptionRequest apply(TranscriptionRequest base, String audio, String title) {
        return new TranscriptionRequest(audio, new TimeWindow(fromSec, toSec), base.detector(),
                withSegmentation(base.segmentation(), minNoteMs, splitCents, dipRatio),
                new TimingSpec(bpm, downbeatSec, timeSignature, key, grid), title);
    }

    public BenchSettings withDownbeat(Double sec) {
        return new BenchSettings(bpm, sec, timeSignature, key, grid, fromSec, toSec, minNoteMs, splitCents, dipRatio);
    }

    private static SegmentationSpec withSegmentation(SegmentationSpec s, double minNoteMs, double splitCents, double dipRatio) {
        return new SegmentationSpec(s.relativeFloorDb(), s.medianMs(), minNoteMs, splitCents,
                s.bridgeGapMs(), s.glideRate(), s.glideMaxMs(), dipRatio, s.trimFraction());
    }
}
