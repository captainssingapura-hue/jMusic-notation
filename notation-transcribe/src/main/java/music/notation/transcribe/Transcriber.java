package music.notation.transcribe;

import music.notation.structure.KeySignature;

import java.nio.file.Path;
import java.util.List;

/**
 * The pipeline: read → cut window → resample to {@link #ANALYSIS_RATE}
 * → pitch track → notes → key → grid. Stateless.
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
        AudioClip clip = Resampler.resample(cut, ANALYSIS_RATE);
        PitchTrack track = PitchDetector.of(request.detector()).detect(clip);
        List<TranscribedNote> notes = new NoteSegmenter(request.segmentation()).segment(track);

        TimingSpec timing = request.timing();
        KeySignature key = timing.autoKey() ? KeyEstimator.estimate(notes) : timing.fixedKey();
        double downbeat = timing.downbeatSec() != null ? timing.downbeatSec()
                : notes.isEmpty() ? cut.offsetSec() : notes.getFirst().startMs() / 1000.0;
        GridQuantizer.Result q = GridQuantizer.quantize(notes, timing, downbeat);

        return new Transcription(request, cut.offsetSec(), cut.offsetSec() + cut.durationSec(), downbeat,
                key, timing.autoKey(), track.voicedFraction(), notes, q.notes(), q.pickupBars(), q.dropped(),
                track);
    }
}
