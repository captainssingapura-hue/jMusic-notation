package music.notation.performance;

import music.notation.duration.Duration;

import java.util.Objects;

/**
 * A percussion hit; {@code piece} selects a drum from the General MIDI
 * percussion map (see {@link Drums}). Drum tracks render to MIDI
 * channel 9; the kit is selected by {@link Instrumentation} on the
 * owning track.
 *
 * <p>Position and length use {@link Duration} (musical, rational) —
 * no wall-clock milliseconds.</p>
 */
public record DrumNote(Duration at, Duration duration, int piece) implements ConcreteNote {
    public DrumNote {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(duration, "duration");
        if (at.compareDuration(Duration.zero()) < 0) {
            throw new IllegalArgumentException("at must be >= 0: " + at);
        }
        if (duration.compareDuration(Duration.zero()) <= 0) {
            throw new IllegalArgumentException("duration must be > 0: " + duration);
        }
        if (piece < 0 || piece > 127) {
            throw new IllegalArgumentException("piece must be in [0,127]: " + piece);
        }
    }
}
