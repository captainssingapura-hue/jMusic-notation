package music.notation.guitar;

import music.notation.duration.Duration;

import java.util.Objects;

/**
 * One note with its chosen place on the neck.
 *
 * <ul>
 *   <li>{@code at}, {@code duration} — as in the performance (fractions of a whole note).</li>
 *   <li>{@code midi} — the sounding pitch (after any octave folding).</li>
 *   <li>{@code position} — string and fret.</li>
 *   <li>{@code handPosition} — the fret the index finger sits at while
 *       this note is played (0 when the passage is all open strings).</li>
 *   <li>{@code bar} — 1-based bar; {@code beat} — 1-based beat within it, fractional.</li>
 *   <li>{@code folded} — true when the original pitch was out of range and moved by octaves.</li>
 * </ul>
 */
public record FingeredNote(Duration at, Duration duration, int midi, FretPosition position,
                           int handPosition, int bar, double beat, boolean folded) {

    public FingeredNote {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(duration, "duration");
        Objects.requireNonNull(position, "position");
        if (bar < 1) throw new IllegalArgumentException("bar must be >= 1");
    }

    public Duration endAt() { return at.plus(duration); }

    private static final String[] NAMES = {"C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B"};

    /** Pitch name with flats, e.g. {@code Bb3}. */
    public String pitchName() {
        return NAMES[Math.floorMod(midi, 12)] + (midi / 12 - 1);
    }
}
