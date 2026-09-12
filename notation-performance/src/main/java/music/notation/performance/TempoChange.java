package music.notation.performance;

import music.notation.duration.Duration;

import java.util.Objects;

/**
 * A single tempo set-point in beats per minute, anchored at a musical
 * position from the start of the score.
 *
 * <p>Wall-clock ms at any later position is computed by walking the
 * {@link TempoTrack} via {@code TimeMapper}, integrating each
 * piecewise-constant segment. Editing tempo means mutating the track;
 * every note's wall-clock playback shifts automatically because
 * nothing is baked.</p>
 */
public record TempoChange(Duration at, int bpm) {
    public TempoChange {
        Objects.requireNonNull(at, "at");
        if (at.compareDuration(Duration.zero()) < 0) {
            throw new IllegalArgumentException("at must be >= 0: " + at);
        }
        if (bpm < 1 || bpm > 999) {
            throw new IllegalArgumentException("bpm must be in [1,999]: " + bpm);
        }
    }
}
