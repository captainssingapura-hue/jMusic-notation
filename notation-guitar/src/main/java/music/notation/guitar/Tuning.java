package music.notation.guitar;

import java.util.List;
import java.util.Objects;

/**
 * Open-string pitches of a guitar, high string first — string 1 is the
 * top line of a tab. {@code capo} shifts every open string up; fret
 * numbers in the arrangement are always counted from the capo.
 *
 * <pre>{@code
 *   { "name": "standard", "openStrings": [64, 59, 55, 50, 45, 40], "capo": 0 }
 * }</pre>
 */
public record Tuning(String name, List<Integer> openStrings, int capo) {

    public static final Tuning STANDARD = new Tuning("standard", List.of(64, 59, 55, 50, 45, 40), 0);
    public static final Tuning DROP_D   = new Tuning("drop D",   List.of(64, 59, 55, 50, 45, 38), 0);

    public Tuning {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(openStrings, "openStrings");
        if (openStrings.isEmpty()) throw new IllegalArgumentException("a tuning needs at least one string");
        if (capo < 0 || capo > 12) throw new IllegalArgumentException("capo out of range: " + capo);
        openStrings = List.copyOf(openStrings);
    }

    public int stringCount() { return openStrings.size(); }

    /** MIDI pitch of {@code string} (1 = highest) stopped at {@code fret} (0 = open, above the capo). */
    public int pitchAt(int string, int fret) {
        return openStrings.get(string - 1) + capo + fret;
    }

    /** Lowest sounding pitch: the lowest open string at the capo. */
    public int lowestPitch() {
        return openStrings.stream().mapToInt(Integer::intValue).min().orElseThrow() + capo;
    }

    /** Highest sounding pitch reachable with {@code maxFret}. */
    public int highestPitch(int maxFret) {
        return openStrings.stream().mapToInt(Integer::intValue).max().orElseThrow() + capo + maxFret;
    }

    public Tuning withCapo(int newCapo) { return new Tuning(name, openStrings, newCapo); }
}
