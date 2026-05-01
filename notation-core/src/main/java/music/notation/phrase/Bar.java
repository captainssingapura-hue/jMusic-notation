package music.notation.phrase;

import music.notation.duration.Duration;

import java.util.List;

/**
 * A single bar (measure) of music — a group of {@link PhraseNode}s whose
 * total duration must match the declared bar length.
 *
 * <p>The constructor validates that the nodes' total duration equals
 * {@code expectedSixtyFourths}; any mismatch throws immediately.</p>
 *
 * <p>Auxiliary parallel voices live on {@link music.notation.structure.MelodicTrack}
 * as a sparse {@code Map<String, Map<Integer, Bar>>} keyed by aux name +
 * bar index — not on {@code Bar} itself. Each aux voice expands to a
 * complete dense bar list, with gaps filled via {@link #silent(int)}.</p>
 */
public record Bar(int expectedSixtyFourths, List<PhraseNode> nodes) {

    public Bar {
        nodes = List.copyOf(nodes);
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
        return new Bar(expectedSixtyFourths, List.of(nodes));
    }

    /**
     * Build a fully-silent bar of the given size — a single
     * {@link PaddingNode} summing to {@code expectedSixtyFourths}.
     * Used by aux-voice expansion to fill bars where the aux is absent.
     */
    public static Bar silent(int expectedSixtyFourths) {
        return new Bar(expectedSixtyFourths,
                List.of(new PaddingNode(Duration.ofSixtyFourths(expectedSixtyFourths))));
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
            case PitchNode n -> n.duration().sixtyFourths();
            case RestNode r -> r.duration().sixtyFourths();
            case PercussionNote p -> p.duration().sixtyFourths();
            case DynamicNode d -> 0;
            case TempoChangeNode t -> 0;
            case TempoTransitionStartNode t -> 0;
            case TempoTransitionEndNode t -> 0;
            case PaddingNode p -> p.duration().sixtyFourths();
            case SubPhrase s -> phraseSixtyFourths(s.phrase());
        };
    }

    /** Total duration of a phrase in 64th-note units. */
    public static int phraseSixtyFourths(AuthorPhrase phrase) {
        return switch (phrase) {
            case MelodicPhrase mp -> mp.nodes().stream().mapToInt(Bar::nodeSixtyFourths).sum();
            case ChordPhrase cp -> cp.chords().stream().mapToInt(c -> c.duration().sixtyFourths()).sum();
            case RestPhrase rp -> rp.duration().sixtyFourths();
            case LayeredPhrase lp -> phraseSixtyFourths(lp.base());
        };
    }
}
