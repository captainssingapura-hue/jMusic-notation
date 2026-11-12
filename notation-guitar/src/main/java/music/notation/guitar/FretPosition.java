package music.notation.guitar;

/** One stopped (or open) string: {@code string} 1 = highest, {@code fret} 0 = open. */
public record FretPosition(int string, int fret) implements Comparable<FretPosition> {

    public FretPosition {
        if (string < 1) throw new IllegalArgumentException("string must be >= 1: " + string);
        if (fret < 0) throw new IllegalArgumentException("fret must be >= 0: " + fret);
    }

    public boolean open() { return fret == 0; }

    @Override public int compareTo(FretPosition o) {
        return string != o.string ? Integer.compare(string, o.string) : Integer.compare(fret, o.fret);
    }

    @Override public String toString() { return "s" + string + "f" + fret; }
}
