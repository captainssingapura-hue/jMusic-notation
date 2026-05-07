package music.notation.duration;

/**
 * A musical duration — sealed ADT, rational-backed.
 *
 * <p>Every {@code Duration} is a fraction of a whole note, exposed as
 * {@link #numerator()} / {@link #denominator()}. The five variants
 * preserve <em>type identity</em> (useful for future score rendering)
 * while sharing one canonical rational representation:</p>
 *
 * <ul>
 *   <li>{@link BaseValue} — the seven powers-of-two (WHOLE … SIXTY_FOURTH)
 *       plus HUNDRED_TWENTY_EIGHTH</li>
 *   <li>{@link Dotted} — {@code base × (2^(d+1) - 1) / 2^d} (single, double
 *       or triple dot)</li>
 *   <li>{@link Triplet} — {@code base × 2/3} (the killer-app tuplet,
 *       3 in time of 2)</li>
 *   <li>{@link RawTuplet} — general N-in-time-of-M of a base value</li>
 *   <li>{@link RawDuration} — arbitrary {@code (numerator, denominator)};
 *       result of arithmetic ops, escape hatch for unusual values</li>
 * </ul>
 *
 * <h3>Author-side examples</h3>
 * <pre>{@code
 * Duration q   = QUARTER;                   // BaseValue
 * Duration dq  = Dotted.QUARTER;            // dotted quarter (3/8)
 * Duration q2  = QUARTER.dot();             // same — = Dotted(QUARTER)
 * Duration te  = Triplet.EIGHTH;            // triplet eighth (1/12)
 * Duration qt  = Duration.tuplet(5, EIGHTH);// quintuplet eighth (1/10)
 * Duration raw = Duration.of(7, 32);        // double-dotted half-ish
 * }</pre>
 *
 * <h3>Equality</h3>
 * <p>Java {@code equals} is <em>type-aware</em> (record default).
 * For value comparison ignoring variant type, use
 * {@link #equalsDuration}.</p>
 */
public sealed interface Duration permits BaseValue, Dotted, Triplet, RawTuplet, RawDuration {

    // ── rational accessors ─────────────────────────────────────────

    long numerator();
    long denominator();

    // ── back-compat (lossy for non-power-of-2 fractions) ───────────

    /**
     * Approximate sixty-fourths-of-a-whole. Exact for all
     * {@link BaseValue}s, {@link Dotted} values down to 64th, and
     * any {@link RawDuration} whose denominator divides 64. Lossy
     * (rounds toward zero) for triplets, quintuplets, etc.
     */
    default int sixtyFourths() {
        return Math.toIntExact(numerator() * 64 / denominator());
    }

    // ── core ops ───────────────────────────────────────────────────

    /** Return a dotted version of this duration. */
    Duration dot();

    /** Sum of two durations as a rational fraction. */
    default Duration plus(Duration o) {
        return new RawDuration(
                this.numerator() * o.denominator() + o.numerator() * this.denominator(),
                this.denominator() * o.denominator());
    }

    /** Difference of two durations. May be negative — use with care. */
    default Duration minus(Duration o) {
        return new RawDuration(
                this.numerator() * o.denominator() - o.numerator() * this.denominator(),
                this.denominator() * o.denominator());
    }

    /** Scalar multiplication. */
    default Duration times(long factor) {
        return new RawDuration(numerator() * factor, denominator());
    }

    /** Scalar division. */
    default Duration dividedBy(long divisor) {
        if (divisor == 0) throw new ArithmeticException("divide by 0");
        return new RawDuration(numerator(), denominator() * divisor);
    }

    /** Compare two durations by value, ignoring variant type. */
    default int compareDuration(Duration o) {
        return Long.compare(numerator() * o.denominator(),
                            o.numerator() * denominator());
    }

    /**
     * Value-aware equality. Two durations of <em>different variant types</em>
     * but same fraction (e.g. {@code Triplet.EIGHTH} vs
     * {@code Duration.of(1, 12)}) compare equal here. For type-aware
     * equality (Java {@code equals}), use {@code equals} as usual.
     */
    default boolean equalsDuration(Duration o) {
        return numerator() * o.denominator() == o.numerator() * denominator();
    }

    /**
     * Exact ticks at a given PPQ — preserves precision for any rational.
     * {@code ppq} = ticks per quarter note; one whole = {@code 4 × ppq} ticks.
     */
    default long ticks(long ppq) {
        return numerator() * 4L * ppq / denominator();
    }

    /**
     * Canonical (normalized {@link RawDuration}) form. Useful for set
     * membership where you want to ignore variant type.
     */
    default Duration canonical() {
        return new RawDuration(numerator(), denominator());
    }

    // ── factories ──────────────────────────────────────────────────

    /** Plain duration from a base value (identity). */
    static Duration of(BaseValue base) {
        return base;
    }

    /** Arbitrary rational duration (escape hatch). */
    static Duration of(long numerator, long denominator) {
        return new RawDuration(numerator, denominator);
    }

    /** Raw duration from a sixty-fourths count (used for tied/merged notes). */
    static Duration ofSixtyFourths(int sixtyFourths) {
        return new RawDuration(sixtyFourths, 64);
    }

    /** Dotted (single dot) duration of a base value. */
    static Duration dotted(BaseValue base) {
        return new Dotted(base);
    }

    /** Triplet ({@code 3-in-time-of-2}) of a base value. */
    static Duration triplet(BaseValue base) {
        return new Triplet(base);
    }

    /** Standard-convention tuplet — {@code count} notes in time of (largest 2^k &lt; count). */
    static Duration tuplet(int count, BaseValue base) {
        return RawTuplet.ofStandard(count, base);
    }

    /** General tuplet — {@code actualCount} notes in time of {@code normalCount} of {@code base}. */
    static Duration tuplet(int actualCount, int normalCount, BaseValue base) {
        return new RawTuplet(actualCount, normalCount, base);
    }
}
