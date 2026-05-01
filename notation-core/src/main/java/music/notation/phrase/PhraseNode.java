package music.notation.phrase;

public sealed interface PhraseNode permits PitchNode, RestNode, PaddingNode, SubPhrase, DynamicNode, PercussionNote, TempoChangeNode, TempoTransitionStartNode, TempoTransitionEndNode {
}
