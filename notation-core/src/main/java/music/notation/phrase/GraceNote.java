package music.notation.phrase;

import music.notation.pitch.Pitch;

public record GraceNote(Pitch pitch, boolean accented) implements PhraseNode {
}
