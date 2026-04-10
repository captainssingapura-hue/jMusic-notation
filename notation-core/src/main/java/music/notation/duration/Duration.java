package music.notation.duration;

/**
 * A musical duration — sealed ADT.
 *
 * <p>The common durations are the {@link BaseValue} enum constants themselves
 * (WHOLE, HALF, QUARTER, …), which implement this interface directly.
 * Dotted and raw (tied) durations are separate variants.</p>
 *
 * <p>Usage:
 * <pre>{@code
 * Duration d1 = QUARTER;           // plain quarter note
 * Duration d2 = QUARTER.dot();     // dotted quarter
 * Duration d3 = Duration.ofSixtyFourths(48); // raw (tied)
 * }</pre>
 */
public sealed interface Duration permits BaseValue, DottedDuration, RawDuration {

    /** Duration measured in 64th-note units. */
    int sixtyFourths();

    /** Return a dotted version of this duration. */
    Duration dot();

    // ── Backward-compatible factories ──

    /** Plain duration from a base value. */
    static Duration of(BaseValue base) {
        return base;
    }

    /** Dotted duration from a base value. */
    static Duration dotted(BaseValue base) {
        return base.dot();
    }

    /** Raw duration from a sixty-fourths count (used for tied/merged notes). */
    static Duration ofSixtyFourths(int sixtyFourths) {
        return new RawDuration(sixtyFourths);
    }
}
