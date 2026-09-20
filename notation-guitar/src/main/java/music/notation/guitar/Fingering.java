package music.notation.guitar;

import java.util.List;
import java.util.Objects;

/**
 * The solver's answer for one passage: every note placed, in time order,
 * with the total cost under the weights it was solved with.
 */
public record Fingering(List<FingeredNote> notes, double cost) {

    public Fingering {
        Objects.requireNonNull(notes, "notes");
        notes = List.copyOf(notes);
    }

    public static Fingering empty() { return new Fingering(List.of(), 0.0); }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isEmpty() { return notes.isEmpty(); }

    /** Number of times the hand position changes between consecutive notes. */
    public int shifts() {
        int n = 0;
        for (int i = 1; i < notes.size(); i++) {
            if (notes.get(i).handPosition() != notes.get(i - 1).handPosition()) n++;
        }
        return n;
    }

    public int lowestFret()  { return notes.stream().mapToInt(x -> x.position().fret()).min().orElse(0); }
    public int highestFret() { return notes.stream().mapToInt(x -> x.position().fret()).max().orElse(0); }
    public int lowestString()  { return notes.stream().mapToInt(x -> x.position().string()).min().orElse(0); }
    public int highestString() { return notes.stream().mapToInt(x -> x.position().string()).max().orElse(0); }
}
