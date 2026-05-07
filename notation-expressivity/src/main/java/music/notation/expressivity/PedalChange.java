package music.notation.expressivity;

import java.util.Objects;

/**
 * A single sustain-pedal state change anchored at a millisecond tick.
 * The codec emits this as a MIDI CC #64 event on the owning track's
 * channel.
 */
public record PedalChange(long tickMs, PedalState state) {
    public PedalChange {
        if (tickMs < 0) throw new IllegalArgumentException("tickMs must be >= 0: " + tickMs);
        Objects.requireNonNull(state, "state");
    }
}
