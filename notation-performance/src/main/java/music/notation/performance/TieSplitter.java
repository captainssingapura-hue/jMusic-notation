package music.notation.performance;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a duration that may overflow the current bar — or that may
 * not be a single legal notation value — into a chain of legal
 * sub-durations whose sum equals the original. The caller wires
 * adjacent sub-durations with {@code tiedToNext = true} on the
 * resulting {@code PitchNode} / {@code PolyPitchNode}, so playback
 * re-merges the chain into one continuous note.
 *
 * <p>Handles two distinct concerns in a single pass:</p>
 * <ol>
 *   <li><b>Bar overflow</b> — a note longer than the bar's remaining
 *       capacity is clipped at the bar boundary, then resumed in the
 *       next bar (and so on, for notes spanning many bars).</li>
 *   <li><b>Illegal lengths</b> — a value like 11 sf isn't representable
 *       directly; it's emitted greedily as the largest-fitting legal
 *       value followed by the remainder (8 + 2 + 1 in this case).</li>
 * </ol>
 *
 * <p>Stateless and thread-safe.</p>
 *
 * <p>See {@code .docs/voice-separation/} README for the place this
 * sits in the import pipeline.</p>
 */
public final class TieSplitter {

    private TieSplitter() {}

    /** A segment of the split: a legal duration plus its bar index. */
    public record Segment(int durationSf, int barIndex) {
        public Segment {
            if (durationSf <= 0) throw new IllegalArgumentException("durationSf must be > 0");
            if (barIndex   <  0) throw new IllegalArgumentException("barIndex must be >= 0");
            if (!Quantizer.isLegal(durationSf)) {
                throw new IllegalArgumentException(
                        "durationSf " + durationSf + " is not a legal Quantizer value");
            }
        }
    }

    /**
     * Split a note's total duration into legal segments that respect
     * bar boundaries.
     *
     * @param totalSf         note's full duration in sixty-fourths.
     * @param positionInBarSf where the note starts within its
     *                        starting bar, in sixty-fourths
     *                        (0 ≤ positionInBarSf &lt; barSf).
     * @param startBarIndex   bar index of the starting bar (0-based).
     * @param barSf           bar size in sixty-fourths.
     * @return ordered list of segments; durations sum to {@code totalSf};
     *         {@code segment[i].barIndex()} monotonically non-decreasing.
     *         All but the last segment should carry {@code tiedToNext = true}
     *         when materialised as {@code PitchNode}s.
     */
    public static List<Segment> split(int totalSf, int positionInBarSf,
                                      int startBarIndex, int barSf) {
        if (totalSf <= 0)
            throw new IllegalArgumentException("totalSf must be > 0: " + totalSf);
        if (barSf <= 0)
            throw new IllegalArgumentException("barSf must be > 0: " + barSf);
        if (positionInBarSf < 0 || positionInBarSf >= barSf)
            throw new IllegalArgumentException(
                    "positionInBarSf must be in [0," + barSf + "): " + positionInBarSf);

        var out = new ArrayList<Segment>();
        int remainingNote = totalSf;
        int barIndex      = startBarIndex;
        int posInBar      = positionInBarSf;

        while (remainingNote > 0) {
            int barCapacity = barSf - posInBar;
            int fitInBar    = Math.min(remainingNote, barCapacity);
            // Decompose `fitInBar` into one or more legal Quantizer values.
            int chunkLeft = fitInBar;
            while (chunkLeft > 0) {
                Integer legal = Quantizer.floor(chunkLeft);
                if (legal == null) {
                    // Should be unreachable for any chunkLeft >= 1 given
                    // SIXTY_FOURTH (1) is in the legal set.
                    throw new IllegalStateException(
                            "No legal duration ≤ " + chunkLeft + "sf");
                }
                out.add(new Segment(legal, barIndex));
                chunkLeft     -= legal;
                remainingNote -= legal;
            }
            // Advance to next bar if the note continues.
            if (remainingNote > 0) {
                barIndex++;
                posInBar = 0;
            }
        }
        return out;
    }

    /**
     * Convenience: the boolean tie-flag for each segment in a split.
     * Same length as {@code segments}; every entry is {@code true}
     * except the last.
     */
    public static List<Boolean> tieFlags(List<Segment> segments) {
        var flags = new ArrayList<Boolean>(segments.size());
        for (int i = 0; i < segments.size(); i++) {
            flags.add(i < segments.size() - 1);
        }
        return flags;
    }
}
