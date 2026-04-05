package music.notation.duration;

/**
 * A musical duration.
 *
 * <p>Normal durations are built from a {@link BaseValue} plus dots.
 * Tied (merged) durations carry a raw sixty-fourths count produced by
 * combining two durations during slur/tie resolution — see
 * {@link #ofSixtyFourths(int)}.</p>
 *
 * @param baseValue the note value (may be {@code null} for raw durations)
 * @param dots      dot count (0–3 for standard durations; 0 for raw)
 * @param rawSixtyFourths  if ≥ 0, overrides the computed value; -1 means "compute from baseValue+dots"
 */
public record Duration(BaseValue baseValue, int dots, int rawSixtyFourths) {

    /** Sentinel: compute sixty-fourths from baseValue + dots. */
    private static final int COMPUTE = -1;

    public Duration {
        if (rawSixtyFourths == COMPUTE) {
            if (baseValue == null) throw new IllegalArgumentException("baseValue required for standard durations");
            if (dots < 0 || dots > 3) throw new IllegalArgumentException("Dot count out of range: " + dots);
        }
    }

    public static Duration of(BaseValue base) {
        return new Duration(base, 0, COMPUTE);
    }

    public static Duration dotted(BaseValue base) {
        return new Duration(base, 1, COMPUTE);
    }

    /** Create a duration from a raw sixty-fourths count (used for tied/merged notes). */
    public static Duration ofSixtyFourths(int sixtyFourths) {
        if (sixtyFourths <= 0) throw new IllegalArgumentException("sixty-fourths must be positive: " + sixtyFourths);
        return new Duration(null, 0, sixtyFourths);
    }

    /**
     * Duration measured in 64th-note units (integer arithmetic, no rounding).
     * A whole note = 64, half = 32, quarter = 16, etc.
     * Dots add half-value increments: dotted quarter = 16 + 8 = 24.
     */
    public int sixtyFourths() {
        if (rawSixtyFourths >= 0) return rawSixtyFourths;
        int base = switch (baseValue) {
            case WHOLE -> 64;
            case HALF -> 32;
            case QUARTER -> 16;
            case EIGHTH -> 8;
            case SIXTEENTH -> 4;
            case THIRTY_SECOND -> 2;
            case SIXTY_FOURTH -> 1;
        };
        int total = base;
        int dotVal = base;
        for (int i = 0; i < dots; i++) {
            dotVal /= 2;
            total += dotVal;
        }
        return total;
    }
}
