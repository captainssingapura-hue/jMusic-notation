package music.notation.performance;

import music.notation.duration.Duration;

import java.util.Objects;

/**
 * A single program (timbre) set-point on a track, anchored at a
 * musical position from the start of the owning track.
 */
public record InstrumentChange(Duration at, int program) {
    public InstrumentChange {
        Objects.requireNonNull(at, "at");
        if (at.compareDuration(Duration.zero()) < 0) {
            throw new IllegalArgumentException("at must be >= 0: " + at);
        }
        if (program < 0 || program > 127) {
            throw new IllegalArgumentException("program must be in [0,127]: " + program);
        }
    }
}
