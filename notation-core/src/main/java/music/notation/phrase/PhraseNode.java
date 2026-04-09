package music.notation.phrase;

public sealed interface PhraseNode permits NoteNode, RestNode, PaddingNode, SubPhrase, DynamicNode, GraceNote, PercussionNote, SlurStart, SlurEnd {
}
