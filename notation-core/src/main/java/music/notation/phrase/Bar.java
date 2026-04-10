package music.notation.phrase;

import music.notation.event.ChordEvent;

import java.util.List;

/**
 * A single bar (measure) of music — a group of {@link PhraseNode}s whose
 * total duration must match the declared bar length.
 *
 * <p>The constructor validates that the nodes' total duration equals
 * {@code expectedSixtyFourths}; any mismatch throws immediately.</p>
 */
public record Bar(int expectedSixtyFourths, List<PhraseNode> nodes, List<AuxBar> auxBars) {

    public Bar {
        nodes = List.copyOf(nodes);
        auxBars = List.copyOf(auxBars);
        int actual = 0;
        for (var node : nodes) {
            actual += nodeSixtyFourths(node);
        }
        if (actual != expectedSixtyFourths) {
            throw new IllegalArgumentException(
                    "Bar: expected " + expectedSixtyFourths
                            + " sixty-fourths but nodes total " + actual);
        }
    }

    public static Bar of(int expectedSixtyFourths, PhraseNode... nodes) {
        return new Bar(expectedSixtyFourths, List.of(nodes), List.of());
    }

    /**
     * Total duration of this bar in 64th-note units.
     * Always equals {@code expectedSixtyFourths} (validated at construction).
     */
    public int totalSixtyFourths() {
        return expectedSixtyFourths;
    }

    /** Duration of a single node in 64th-note units. */
    public static int nodeSixtyFourths(PhraseNode node) {
        return switch (node) {
            case NoteNode n -> n.duration().sixtyFourths();
            case RestNode r -> r.duration().sixtyFourths();
            case PercussionNote p -> p.duration().sixtyFourths();
            case DynamicNode d -> 0;
            case GraceNote g -> 0;
            case SlurStart s -> 0;
            case SlurEnd s -> 0;
            case PaddingNode p -> p.duration().sixtyFourths();
            case SubPhrase s -> phraseSixtyFourths(s.phrase());
        };
    }

    /** Total duration of a phrase in 64th-note units. */
    public static int phraseSixtyFourths(Phrase phrase) {
        return switch (phrase) {
            case MelodicPhrase mp -> mp.nodes().stream().mapToInt(Bar::nodeSixtyFourths).sum();
            case DrumPhrase dp -> dp.nodes().stream().mapToInt(Bar::nodeSixtyFourths).sum();
            case ChordPhrase cp -> cp.chords().stream().mapToInt(c -> c.duration().sixtyFourths()).sum();
            case RestPhrase rp -> rp.duration().sixtyFourths();
            case ShiftedPhrase sp -> phraseSixtyFourths(sp.source());
            case LyricPhrase lp -> lp.syllables().stream().mapToInt(e -> e.duration().sixtyFourths()).sum();
        };
    }
}
