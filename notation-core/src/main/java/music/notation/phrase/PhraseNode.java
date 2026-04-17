package music.notation.phrase;

public sealed interface PhraseNode permits NoteNode, RestNode, PaddingNode, SubPhrase, DynamicNode, PercussionNote, SlurStart, SlurEnd, TempoChangeNode, TempoTransitionStartNode, TempoTransitionEndNode {
}
