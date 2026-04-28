package music.notation.phrase;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Composite {@link BarPhrase} — joins multiple child phrases with a
 * {@link ConnectingMode}. {@link #bars()} materialises the configured
 * stitch into a flat bar list.
 *
 * <h2>Resolution spec</h2>
 *
 * <h3>{@link ConnectingMode#ATTACCA}</h3>
 * Pure {@code flatMap} of {@code child.bars()} — no merging, no trimming.
 *
 * <h3>{@link ConnectingMode#ELIDED} — two-stage</h3>
 * For each boundary between {@code child[i]} and {@code child[i+1]}:
 *
 * <ol>
 *   <li><b>Stage 1 — within-bar pickup absorption.</b>
 *     Look at the last bar of {@code child[i]} ({@code last}) and the
 *     first bar of {@code child[i+1]} ({@code first}). Compute
 *     {@code trailPad64} (trailing {@link PaddingNode}/{@link RestNode}
 *     chain in {@code last}, in 64ths) and {@code leadPad64} (leading
 *     {@link PaddingNode}/{@link RestNode} chain in {@code first}).
 *     Compute {@code firstAudible64 = first.expectedSixtyFourths() - leadPad64}.
 *
 *     <ul>
 *       <li>If {@code firstAudible64 > 0 && firstAudible64 <= trailPad64}:
 *         <b>absorb</b>. Build a merged bar from {@code last}'s audible
 *         nodes + {@code first}'s audible nodes + a residual
 *         {@link PaddingNode} of {@code trailPad64 - firstAudible64} (if
 *         non-zero) so the merged bar still sums to its expected size.
 *         The merged bar replaces {@code last}; {@code first} is consumed
 *         (dropped from {@code child[i+1]}).</li>
 *       <li>If {@code firstAudible64 > trailPad64}: throw
 *         {@link IllegalStateException} — pickup doesn't fit; composer
 *         must shrink the pickup or grow the trailing pad.</li>
 *       <li>If {@code firstAudible64 == 0}: skip absorption (entire
 *         {@code first} is silent — falls through to Stage 2).</li>
 *     </ul>
 *   </li>
 *   <li><b>Stage 2 — whole-bar trim.</b>
 *     After Stage 1 (or if Stage 1 didn't apply): drop
 *     {@code min(t, l)} whole bars from the front of {@code child[i+1]},
 *     where {@code t} = trailing-silence-bar count of the
 *     (post-Stage-1) {@code child[i]} bar list and {@code l} =
 *     leading-silence-bar count of {@code child[i+1]}.</li>
 * </ol>
 *
 * <p>Implementation note: {@code bars()} computes on each call. If
 * profiling shows this hot, add a cache; for the current corpus a
 * recursive recompute is fine.</p>
 */
public record JoinedPhrase(String name, List<BarPhrase> children, ConnectingMode mode)
        implements BarPhrase {

    public JoinedPhrase {
        Objects.requireNonNull(name, "name must not be null (use \"\" for anonymous)");
        Objects.requireNonNull(mode, "mode must not be null");
        children = List.copyOf(children);
    }

    @Override
    public List<Bar> bars() {
        if (children.isEmpty()) {
            return List.of();
        }
        // Start with first child's bars (mutable working list).
        List<Bar> acc = new ArrayList<>(children.get(0).bars());
        for (int i = 1; i < children.size(); i++) {
            List<Bar> next = new ArrayList<>(children.get(i).bars());
            switch (mode) {
                case ATTACCA -> acc.addAll(next);
                case ELIDED  -> elideStitch(acc, next);
            }
        }
        return List.copyOf(acc);
    }

    // ── ELIDED stitch ────────────────────────────────────────────────

    private static void elideStitch(List<Bar> acc, List<Bar> next) {
        if (acc.isEmpty() || next.isEmpty()) {
            acc.addAll(next);
            return;
        }
        // Stage 1: pickup-bar absorption — collapse last+first into one
        // merged bar with pickup audible at the END of the bar so bar 2
        // of the pickup phrase follows immediately. Layout:
        //   [audible_last] [middle_gap] [audible_first]
        // where middle_gap = bar - audibleLast - audibleFirst >= 0.
        //
        // Triggers iff trail + lead >= bar (otherwise the audible content
        // of both sides won't fit alongside any gap). When the constraint
        // fails, we fall through to ATTACCA-like sequential playout —
        // no throw.
        Bar last = acc.get(acc.size() - 1);
        Bar first = next.get(0);
        int barSize = last.expectedSixtyFourths();
        int trailPad64 = trailingPadSixtyFourths(last);
        int leadPad64  = leadingPadSixtyFourths(first);

        if (trailPad64 > 0 && leadPad64 > 0
                && first.expectedSixtyFourths() == barSize
                && trailPad64 + leadPad64 >= barSize) {
            int audibleFirst64 = barSize - leadPad64;
            if (audibleFirst64 > 0) {
                Bar merged = mergeAbsorption(last, first, trailPad64, leadPad64);
                acc.set(acc.size() - 1, merged);
            }
            // Silent-only pickup (audibleFirst64 == 0): drop the pickup bar
            // without modifying last. Harmony "alignment" pickups land here.
            next.remove(0);
        }

        // Stage 2: whole-bar trim. Recompute counts post-Stage-1.
        int t = trailingSilenceBarCount(acc);
        int l = leadingSilenceBarCount(next);
        int trim = Math.min(t, l);
        for (int k = 0; k < trim; k++) {
            next.remove(0);
        }
        acc.addAll(next);
    }

    /**
     * Build a merged bar with the pickup audible at the END of the
     * bar so bar 2 of the pickup phrase follows immediately. Layout:
     * <pre>
     *   [audible_last] [middle_gap] [audible_first]
     * </pre>
     * where {@code middle_gap = bar - audibleLast - audibleFirst}.
     *
     * <p>Visual win: bar 2 of the pickup phrase butts up against the
     * pickup audible in the piano roll — no trailing residual gap
     * inside the merged bar.</p>
     */
    private static Bar mergeAbsorption(Bar last, Bar first, int trailPad64, int leadPad64) {
        int barSize = last.expectedSixtyFourths();
        int audibleLast64  = barSize - trailPad64;
        int audibleFirst64 = first.expectedSixtyFourths() - leadPad64;
        int middleGap64    = barSize - audibleLast64 - audibleFirst64;

        List<PhraseNode> lastAudible = stripTrailingPad(last.nodes(), trailPad64);
        List<PhraseNode> firstAudible = stripLeadingPad(first.nodes(), leadPad64);

        var merged = new ArrayList<PhraseNode>(
                lastAudible.size() + firstAudible.size() + 1);
        merged.addAll(lastAudible);
        if (middleGap64 > 0) {
            merged.add(new PaddingNode(
                    music.notation.duration.Duration.ofSixtyFourths(middleGap64)));
        }
        merged.addAll(firstAudible);
        return new Bar(barSize, merged, last.auxBars());
    }

    // ── Bar inspection helpers ───────────────────────────────────────

    /** True iff the node is structural silence (no audible output). */
    private static boolean isSilence(PhraseNode node) {
        return node instanceof PaddingNode || node instanceof RestNode;
    }

    /** Sum of trailing silence-node durations in 64ths. */
    static int trailingPadSixtyFourths(Bar bar) {
        var nodes = bar.nodes();
        int total = 0;
        for (int i = nodes.size() - 1; i >= 0; i--) {
            PhraseNode n = nodes.get(i);
            if (!isSilence(n)) break;
            total += Bar.nodeSixtyFourths(n);
        }
        return total;
    }

    /** Sum of leading silence-node durations in 64ths. */
    static int leadingPadSixtyFourths(Bar bar) {
        int total = 0;
        for (PhraseNode n : bar.nodes()) {
            if (!isSilence(n)) break;
            total += Bar.nodeSixtyFourths(n);
        }
        return total;
    }

    /** Drop the trailing silence chain from a node list. */
    private static List<PhraseNode> stripTrailingPad(List<PhraseNode> nodes, int trailPad64) {
        if (trailPad64 == 0) return nodes;
        int idx = nodes.size();
        int acc = 0;
        for (int i = nodes.size() - 1; i >= 0; i--) {
            PhraseNode n = nodes.get(i);
            if (!isSilence(n)) break;
            acc += Bar.nodeSixtyFourths(n);
            idx = i;
            if (acc >= trailPad64) break;
        }
        return new ArrayList<>(nodes.subList(0, idx));
    }

    /** Drop the leading silence chain from a node list. */
    private static List<PhraseNode> stripLeadingPad(List<PhraseNode> nodes, int leadPad64) {
        if (leadPad64 == 0) return nodes;
        int idx = 0;
        int acc = 0;
        for (int i = 0; i < nodes.size(); i++) {
            PhraseNode n = nodes.get(i);
            if (!isSilence(n)) break;
            acc += Bar.nodeSixtyFourths(n);
            idx = i + 1;
            if (acc >= leadPad64) break;
        }
        return new ArrayList<>(nodes.subList(idx, nodes.size()));
    }

    /** Count of trailing whole-silence bars in a list. */
    private static int trailingSilenceBarCount(List<Bar> bars) {
        int count = 0;
        for (int i = bars.size() - 1; i >= 0; i--) {
            if (!isAllSilence(bars.get(i))) break;
            count++;
        }
        return count;
    }

    /** Count of leading whole-silence bars in a list. */
    private static int leadingSilenceBarCount(List<Bar> bars) {
        int count = 0;
        for (Bar bar : bars) {
            if (!isAllSilence(bar)) break;
            count++;
        }
        return count;
    }

    /** True iff every node in the bar is structural silence. */
    private static boolean isAllSilence(Bar bar) {
        for (PhraseNode n : bar.nodes()) {
            if (!isSilence(n)) return false;
        }
        return true;
    }
}
