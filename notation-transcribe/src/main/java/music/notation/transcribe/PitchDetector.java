package music.notation.transcribe;

/**
 * Turns mono audio at the analysis rate into a {@link PitchTrack}.
 * Implementations are offline: they see the whole clip.
 */
public interface PitchDetector {

    PitchTrack detect(AudioClip clip);

    static PitchDetector of(PitchDetectorSpec spec) {
        return switch (spec) {
            case PitchDetectorSpec.Yin y -> new YinTrackDetector(y);
        };
    }
}
