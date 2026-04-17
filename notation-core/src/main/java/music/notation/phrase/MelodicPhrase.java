package music.notation.phrase;

import music.notation.duration.Duration;
import music.notation.structure.TimeSignature;

import java.util.ArrayList;
import java.util.List;

public record MelodicPhrase(List<PhraseNode> nodes, List<Bar> bars, PhraseMarking marking) implements Phrase {
    public MelodicPhrase {
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("MelodicPhrase must contain at least one node");
        }
        nodes = List.copyOf(nodes);
        bars = List.copyOf(bars);
    }

    /** Backwards-compatible constructor for phrases built without bar structure. */
    public MelodicPhrase(List<PhraseNode> nodes, PhraseMarking marking) {
        this(nodes, List.of(), marking);
    }

    /**
     * Build a phrase from validated bars. Each {@link Bar} has already verified
     * its own duration at construction time. This method additionally checks that
     * middle bars match the time signature (first/last may be partial pickups).
     *
     * <p>After flattening bars into a node list, slur regions are resolved:
     * <ul>
     *   <li><b>Same-pitch tie</b> — consecutive same-pitch notes bridged by
     *       {@link SlurStart}/{@link SlurEnd} are merged into a single
     *       {@link NoteNode} with combined duration.</li>
     *   <li><b>Different-pitch slur</b> — the markers are preserved so the
     *       playback layer can apply legato overlap.</li>
     * </ul>
     */
    public static MelodicPhrase fromBars(TimeSignature ts, PhraseMarking marking, Bar... bars) {
        int expected = ts.barSixtyFourths();
        for (int i = 0; i < bars.length; i++) {
            int actual = bars[i].expectedSixtyFourths();
            if (actual != expected) {
                boolean isFirstOrLast = (i == 0 || i == bars.length - 1);
                if (isFirstOrLast && actual < expected) {
                    continue;
                }
                throw new IllegalArgumentException(
                        "Bar " + (i + 1) + "/" + bars.length + ": expected " + expected
                                + " sixty-fourths (" + ts.beats() + "/" + ts.beatValue()
                                + ") but got " + actual);
            }
        }
        var flat = new ArrayList<PhraseNode>();
        for (Bar bar : bars) {
            flat.addAll(bar.nodes());
        }
        return new MelodicPhrase(resolveSlurs(flat), List.of(bars), marking);
    }

    // ── Slur / tie resolution ──────────────────────────────────────────────

    /**
     * Walk the flat node list and resolve slur regions.
     *
     * <p>Pattern: {@code NoteNode(P,D1), SlurStart, NoteNode(P,D2), SlurEnd}
     * with the same pitch P → merged into {@code NoteNode(P, D1+D2)}.
     * The merge chains: if the merged note is followed by another
     * SlurStart+same-pitch, it keeps merging.</p>
     *
     * <p>If the pitches differ, the SlurStart/SlurEnd markers are kept for
     * the playback layer to interpret as legato.</p>
     */
    static List<PhraseNode> resolveSlurs(List<PhraseNode> nodes) {
        var result = new ArrayList<PhraseNode>(nodes.size());

        for (int i = 0; i < nodes.size(); ) {
            // Look for pattern: NoteNode, SlurStart, ..., NoteNode, SlurEnd
            if (i + 3 < nodes.size()
                    && nodes.get(i) instanceof NoteNode before
                    && nodes.get(i + 1) instanceof SlurStart) {

                // Skip zero-duration markers between SlurStart and the next NoteNode
                int j = i + 2;
                while (j < nodes.size() && isZeroDurationMarker(nodes.get(j))
                        && !(nodes.get(j) instanceof SlurEnd)) {
                    j++;
                }

                if (j < nodes.size() && nodes.get(j) instanceof NoteNode after
                        && j + 1 < nodes.size() && nodes.get(j + 1) instanceof SlurEnd
                        && before.pitches().equals(after.pitches())) {
                    // Same pitch(es) — merge into one sustained note
                    int combined = before.duration().sixtyFourths()
                            + after.duration().sixtyFourths();
                    var merged = NoteNode.poly(
                            Duration.ofSixtyFourths(combined), before.pitches());
                    // Replace "before" with merged, skip SlurStart..SlurEnd
                    result.add(merged);
                    i = j + 2; // past SlurEnd

                    // Chain: if another SlurStart+same-pitch follows, keep merging
                    while (i + 2 < nodes.size()
                            && nodes.get(i) instanceof SlurStart
                            && nodes.get(i + 1) instanceof NoteNode next
                            && nodes.get(i + 2) instanceof SlurEnd
                            && next.pitches().equals(merged.pitches())) {
                        int chainCombined = merged.duration().sixtyFourths()
                                + next.duration().sixtyFourths();
                        merged = NoteNode.poly(
                                Duration.ofSixtyFourths(chainCombined), merged.pitches());
                        result.set(result.size() - 1, merged);
                        i += 3;
                    }
                    continue;
                }
            }
            // No merge — emit node as-is
            result.add(nodes.get(i));
            i++;
        }
        return result;
    }

    private static boolean isZeroDurationMarker(PhraseNode node) {
        return node instanceof DynamicNode
                || node instanceof SlurStart
                || node instanceof SlurEnd;
    }
}
