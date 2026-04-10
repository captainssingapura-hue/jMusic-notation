package music.notation.pitch;

/**
 * A note with an explicit accidental that overrides the key signature.
 */
public record AccidentedNote(NoteName noteName, Accidental accidental) implements Note {
}
