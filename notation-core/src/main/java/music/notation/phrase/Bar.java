package music.notation.phrase;

import music.notation.duration.BarDuration;
import music.notation.duration.Duration;

import java.util.List;

/**
 * A single bar (measure) of music — a group of {@link PhraseNode}s whose
 * total duration must match the declared bar length.
 *
 * <p>The bar's expected size is carried as a {@link BarDuration}
 * (logical {@code (count, unit)} pair — e.g. {@code (4, QUARTER)} for
 * 4/4) rather than a raw integer sf count. The constructor validates
 * that the nodes' total duration equals
 * {@code expectedDuration.sixtyFourths()}; any mismatch throws
 * immediately.</p>
 *
 * <p>{@link #expectedSixtyFourths()} is preserved as a derived view
 * for converters and visualisers that still need a raw sf integer.</p>
 *
 * <p>Auxiliary parallel voices live on {@link music.notation.structure.MelodicTrack}
 * as a sparse {@code Map<String, Map<Integer, Bar>>} keyed by aux name +
 * bar index — not on {@code Bar} itself. Each aux voice expands to a
 * complete dense bar list, with gaps filled via {@link #silent}.</p>
 */
public record Bar(BarDuration expectedDuration, List<PhraseNode> nodes) {

    public Bar {
        nodes = List.copyOf(nodes);
        // Sum nodes' durations as exact rationals — handles triplets,
        // quintuplets, and any other tuplet without precision loss.
        Duration actual = new music.notation.duration.RawDuration(0, 1);
        for (var node : nodes) {
            actual = actual.plus(nodeDuration(node));
        }
        Duration expected = expectedDuration.totalDuration();
        if (!actual.equalsDuration(expected)) {
            throw new IllegalArgumentException(
                    "Bar: expected " + expected.numerator() + "/" + expected.denominator()
                            + " (" + expectedDuration.sixtyFourths() + " sf)"
                            + " but nodes total "
                            + actual.numerator() + "/" + actual.denominator());
        }
    }

    // ── Factories ────────────────────────────────────────────────────

    /** Author-side factory taking a logical {@link BarDuration}. */
    public static Bar of(BarDuration barDuration, PhraseNode... nodes) {
        return new Bar(barDuration, List.of(nodes));
    }

    /**
     * Backward-compat factory accepting an integer sixty-fourths count.
     * The {@code int} is reverse-decoded into a {@link BarDuration} via
     * {@link BarDuration#fromSixtyFourths(int)} — best-effort meter
     * inference. Authors who want explicit meter character should use
     * the {@link #of(BarDuration, PhraseNode...)} overload directly.
     */
    public static Bar of(int expectedSixtyFourths, PhraseNode... nodes) {
        return new Bar(BarDuration.fromSixtyFourths(expectedSixtyFourths), List.of(nodes));
    }

    /** Silent-bar factory taking a {@link BarDuration}. */
    public static Bar silent(BarDuration barDuration) {
        return new Bar(barDuration,
                List.of(new PaddingNode(Duration.ofSixtyFourths(barDuration.sixtyFourths()))));
    }

    /** Backward-compat silent-bar factory; reverse-decodes via {@link BarDuration#fromSixtyFourths}. */
    public static Bar silent(int expectedSixtyFourths) {
        return silent(BarDuration.fromSixtyFourths(expectedSixtyFourths));
    }

    // ── Derived view ─────────────────────────────────────────────────

    /**
     * Total duration of this bar in 64th-note units. Equivalent to
     * {@code expectedDuration().sixtyFourths()} — kept for backward
     * compatibility with converters and visualisers that operate on
     * raw integer sf.
     */
    public int expectedSixtyFourths() {
        return expectedDuration.sixtyFourths();
    }

    /** Synonym for {@link #expectedSixtyFourths()}. */
    public int totalSixtyFourths() {
        return expectedDuration.sixtyFourths();
    }

    // ── Static node-walking helpers ──────────────────────────────────

    /**
     * Duration of a single node as an exact rational. Returns
     * {@code RawDuration(0, 1)} for nodes that don't consume time
     * (dynamics, tempo events).
     */
    public static Duration nodeDuration(PhraseNode node) {
        return switch (node) {
            case PitchNode n -> n.duration();
            case RestNode r -> r.duration();
            case PercussionNote p -> p.duration();
            case DynamicNode d -> new music.notation.duration.RawDuration(0, 1);
            case TempoChangeNode t -> new music.notation.duration.RawDuration(0, 1);
            case TempoTransitionStartNode t -> new music.notation.duration.RawDuration(0, 1);
            case TempoTransitionEndNode t -> new music.notation.duration.RawDuration(0, 1);
            case PaddingNode p -> p.duration();
            case SubPhrase s -> phraseDuration(s.phrase());
        };
    }

    /** Total duration of a phrase as an exact rational. */
    public static Duration phraseDuration(AuthorPhrase phrase) {
        return switch (phrase) {
            case MelodicPhrase mp -> {
                Duration sum = new music.notation.duration.RawDuration(0, 1);
                for (var n : mp.nodes()) sum = sum.plus(nodeDuration(n));
                yield sum;
            }
            case ChordPhrase cp -> {
                Duration sum = new music.notation.duration.RawDuration(0, 1);
                for (var c : cp.chords()) sum = sum.plus(c.duration());
                yield sum;
            }
            case RestPhrase rp -> rp.duration();
            case LayeredPhrase lp -> phraseDuration(lp.base());
        };
    }

    /** Duration of a single node in 64th-note units (lossy for tuplets). */
    public static int nodeSixtyFourths(PhraseNode node) {
        return nodeDuration(node).sixtyFourths();
    }

    /** Total duration of a phrase in 64th-note units (lossy for tuplets). */
    public static int phraseSixtyFourths(AuthorPhrase phrase) {
        return phraseDuration(phrase).sixtyFourths();
    }
}
