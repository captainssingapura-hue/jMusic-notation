package music.notation.duration;

import java.util.Objects;

/**
 * The logical duration of a bar, expressed in its natural musical
 * unit — <em>N copies of a {@link BaseValue}</em>. Mirrors the meaning
 * of a time signature's "beats × beat-value" without reaching for an
 * integer sixty-fourths count.
 *
 * <h3>Examples</h3>
 * <ul>
 *   <li>{@code new BarDuration(4, QUARTER)}  — 4/4 (a whole-note worth)</li>
 *   <li>{@code new BarDuration(3, QUARTER)}  — 3/4 (waltz)</li>
 *   <li>{@code new BarDuration(6, EIGHTH)}   — 6/8 (compound duple)</li>
 *   <li>{@code new BarDuration(3, EIGHTH)}   — 3/8</li>
 *   <li>{@code new BarDuration(5, QUARTER)}  — 5/4 (irregular)</li>
 *   <li>{@code new BarDuration(12, EIGHTH)}  — 12/8</li>
 *   <li>{@code new BarDuration(2, HALF)}     — 2/2 (cut time)</li>
 * </ul>
 *
 * <p>Note that 6/8 and 3/4 produce the same {@link #totalDuration}
 * (a dotted half, 48 sf) but are <em>different</em> {@code BarDuration}
 * values — the (count, unit) pair preserves the meter's character.
 * Useful later for stress patterns, beaming, and score rendering.</p>
 *
 * <h3>Author-side use</h3>
 * <p>Pass to {@code Bar.of(BarDuration, …)} as the bar's expected size
 * (replaces the older {@code Bar.of(int sixtyFourths, …)} idiom; see
 * {@code .docs/duration-abstraction-plan.md}).</p>
 */
public record BarDuration(int unitCount, BaseValue unit) {

    public BarDuration {
        if (unitCount <= 0) {
            throw new IllegalArgumentException(
                    "unitCount must be > 0, got " + unitCount);
        }
        Objects.requireNonNull(unit, "unit");
    }

    /**
     * Build from time-signature numbers
     * (e.g. {@code fromTimeSignature(4, 4)} → {@code BarDuration(4, QUARTER)}).
     *
     * @throws IllegalArgumentException if {@code beatValue} is not a
     *         power of two from 1 (whole) through 64 (sixty-fourth).
     */
    public static BarDuration fromTimeSignature(int beats, int beatValue) {
        return new BarDuration(beats, baseValueFor(beatValue));
    }

    /**
     * Best-effort reverse math: given a raw sixty-fourths count
     * (e.g. {@code 64} from a legacy {@code Bar.of(int, …)} call),
     * pick the most natural {@code (count, unit)} pair.
     *
     * <p>Preference order: the largest {@link BaseValue} unit that
     * divides the count cleanly, biased toward common time signatures —
     * QUARTER beat (4/4, 3/4, 2/4, 5/4, 7/4, …) before EIGHTH beat
     * (3/8, 6/8, 9/8, 12/8) before smaller subdivisions.</p>
     *
     * <p>Examples (assuming current 64-base):</p>
     * <pre>
     *   64 sf  → BarDuration(4,  QUARTER)        // 4/4
     *   48 sf  → BarDuration(3,  QUARTER)        // 3/4 (could also be 6/8)
     *   32 sf  → BarDuration(2,  QUARTER)        // 2/4 (could also be 1/2 cut time)
     *   24 sf  → BarDuration(3,  EIGHTH)         // 3/8
     *   80 sf  → BarDuration(5,  QUARTER)        // 5/4
     *   96 sf  → BarDuration(6,  QUARTER)        // 6/4 (could also be 12/8)
     *   12 sf  → BarDuration(3,  SIXTEENTH)      // 3/16
     *    7 sf  → BarDuration(7,  SIXTY_FOURTH)   // pathological fallback
     * </pre>
     *
     * <p>For meters where two readings are valid, the chosen one is
     * arbitrary metadata — the {@link #sixtyFourths()} total is the
     * same. Authors who care about meter character should construct
     * {@code BarDuration} explicitly via the
     * {@link BarDuration#BarDuration(int, BaseValue)} canonical
     * constructor or {@link #fromTimeSignature}.</p>
     *
     * @throws IllegalArgumentException if {@code sf <= 0}.
     */
    public static BarDuration fromSixtyFourths(int sf) {
        if (sf <= 0) {
            throw new IllegalArgumentException(
                    "sf must be > 0, got " + sf);
        }
        if (sf %  16 == 0) return new BarDuration(sf / 16, BaseValue.QUARTER);
        if (sf %   8 == 0) return new BarDuration(sf /  8, BaseValue.EIGHTH);
        if (sf %   4 == 0) return new BarDuration(sf /  4, BaseValue.SIXTEENTH);
        if (sf %   2 == 0) return new BarDuration(sf /  2, BaseValue.THIRTY_SECOND);
        return new BarDuration(sf, BaseValue.SIXTY_FOURTH);
    }

    /** Total duration of one bar (count × unit) as a {@link Duration}. */
    public Duration totalDuration() {
        return Duration.ofSixtyFourths(sixtyFourths());
    }

    /** Total in sixty-fourths — {@code unitCount * unit.sixtyFourths()}. */
    public int sixtyFourths() {
        return unitCount * unit.sixtyFourths();
    }

    /** Map a time-signature denominator (1, 2, 4, 8, 16, 32, 64) to its BaseValue. */
    private static BaseValue baseValueFor(int denominator) {
        return switch (denominator) {
            case 1  -> BaseValue.WHOLE;
            case 2  -> BaseValue.HALF;
            case 4  -> BaseValue.QUARTER;
            case 8  -> BaseValue.EIGHTH;
            case 16 -> BaseValue.SIXTEENTH;
            case 32 -> BaseValue.THIRTY_SECOND;
            case 64 -> BaseValue.SIXTY_FOURTH;
            default -> throw new IllegalArgumentException(
                    "Unsupported time-signature denominator: " + denominator
                    + " (must be 1, 2, 4, 8, 16, 32, or 64)");
        };
    }
}
