package music.notation.phrase;

import music.notation.pitch.Pitch;

/**
 * A grace note preceding a main {@link NoteNode}.
 * No longer a standalone {@link PhraseNode} — carried inside {@code NoteNode.graceNotes()}.
 */
public record GraceNote(Pitch pitch, boolean accented) {
}
