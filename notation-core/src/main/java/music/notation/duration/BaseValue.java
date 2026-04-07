package music.notation.duration;

/**
 * Standard note values. Each constant is itself a {@link Duration}.
 */
public enum BaseValue implements Duration {
    WHOLE(64),
    HALF(32),
    QUARTER(16),
    EIGHTH(8),
    SIXTEENTH(4),
    THIRTY_SECOND(2),
    SIXTY_FOURTH(1);

    private final int sixtyFourths;

    BaseValue(int sixtyFourths) {
        this.sixtyFourths = sixtyFourths;
    }

    @Override
    public int sixtyFourths() {
        return sixtyFourths;
    }

    @Override
    public DottedDuration dot() {
        return new DottedDuration(this);
    }
}
