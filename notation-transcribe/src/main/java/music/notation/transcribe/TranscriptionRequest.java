package music.notation.transcribe;

import java.nio.file.Path;

/**
 * Everything a transcription run needs. Only {@code audio} is required;
 * every other part falls back to its default when omitted.
 *
 * <pre>{@code
 *   {
 *     "audio": "take-01.wav",
 *     "window": { "startSec": 0, "endSec": 30 },
 *     "detector": { "type": "yin", "windowSize": 1024, "hopMs": 10, "threshold": 0.15,
 *                   "minHz": 65, "maxHz": 1100, "minRms": 0.003 },
 *     "segmentation": { "minNoteMs": 60, "splitCents": 60, ... },
 *     "smoothing": { "snapCents": 30, "octaveMaxMs": 250, "blipMaxMs": 100 },
 *     "timing": { "bpm": 92, "downbeatSec": 1.3, "key": "auto", "grid": "1/16" },
 *     "title": "Take 1"
 *   }
 * }</pre>
 *
 * @param audio        path of the audio file; relative paths are resolved
 *                     against the request file's folder by the CLI
 * @param window       part of the file to analyse; default whole file
 * @param detector     pitch detector; default {@link PitchDetectorSpec.Yin#DEFAULT}
 * @param segmentation note segmentation; default {@link SegmentationSpec#DEFAULT}
 * @param smoothing    pitch clean-up of the notes; default {@link SmoothingSpec#DEFAULT}
 * @param timing       tempo, downbeat, bar and key; default {@link TimingSpec#DEFAULT}
 * @param title        name for the output piece; default the audio file name
 */
public record TranscriptionRequest(String audio, TimeWindow window, PitchDetectorSpec detector,
                                   SegmentationSpec segmentation, SmoothingSpec smoothing, TimingSpec timing,
                                   String title) {

    public TranscriptionRequest {
        if (audio == null || audio.isBlank()) throw new IllegalArgumentException("audio is required");
        if (window == null) window = TimeWindow.WHOLE;
        if (detector == null) detector = PitchDetectorSpec.Yin.DEFAULT;
        if (segmentation == null) segmentation = SegmentationSpec.DEFAULT;
        if (smoothing == null) smoothing = SmoothingSpec.DEFAULT;
        if (timing == null) timing = TimingSpec.DEFAULT;
        if (title == null || title.isBlank()) title = defaultTitle(audio);
    }

    /** Without explicit smoothing (it takes its default). */
    public TranscriptionRequest(String audio, TimeWindow window, PitchDetectorSpec detector,
                                SegmentationSpec segmentation, TimingSpec timing, String title) {
        this(audio, window, detector, segmentation, null, timing, title);
    }

    public static TranscriptionRequest of(String audio, TimingSpec timing) {
        return new TranscriptionRequest(audio, null, null, null, timing, null);
    }

    public TranscriptionRequest withAudio(String newAudio) {
        return new TranscriptionRequest(newAudio, window, detector, segmentation, smoothing, timing, title);
    }

    private static String defaultTitle(String audio) {
        String name = Path.of(audio).getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
