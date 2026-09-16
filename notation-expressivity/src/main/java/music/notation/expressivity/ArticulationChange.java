package music.notation.expressivity;

import music.notation.duration.Duration;

import java.util.Objects;

/**
 * A single articulation set-point on a track, anchored at a musical
 * position from the start of the owning track.
 */
public record ArticulationChange(Duration at, Articulation kind) {
    public ArticulationChange {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(kind, "kind");
        if (at.compareDuration(Duration.zero()) < 0) {
            throw new IllegalArgumentException("at must be >= 0: " + at);
        }
    }
}
