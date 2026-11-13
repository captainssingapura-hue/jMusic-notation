package music.notation.transcribe;

import music.notation.structure.KeySignature;

import java.nio.file.Path;
import java.util.List;

/**
 * The pipeline: read → cut window → resample to {@link #ANALYSIS_RATE}
 * → pitch track → notes → octave/blip fixes → key → snap to key → grid.
 * Stateless.
 */
public final class Transcriber {

    /** Every detector runs at 16 kHz mono — the rate the neural pitch models expect. */
    public static final int ANALYSIS_RATE = 16_000;

    private Transcriber() {}

    public static Transcription transcribe(TranscriptionRequest request) {
        return transcribe(request, AudioReader.read(Path.of(request.audio())));
    }

    /** Transcribe already-decoded audio (any rate, mono). */
    public static Transcription transcribe(TranscriptionRequest request, AudioClip audio) {
        AudioClip cut = audio.slice(request.window());
        return fromTrack(request, cut, track(request.detector(), cut));
    }

    /**
     * The expensive half: resample {@code cut} and run the detector. Its
     * result depends only on the detector and the audio window, so a
     * caller that re-runs with other segmentation or timing settings can
     * keep it and call {@link #fromTrack}.
     */
    public static PitchTrack track(PitchDetectorSpec detector, AudioClip cut) {
        return PitchDetector.of(detector).detect(Resampler.resample(cut, ANALYSIS_RATE));
    }

    /** The cheap half: notes, key and grid from a pitch track of {@code cut}. */
    public static Transcription fromTrack(TranscriptionRequest request, AudioClip cut, PitchTrack track) {
        List<TranscribedNote> segmented = new NoteSegmenter(request.segmentation()).segment(track);
        var smoother = new NoteSmoother(request.smoothing());
        NoteSmoother.Result shaped = smoother.fixShapes(segmented);

        TimingSpec timing = request.timing();
        KeySignature key = timing.autoKey() ? KeyEstimator.estimate(shaped.notes()) : timing.fixedKey();
        NoteSmoother.Result snapped = smoother.snap(shaped.notes(), key);
        List<TranscribedNote> notes = snapped.notes();
        var edits = new java.util.ArrayList<>(shaped.edits());
        edits.addAll(snapped.edits());
        edits.sort(java.util.Comparator.comparingDouble(SmoothingEdit::startMs));
        double downbeat = timing.downbeatSec() != null ? timing.downbeatSec()
                : notes.isEmpty() ? cut.offsetSec() : notes.getFirst().startMs() / 1000.0;
        GridQuantizer.Result q = GridQuantizer.quantize(notes, timing, downbeat);

        return new Transcription(request, cut.offsetSec(), cut.offsetSec() + cut.durationSec(), downbeat,
                key, timing.autoKey(), track.voicedFraction(), notes, edits, q.notes(), q.pickupBars(), q.dropped(),
                track);
    }
}
