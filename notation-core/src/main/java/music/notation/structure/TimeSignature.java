package music.notation.structure;

public record TimeSignature(int beats, int beatValue) {
    public TimeSignature {
        if (beats < 1 || beatValue < 1) {
            throw new IllegalArgumentException("Invalid time signature: " + beats + "/" + beatValue);
        }
    }

    /**
     * Expected bar duration in 64th-note units.
     * E.g. 4/4 → 64, 3/4 → 48, 6/8 → 48.
     */
    public int barSixtyFourths() {
        return beats * (64 / beatValue);
    }
}
