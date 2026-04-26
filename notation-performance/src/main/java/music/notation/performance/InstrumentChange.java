package music.notation.performance;

/**
 * A single program (timbre) set-point on a track, anchored at a millisecond tick.
 */
public record InstrumentChange(long tickMs, int program) {
    public InstrumentChange {
        if (tickMs < 0) throw new IllegalArgumentException("tickMs must be >= 0: " + tickMs);
        if (program < 0 || program > 127) throw new IllegalArgumentException("program must be in [0,127]: " + program);
    }
}
