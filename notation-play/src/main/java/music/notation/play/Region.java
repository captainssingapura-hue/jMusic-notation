package music.notation.play;

/**
 * A tick range used for partial export. {@code [startTick, endTick)}
 * — start inclusive, end exclusive.
 *
 * <p>Use {@link #full()} for whole-song export.</p>
 */
public record Region(long startTick, long endTick) {

    public Region {
        if (startTick < 0) {
            throw new IllegalArgumentException("startTick must be >= 0: " + startTick);
        }
        if (endTick <= startTick) {
            throw new IllegalArgumentException(
                    "endTick must be > startTick: start=" + startTick + ", end=" + endTick);
        }
    }

    /** Whole-song region. {@code endTick = Long.MAX_VALUE} flags "no upper bound". */
    public static Region full() {
        return new Region(0L, Long.MAX_VALUE);
    }

    /** True iff this is the full-song region. */
    public boolean isFull() {
        return startTick == 0L && endTick == Long.MAX_VALUE;
    }

    /** Length in ticks (clamped against the empty-region case). */
    public long length() {
        return endTick - startTick;
    }
}
