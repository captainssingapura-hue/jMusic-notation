package music.notation.performance;

import music.notation.duration.Duration;
import music.notation.structure.TimeSignature;

import java.util.Objects;

/**
 * One time-signature change anchored at a musical position from the
 * start of the score. Mirrors {@link TempoChange} in shape and
 * purpose — the piece's piecewise-constant time-signature timeline
 * is built from a list of these.
 */
public record TimeSignatureChange(Duration at, TimeSignature timeSig) {
    public TimeSignatureChange {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(timeSig, "timeSig");
        if (at.compareDuration(Duration.zero()) < 0) {
            throw new IllegalArgumentException("at must be >= 0: " + at);
        }
    }
}
