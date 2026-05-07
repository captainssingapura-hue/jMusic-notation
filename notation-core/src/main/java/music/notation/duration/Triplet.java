package music.notation.duration;

/**
 * A triplet of a base value — three notes in the time of two of
 * {@code base}. Each note's value is {@code base × 2/3}.
 *
 * <p>Examples:</p>
 * <ul>
 *   <li>{@code new Triplet(EIGHTH)}  = {@code 1/12} (triplet eighth — 3 in time of 2 eighths = 1 quarter)</li>
 *   <li>{@code new Triplet(QUARTER)} = {@code 1/6}  (triplet quarter — 3 in time of 2 quarters = 1 half)</li>
 *   <li>{@code new Triplet(SIXTEENTH)} = {@code 1/24}</li>
 * </ul>
 *
 * <p>The "in time of 2" ratio is hard-coded because the triplet is
 * the single most common tuplet in Western music — it earns its
 * dedicated type. For other tuplet ratios use {@link RawTuplet}.</p>
 */
public record Triplet(BaseValue base) implements Duration {

    public Triplet {
        if (base == null) {
            throw new IllegalArgumentException("base must not be null");
        }
    }

    @Override
    public long numerator() {
        return base.numerator() * 2L;     // 3-in-time-of-2 → multiplier 2/3
    }

    @Override
    public long denominator() {
        return base.denominator() * 3L;
    }

    @Override
    public Duration dot() {
        // Dotted triplet is rare; degrade to RawDuration.
        return new RawDuration(numerator() * 3, denominator() * 2);
    }

    // ── Constants for common triplets ────────────────────────────────

    public static final Triplet HALF          = new Triplet(BaseValue.HALF);
    public static final Triplet QUARTER       = new Triplet(BaseValue.QUARTER);
    public static final Triplet EIGHTH        = new Triplet(BaseValue.EIGHTH);
    public static final Triplet SIXTEENTH     = new Triplet(BaseValue.SIXTEENTH);
    public static final Triplet THIRTY_SECOND = new Triplet(BaseValue.THIRTY_SECOND);
}
