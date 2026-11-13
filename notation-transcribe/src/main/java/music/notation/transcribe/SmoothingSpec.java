package music.notation.transcribe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Pitch clean-up applied to segmented notes by {@link NoteSmoother}.
 * Each pass is off when its threshold is 0.
 *
 * <pre>{@code  "smoothing": { "snapCents": 30, "octaveMaxMs": 250, "blipMaxMs": 100, "neighbourGapMs": 60 } }</pre>
 *
 * @param snapCents      an out-of-key note sung at least this far towards
 *                       an in-key neighbour semitone is moved onto it
 *                       (0 – 50; 0 = never snap)
 * @param octaveMaxMs    a note no longer than this, an octave away from
 *                       both neighbours, is folded back (0 = off)
 * @param blipMaxMs      a note no longer than this and less than half as
 *                       long as both neighbours is absorbed when it is a
 *                       glide fragment or a flick off a held pitch (0 = off)
 * @param neighbourGapMs notes closer than this count as neighbours for the
 *                       octave and blip passes
 */
public record SmoothingSpec(double snapCents, double octaveMaxMs, double blipMaxMs, double neighbourGapMs) {

    public static final SmoothingSpec DEFAULT = new SmoothingSpec(30, 250, 100, 60);
    public static final SmoothingSpec OFF = new SmoothingSpec(0, 0, 0, DEFAULT.neighbourGapMs());

    public SmoothingSpec {
        if (!(snapCents >= 0 && snapCents < 50)) throw new IllegalArgumentException("snapCents must be in [0, 50): " + snapCents);
        if (!(octaveMaxMs >= 0)) throw new IllegalArgumentException("octaveMaxMs must be >= 0");
        if (!(blipMaxMs >= 0)) throw new IllegalArgumentException("blipMaxMs must be >= 0");
        if (!(neighbourGapMs >= 0)) throw new IllegalArgumentException("neighbourGapMs must be >= 0");
    }

    /** JSON form: any field left out takes its {@link #DEFAULT} value. */
    @JsonCreator
    static SmoothingSpec fromJson(@JsonProperty("snapCents") Double snapCents,
                                  @JsonProperty("octaveMaxMs") Double octaveMaxMs,
                                  @JsonProperty("blipMaxMs") Double blipMaxMs,
                                  @JsonProperty("neighbourGapMs") Double neighbourGapMs) {
        return new SmoothingSpec(
                snapCents != null ? snapCents : DEFAULT.snapCents(),
                octaveMaxMs != null ? octaveMaxMs : DEFAULT.octaveMaxMs(),
                blipMaxMs != null ? blipMaxMs : DEFAULT.blipMaxMs(),
                neighbourGapMs != null ? neighbourGapMs : DEFAULT.neighbourGapMs());
    }
}
