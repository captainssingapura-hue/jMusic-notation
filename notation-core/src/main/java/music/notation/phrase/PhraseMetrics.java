package music.notation.phrase;

import java.util.List;

/**
 * Static utilities to measure phrase structure in 64th-note units — the native
 * integer unit of the notation layer. Used by {@link music.notation.structure.Track}
 * for construction-time elision validation and by the playback layer for
 * tick-accurate timing (after multiplying by {@code TICKS_PER_QUARTER/16}).
 */
public final class PhraseMetrics {

    private PhraseMetrics() {}

    /**
     * Duration (in 64ths) of leading {@link PaddingNode}s at the very start of a
     * phrase's node list — the anacrusis silence before any audible content.
     * Zero-duration markers (dynamics, slurs, tempo) are skipped; the walk stops
     * at the first audible node.
     */
    public static int leadingPaddingSixtyFourths(Phrase phrase) {
        return switch (phrase) {
            case MelodicPhrase mp -> leadingPaddingFromNodes(mp.nodes());
            case DrumPhrase dp    -> leadingPaddingFromNodes(dp.nodes());
            case ShiftedPhrase sp -> leadingPaddingSixtyFourths(sp.source());
            case LayeredPhrase lp -> leadingPaddingSixtyFourths(lp.resolve());
            default -> 0;
        };
    }

    /**
     * Duration (in 64ths) of trailing {@link PaddingNode}s at the very end of a
     * phrase's node list — the silence left over after the last audible content
     * (typically added by {@code ending()}).
     */
    public static int trailingPaddingSixtyFourths(Phrase phrase) {
        return switch (phrase) {
            case MelodicPhrase mp -> trailingPaddingFromNodes(mp.nodes());
            case DrumPhrase dp    -> trailingPaddingFromNodes(dp.nodes());
            case ShiftedPhrase sp -> trailingPaddingSixtyFourths(sp.source());
            case LayeredPhrase lp -> trailingPaddingSixtyFourths(lp.resolve());
            default -> 0;
        };
    }

    /**
     * Expected size (in 64ths) of a phrase's last bar. Returns 0 if the phrase
     * has no bar structure (e.g. a hand-built {@link MelodicPhrase} without
     * the bar list populated, or a non-structured phrase type).
     */
    public static int lastBarSixtyFourths(Phrase phrase) {
        return switch (phrase) {
            case MelodicPhrase mp -> barListLast(mp.bars());
            case ShiftedPhrase sp -> lastBarSixtyFourths(sp.source());
            case LayeredPhrase lp -> lastBarSixtyFourths(lp.resolve());
            default -> 0;
        };
    }

    private static int barListLast(List<Bar> bars) {
        return bars.isEmpty() ? 0 : bars.get(bars.size() - 1).expectedSixtyFourths();
    }

    private static int leadingPaddingFromNodes(List<PhraseNode> nodes) {
        int padding = 0;
        for (PhraseNode node : nodes) {
            switch (node) {
                case PaddingNode p -> padding += p.duration().sixtyFourths();
                case DynamicNode d -> {}
                case TempoChangeNode t -> {}
                case TempoTransitionStartNode t -> {}
                case TempoTransitionEndNode t -> {}
                default -> { return padding; } // first audible node reached
            }
        }
        return padding;
    }

    private static int trailingPaddingFromNodes(List<PhraseNode> nodes) {
        int padding = 0;
        for (int i = nodes.size() - 1; i >= 0; i--) {
            PhraseNode node = nodes.get(i);
            switch (node) {
                case PaddingNode p -> padding += p.duration().sixtyFourths();
                case DynamicNode d -> {}
                case TempoChangeNode t -> {}
                case TempoTransitionStartNode t -> {}
                case TempoTransitionEndNode t -> {}
                default -> { return padding; } // first audible node from end
            }
        }
        return padding;
    }
}
