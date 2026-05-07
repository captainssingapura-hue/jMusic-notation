package music.notation.duration;

/**
 * Standard note values — the seven powers-of-two from whole down to
 * sixty-fourth, plus the rare 128th. Each constant is a
 * {@link Duration} singleton, sharing reference identity across the
 * JVM (so {@code QUARTER == QUARTER} works as expected).
 *
 * <p>Internally rational-backed; {@link #sixtyFourths()} returns
 * exact integer values for these power-of-two fractions.</p>
 */
public enum BaseValue implements Duration {
    WHOLE                 (1, 1),
    HALF                  (1, 2),
    QUARTER               (1, 4),
    EIGHTH                (1, 8),
    SIXTEENTH             (1, 16),
    THIRTY_SECOND         (1, 32),
    SIXTY_FOURTH          (1, 64),
    HUNDRED_TWENTY_EIGHTH (1, 128);

    private final long numerator;
    private final long denominator;

    BaseValue(long numerator, long denominator) {
        this.numerator = numerator;
        this.denominator = denominator;
    }

    @Override public long numerator()   { return numerator; }
    @Override public long denominator() { return denominator; }

    @Override
    public Dotted dot() {
        return new Dotted(this);
    }
}
