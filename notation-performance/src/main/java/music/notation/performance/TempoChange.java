package music.notation.performance;

/**
 * A single tempo set-point in beats per minute, anchored at a millisecond tick.
 */
public record TempoChange(long tickMs, int bpm) {
    public TempoChange {
        if (tickMs < 0) throw new IllegalArgumentException("tickMs must be >= 0: " + tickMs);
        if (bpm < 1 || bpm > 999) throw new IllegalArgumentException("bpm must be in [1,999]: " + bpm);
    }
}
