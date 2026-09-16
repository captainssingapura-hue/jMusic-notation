package music.notation.expressivity;

import music.notation.duration.Duration;

import java.util.Objects;

/**
 * A single channel-volume set-point on a track, anchored at a musical
 * position from the start of the owning track. The {@link #loudness}
 * payload is a {@link Loudness} sum type so authored symbolic marks
 * ({@link Loudness.Named}) survive round-trip while numeric values
 * ({@link Loudness.Raw}) stay continuous. The model carries no MIDI
 * CC #7 byte (a synth-specific 7-bit value).
 *
 * <p>Output mapping at the codec boundary:</p>
 * <ul>
 *   <li>MIDI {@code CC #7 = round(loudness.level() × 127)} clamped to
 *       {@code [0, 127]}. CC #7 = 0 is valid silence.</li>
 * </ul>
 */
public record VolumeChange(Duration at, Loudness loudness) {
    public VolumeChange {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(loudness, "loudness");
        if (at.compareDuration(Duration.zero()) < 0) {
            throw new IllegalArgumentException("at must be >= 0: " + at);
        }
    }

    /** Convenience for {@code new VolumeChange(at, Loudness.of(level))}. */
    public VolumeChange(Duration at, double level) {
        this(at, Loudness.of(level));
    }

    /** Convenience for {@code new VolumeChange(at, Loudness.of(mark))}. */
    public VolumeChange(Duration at, music.notation.event.Dynamic mark) {
        this(at, Loudness.of(mark));
    }

    /** Resolved loudness in {@code [0.0, 1.0]}. */
    public double level() { return loudness.level(); }
}
