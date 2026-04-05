package music.notation.phrase;

public sealed interface PhraseNode permits NoteNode, RestNode, SubPhrase, DynamicNode, GraceNote, PercussionNote, SlurStart, SlurEnd {
}
