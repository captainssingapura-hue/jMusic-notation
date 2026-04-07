package music.notation.duration;

/**
 * A raw duration specified in sixty-fourths, used for tied/merged notes.
 */
public record RawDuration(int sixtyFourths) implements Duration {

    public RawDuration {
        if (sixtyFourths <= 0) {
            throw new IllegalArgumentException("sixty-fourths must be positive: " + sixtyFourths);
        }
    }

    @Override
    public Duration dot() {
        throw new UnsupportedOperationException("Cannot dot a raw duration");
    }
}
