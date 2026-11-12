package music.notation.guitar;

import music.notation.performance.PitchedNote;

import java.util.List;

/** Thrown when notes cannot be placed on the neck and octave folding is off. */
public final class UnplayableNotesException extends IllegalArgumentException {

    private final List<PitchedNote> notes;

    public UnplayableNotesException(String message, List<PitchedNote> notes) {
        super(message);
        this.notes = List.copyOf(notes);
    }

    public List<PitchedNote> notes() { return notes; }
}
