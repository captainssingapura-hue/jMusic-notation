package music.notation.performance;

/**
 * A single volume set-point on a track, anchored at a millisecond tick.
 *
 * <p>{@code level} is in MIDI CC #7 range, 0–127, where 0 is silence and
 * 127 is full. Values outside the range throw at construction.</p>
 */
public record VolumeChange(long tickMs, int level) {
    public VolumeChange {
        if (tickMs < 0) throw new IllegalArgumentException("tickMs must be >= 0: " + tickMs);
        if (level < 0 || level > 127) throw new IllegalArgumentException("level must be in [0,127]: " + level);
    }
}
