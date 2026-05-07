package music.notation.duration;

/**
 * A dotted duration — {@code base × (2^(d+1) - 1) / 2^d}, where
 * {@code d} is the number of dots (1, 2, or 3).
 *
 * <p>Examples (single dot, the {@code d=1} default):</p>
 * <ul>
 *   <li>{@code new Dotted(QUARTER)} = {@code 3/8}  (dotted quarter)</li>
 *   <li>{@code new Dotted(EIGHTH)}  = {@code 3/16} (dotted eighth)</li>
 * </ul>
 *
 * <p>Multi-dot:</p>
 * <ul>
 *   <li>{@code new Dotted(QUARTER, 2)} = {@code 7/16} (double-dotted quarter)</li>
 *   <li>{@code new Dotted(HALF, 3)}    = {@code 15/16} (triple-dotted half)</li>
 * </ul>
 *
 * <p>Multi-dot beyond 3 falls outside standard notation. Use
 * {@link RawDuration} via {@code Duration.of(num, den)} for
 * unusual fractions.</p>
 */
public record Dotted(BaseValue base, int dotCount) implements Duration {

    public Dotted {
        if (base == null) {
            throw new IllegalArgumentException("base must not be null");
        }
        if (dotCount < 1 || dotCount > 3) {
            throw new IllegalArgumentException(
                    "dotCount must be 1..3, got " + dotCount);
        }
    }

    /** Single-dot convenience constructor. */
    public Dotted(BaseValue base) {
        this(base, 1);
    }

    @Override
    public long numerator() {
        // For d dots: factor = (2^(d+1) - 1)
        return base.numerator() * ((1L << (dotCount + 1)) - 1L);
    }

    @Override
    public long denominator() {
        // For d dots: factor = 2^d
        return base.denominator() * (1L << dotCount);
    }

    @Override
    public Duration dot() {
        // Add another dot, up to 3. Beyond that, degrade to RawDuration.
        if (dotCount < 3) {
            return new Dotted(base, dotCount + 1);
        }
        return new RawDuration(numerator() * 3, denominator() * 2);
    }

    // ── Constants for common single-dotted values ───────────────────

    public static final Dotted WHOLE         = new Dotted(BaseValue.WHOLE);
    public static final Dotted HALF          = new Dotted(BaseValue.HALF);
    public static final Dotted QUARTER       = new Dotted(BaseValue.QUARTER);
    public static final Dotted EIGHTH        = new Dotted(BaseValue.EIGHTH);
    public static final Dotted SIXTEENTH     = new Dotted(BaseValue.SIXTEENTH);
    public static final Dotted THIRTY_SECOND = new Dotted(BaseValue.THIRTY_SECOND);
}
