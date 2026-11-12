package music.notation.guitar;

/** Inclusive 1-based bar range, as a musician says it: "bars 17 to 24". */
public record BarRange(int fromBar, int toBar) {

    public BarRange {
        if (fromBar < 1) throw new IllegalArgumentException("fromBar must be >= 1: " + fromBar);
        if (toBar < fromBar) throw new IllegalArgumentException("toBar must be >= fromBar");
    }

    public int bars() { return toBar - fromBar + 1; }

    public boolean contains(int bar1) { return bar1 >= fromBar && bar1 <= toBar; }

    @Override public String toString() { return fromBar == toBar ? "bar " + fromBar : "bars " + fromBar + "–" + toBar; }
}
