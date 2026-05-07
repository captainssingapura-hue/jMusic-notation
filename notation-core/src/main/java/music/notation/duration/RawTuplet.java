package music.notation.duration;

/**
 * A general N-in-time-of-M tuplet of a base value. Each note's value
 * is {@code base × normalCount / actualCount}.
 *
 * <p>For the common case where {@code normalCount} is the largest
 * power of two strictly less than {@code actualCount}, use the
 * {@link #ofStandard(int, BaseValue)} factory or — for triplets
 * specifically — the dedicated {@link Triplet} type (which is more
 * ergonomic and has constants).</p>
 *
 * <h3>Examples</h3>
 * <ul>
 *   <li>{@code RawTuplet.ofStandard(5, EIGHTH)} = {@code 1/10}
 *       (quintuplet eighth — 5 in time of 4 eighths)</li>
 *   <li>{@code RawTuplet.ofStandard(7, EIGHTH)} = {@code 1/14}
 *       (septuplet eighth — 7 in time of 4 eighths)</li>
 *   <li>{@code RawTuplet.ofStandard(9, EIGHTH)} = {@code 1/9}
 *       (nonuplet eighth — 9 in time of 8 eighths)</li>
 *   <li>{@code new RawTuplet(5, 6, EIGHTH)} = {@code 6/40 = 3/20}
 *       (5 in time of 6 eighths — non-standard ratio)</li>
 * </ul>
 *
 * <p>For arbitrary fractions outside the (count, over, base) shape,
 * use {@link RawDuration} via {@code Duration.of(num, den)}.</p>
 */
public record RawTuplet(int actualCount, int normalCount, BaseValue base)
        implements Duration {

    public RawTuplet {
        if (actualCount < 2) {
            throw new IllegalArgumentException(
                    "actualCount must be >= 2, got " + actualCount);
        }
        if (normalCount < 1) {
            throw new IllegalArgumentException(
                    "normalCount must be >= 1, got " + normalCount);
        }
        if (base == null) {
            throw new IllegalArgumentException("base must not be null");
        }
    }

    /**
     * Standard convention: {@code count} notes in time of the largest
     * power of two strictly less than {@code count}, of {@code base}.
     *
     * <p>Resolves to:</p>
     * <ul>
     *   <li>3 → 2 (triplet — but prefer {@link Triplet})</li>
     *   <li>5 → 4 (quintuplet)</li>
     *   <li>6 → 4 (sextuplet)</li>
     *   <li>7 → 4 (septuplet)</li>
     *   <li>9 → 8 (nonuplet)</li>
     *   <li>11 → 8, 13 → 8 (rare)</li>
     * </ul>
     */
    public static RawTuplet ofStandard(int count, BaseValue base) {
        return new RawTuplet(count, Integer.highestOneBit(count - 1), base);
    }

    @Override
    public long numerator() {
        return base.numerator() * (long) normalCount;
    }

    @Override
    public long denominator() {
        return base.denominator() * (long) actualCount;
    }

    @Override
    public Duration dot() {
        // Dotted tuplet is rare; degrade to RawDuration.
        return new RawDuration(numerator() * 3, denominator() * 2);
    }
}
