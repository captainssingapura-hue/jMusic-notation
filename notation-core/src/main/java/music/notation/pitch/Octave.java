package music.notation.pitch;

public record Octave(int value) {
    public Octave {
        if (value < 0 || value > 10) {
            throw new IllegalArgumentException("Octave out of range: " + value);
        }
    }
}
