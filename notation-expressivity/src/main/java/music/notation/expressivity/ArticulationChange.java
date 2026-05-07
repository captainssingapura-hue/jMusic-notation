package music.notation.expressivity;

import java.util.Objects;

/**
 * A single articulation set-point on a track, anchored at a millisecond tick.
 */
public record ArticulationChange(long tickMs, Articulation kind) {
    public ArticulationChange {
        if (tickMs < 0) throw new IllegalArgumentException("tickMs must be >= 0: " + tickMs);
        Objects.requireNonNull(kind, "kind");
    }
}
