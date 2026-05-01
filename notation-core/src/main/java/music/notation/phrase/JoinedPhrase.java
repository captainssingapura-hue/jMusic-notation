package music.notation.phrase;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Composite {@link Phrase} — joins multiple child phrases with a
 * {@link ConnectingMode}. {@link #bars()} materialises the configured
 * stitch into a flat bar list; {@link #auxBars()} replays the same
 * structural decisions on each named aux voice so aux always stays in
 * lock-step with primary.
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
 *     first bar of {@code child[i+1]} ({@code first}). If the trailing
 *     pad of {@code last} plus the leading pad of {@code first} fills
 *     the bar, collapse them into a single merged bar laid out as
 *     {@code [audible_last] [middle_gap] [audible_first]}. The merged
 *     bar replaces {@code last}; {@code first} is consumed.</li>
 *   <li><b>Stage 2 — whole-bar trim.</b> After Stage 1: drop
 *     {@code min(t, l)} whole silent bars from the front of
 *     {@code child[i+1]}.</li>
 * </ol>
 *
 * <h2>Aux composition</h2>
 * Joining is decided at the primary level — aux voices follow. For each
 * boundary, primary records a {@link StitchPlan} {@code (merged?,
 * rightHeadDrop)}. Each named aux voice replays that plan: if primary
 * merged, aux merges its own pair via {@link #mergeAuxBars}; aux always
 * drops the same number of bars from {@code right}'s head as primary
 * did. Result: aux bar lists have the same length as primary.
 */
public record JoinedPhrase(String name, List<Phrase> children, ConnectingMode mode)
        implements Phrase {

    public JoinedPhrase {
        Objects.requireNonNull(name, "name must not be null (use \"\" for anonymous)");
        Objects.requireNonNull(mode, "mode must not be null");
        children = List.copyOf(children);
    }

    @Override
    public List<Bar> bars() {
        return composeWithPlans().bars;
    }

    @Override
    public Map<String, List<Bar>> auxBars() {
        if (children.isEmpty()) return Map.of();

        // Collect voice names appearing in any child.
        Set<String> names = new LinkedHashSet<>();
        for (Phrase c : children) names.addAll(c.auxBars().keySet());
        if (names.isEmpty()) return Map.of();

        var primary = composeWithPlans();
        var out = new LinkedHashMap<String, List<Bar>>();
        for (String v : names) {
            List<Bar> acc = new ArrayList<>(auxOrSilent(children.get(0), v));
            for (int i = 1; i < children.size(); i++) {
                List<Bar> next = new ArrayList<>(auxOrSilent(children.get(i), v));
                applyPlan(acc, next, primary.plans.get(i - 1));
            }
            out.put(v, List.copyOf(acc));
        }
        return Map.copyOf(out);
    }

    // ── Single-pass primary composer that captures per-boundary plans ──

    private record Composed(List<Bar> bars, List<StitchPlan> plans) {}
    private record StitchPlan(boolean merged, int rightHeadDrop) {}

    private Composed composeWithPlans() {
        if (children.isEmpty()) return new Composed(List.of(), List.of());
        List<Bar> acc = new ArrayList<>(children.get(0).bars());
        List<StitchPlan> plans = new ArrayList<>();
        for (int i = 1; i < children.size(); i++) {
            List<Bar> next = new ArrayList<>(children.get(i).bars());
            StitchPlan plan = switch (mode) {
                case ATTACCA -> new StitchPlan(false, 0);
                case ELIDED  -> elideStitch(acc, next);
            };
            plans.add(plan);
            acc.addAll(next);  // ATTACCA: full next; ELIDED: residual after drops.
        }
        return new Composed(List.copyOf(acc), List.copyOf(plans));
    }

    /**
     * In-place ELIDED stitch: mutates {@code acc} (replacing its last
     * bar with the merged bar when absorption fires) and {@code next}
     * (removing its head by stage-1 absorption + stage-2 silence trim).
     * Returns the plan executed so aux can replay it.
     */
    private static StitchPlan elideStitch(List<Bar> acc, List<Bar> next) {
        if (acc.isEmpty() || next.isEmpty()) return new StitchPlan(false, 0);

        Bar last = acc.get(acc.size() - 1);
        Bar first = next.get(0);
        int barSize = last.expectedSixtyFourths();
        int trailPad64 = trailingPadSixtyFourths(last);
        int leadPad64  = leadingPadSixtyFourths(first);

        boolean merged = false;
        int drop = 0;

        if (trailPad64 > 0 && leadPad64 > 0
                && first.expectedSixtyFourths() == barSize
                && trailPad64 + leadPad64 >= barSize) {
            int audibleFirst64 = barSize - leadPad64;
            if (audibleFirst64 > 0) {
                Bar mergedBar = mergeAbsorption(last, first, trailPad64, leadPad64);
                acc.set(acc.size() - 1, mergedBar);
                merged = true;
            }
            next.remove(0);
            drop = 1;
        }

        int t = trailingSilenceBarCount(acc);
        int l = leadingSilenceBarCount(next);
        int trim = Math.min(t, l);
        for (int k = 0; k < trim; k++) next.remove(0);
        drop += trim;

        return new StitchPlan(merged, drop);
    }

    // ── Apply primary plan to one aux voice ────────────────────────────

    private static void applyPlan(List<Bar> acc, List<Bar> next, StitchPlan plan) {
        if (plan.merged() && !acc.isEmpty() && !next.isEmpty()) {
            Bar mergedAux = mergeAuxBars(acc.get(acc.size() - 1), next.get(0));
            acc.set(acc.size() - 1, mergedAux);
        }
        int drop = Math.min(plan.rightHeadDrop(), next.size());
        for (int k = 0; k < drop; k++) next.remove(0);
        acc.addAll(next);
    }

    /**
     * Best-effort aux-bar merge: silent sides yield trivially; both
     * audible falls back to {@link #mergeAbsorption} when pads allow,
     * else right wins. Author should structure aux so audible content
     * doesn't collide at elision boundaries; this fallback keeps the
     * length contract.
     */
    private static Bar mergeAuxBars(Bar left, Bar right) {
        boolean leftSilent = isAllSilence(left);
        boolean rightSilent = isAllSilence(right);
        int barSize = left.expectedSixtyFourths();
        if (leftSilent && rightSilent) return Bar.silent(barSize);
        if (leftSilent)  return right;
        if (rightSilent) return left;
        int trailPad = trailingPadSixtyFourths(left);
        int leadPad  = leadingPadSixtyFourths(right);
        if (trailPad > 0 && leadPad > 0
                && right.expectedSixtyFourths() == barSize
                && trailPad + leadPad >= barSize) {
            return mergeAbsorption(left, right, trailPad, leadPad);
        }
        return right;
    }

    private static List<Bar> auxOrSilent(Phrase child, String voice) {
        List<Bar> aux = child.auxBars().get(voice);
        if (aux != null) return aux;
        var primary = child.bars();
        var out = new ArrayList<Bar>(primary.size());
        for (Bar b : primary) out.add(Bar.silent(b.expectedSixtyFourths()));
        return out;
    }

    /**
     * Build a merged bar with the pickup audible at the END of the
     * bar so bar 2 of the pickup phrase follows immediately. Layout:
     * <pre>
     *   [audible_last] [middle_gap] [audible_first]
     * </pre>
     * where {@code middle_gap = bar - audibleLast - audibleFirst}.
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
        return new Bar(barSize, merged);
    }

    // ── Bar inspection helpers ───────────────────────────────────────

    private static boolean isSilence(PhraseNode node) {
        return node instanceof PaddingNode || node instanceof RestNode;
    }

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

    static int leadingPadSixtyFourths(Bar bar) {
        int total = 0;
        for (PhraseNode n : bar.nodes()) {
            if (!isSilence(n)) break;
            total += Bar.nodeSixtyFourths(n);
        }
        return total;
    }

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

    private static int trailingSilenceBarCount(List<Bar> bars) {
        int count = 0;
        for (int i = bars.size() - 1; i >= 0; i--) {
            if (!isAllSilence(bars.get(i))) break;
            count++;
        }
        return count;
    }

    private static int leadingSilenceBarCount(List<Bar> bars) {
        int count = 0;
        for (Bar bar : bars) {
            if (!isAllSilence(bar)) break;
            count++;
        }
        return count;
    }

    private static boolean isAllSilence(Bar bar) {
        for (PhraseNode n : bar.nodes()) {
            if (!isSilence(n)) return false;
        }
        return true;
    }
}
