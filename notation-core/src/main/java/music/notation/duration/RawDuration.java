package music.notation.duration;

/**
 * Arbitrary-rational duration {@code (numerator/denominator)} of a
 * whole note. Result type for {@code Duration} arithmetic and the
 * escape hatch for any musical fraction not expressible as
 * {@link BaseValue}, {@link Dotted}, {@link Triplet}, or
 * {@link RawTuplet}.
 *
 * <p>Always normalised to lowest terms with positive denominator
 * via the compact constructor (gcd reduction).</p>
 *
 * <p>Allows {@code numerator == 0} for the additive identity
 * ({@code Duration.ZERO}-style use). The denominator must be
 * non-zero.</p>
 */
public record RawDuration(long numerator, long denominator) implements Duration {

    public RawDuration {
        if (denominator == 0) {
            throw new IllegalArgumentException("denominator must not be 0");
        }
        // Normalise sign: keep denominator positive.
        if (denominator < 0) {
            numerator = -numerator;
            denominator = -denominator;
        }
        // Reduce to lowest terms.
        if (numerator != 0) {
            long g = gcd(Math.abs(numerator), denominator);
            if (g > 1) {
                numerator /= g;
                denominator /= g;
            }
        } else {
            denominator = 1;     // canonical 0/1
        }
    }

    @Override
    public Duration dot() {
        return new RawDuration(numerator * 3, denominator * 2);
    }

    private static long gcd(long a, long b) {
        while (b != 0) {
            long t = b;
            b = a % b;
            a = t;
        }
        return a == 0 ? 1 : a;
    }
}
