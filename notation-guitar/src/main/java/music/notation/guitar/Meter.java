package music.notation.guitar;

import music.notation.duration.Duration;
import music.notation.performance.Performance;
import music.notation.structure.TimeSignature;

/**
 * Bar arithmetic for a constant time signature. Positions in a
 * {@link Performance} are fractions of a whole note; this turns them
 * into (bar, beat) and back.
 *
 * <p>Only the first time signature of the piece is honoured; pieces
 * that change metre mid-way get their bars counted as if they did not.</p>
 */
public record Meter(TimeSignature timeSignature) {

    public static final Meter FOUR_FOUR = new Meter(new TimeSignature(4, 4));

    public static Meter of(Performance p) {
        var changes = p.timeSignatures() == null ? null : p.timeSignatures().changes();
        if (changes == null || changes.isEmpty()) return FOUR_FOUR;
        return new Meter(changes.get(0).timeSig());
    }

    /** Length of one bar as a fraction of a whole note. */
    public Duration barLength() {
        return Duration.of(timeSignature.beats(), timeSignature.beatValue());
    }

    /** Length of one beat as a fraction of a whole note. */
    public Duration beatLength() {
        return Duration.of(1, timeSignature.beatValue());
    }

    /** 0-based bar containing {@code at}. */
    public int barOf(Duration at) {
        Duration bar = barLength();
        return Math.toIntExact(Math.floorDiv(at.numerator() * bar.denominator(), bar.numerator() * at.denominator()));
    }

    /** Start of 0-based bar {@code bar0}. */
    public Duration startOfBar(int bar0) {
        return barLength().times(bar0);
    }

    /** Beat within the bar (0-based, fractional) of {@code at}. */
    public double beatInBar(Duration at) {
        Duration offset = at.minus(startOfBar(barOf(at)));
        return toBeats(offset);
    }

    /** A span expressed in beats. */
    public double toBeats(Duration d) {
        Duration beat = beatLength();
        return (double) (d.numerator() * beat.denominator()) / (double) (d.denominator() * beat.numerator());
    }
}
