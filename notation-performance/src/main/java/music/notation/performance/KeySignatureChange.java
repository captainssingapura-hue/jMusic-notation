package music.notation.performance;

import music.notation.duration.Duration;
import music.notation.structure.KeySignature;

import java.util.Objects;

/**
 * One key-signature change anchored at a musical position from the
 * start of the score. Mirrors {@link TempoChange} /
 * {@link TimeSignatureChange} in shape — the piece's piecewise-constant
 * key-signature timeline is built from a list of these.
 */
public record KeySignatureChange(Duration at, KeySignature key) {
    public KeySignatureChange {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(key, "key");
        if (at.compareDuration(Duration.zero()) < 0) {
            throw new IllegalArgumentException("at must be >= 0: " + at);
        }
    }
}
