package music.notation.duration;

/**
 * A dotted duration — adds half the base value.
 */
public record DottedDuration(BaseValue base) implements Duration {

    @Override
    public int sixtyFourths() {
        return base.sixtyFourths() + base.sixtyFourths() / 2;
    }

    @Override
    public Duration dot() {
        throw new UnsupportedOperationException("Double-dotted durations not supported");
    }
}
