package music.notation.expressivity;

import music.notation.duration.Duration;

import java.util.Objects;

/**
 * A single sustain-pedal state change anchored at a musical position
 * from the start of the owning track. The codec emits this as a MIDI
 * CC #64 event by converting the musical position to ticks via PPQ +
 * TempoTrack at emit time.
 */
public record PedalChange(Duration at, PedalState state) {
    public PedalChange {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(state, "state");
        if (at.compareDuration(Duration.zero()) < 0) {
            throw new IllegalArgumentException("at must be >= 0: " + at);
        }
    }
}
