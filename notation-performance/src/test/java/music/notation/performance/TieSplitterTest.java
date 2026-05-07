package music.notation.performance;

import music.notation.performance.TieSplitter.Segment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TieSplitterTest {

    private static final int BAR_4_4 = 64;   // 4/4 bar
    private static final int BAR_3_4 = 48;   // 3/4 bar

    @Test
    void noteFitsWithinBar_singleSegment_noTies() {
        // QUARTER (16sf) starting at position 0 in a 4/4 bar.
        var segs = TieSplitter.split(16, 0, 0, BAR_4_4);
        assertEquals(List.of(new Segment(16, 0)), segs);
        assertEquals(List.of(false), TieSplitter.tieFlags(segs));
    }

    @Test
    void noteOverflowsBarBoundary_twoTiedSegments() {
        // HALF (32sf) starting halfway through bar 0 (position 48) — only
        // 16sf remain in bar 0; the rest spills into bar 1.
        var segs = TieSplitter.split(32, 48, 0, BAR_4_4);
        assertEquals(List.of(
                new Segment(16, 0),     // QUARTER, fills bar 0
                new Segment(16, 1)),    // QUARTER, starts bar 1
                segs);
        assertEquals(List.of(true, false), TieSplitter.tieFlags(segs));
    }

    @Test
    void noteSpansMultipleBars_chainOfWholes() {
        // 3 whole bars (192sf in 4/4) starting at bar 0 position 0.
        var segs = TieSplitter.split(192, 0, 0, BAR_4_4);
        assertEquals(List.of(
                new Segment(64, 0),    // WHOLE in bar 0
                new Segment(64, 1),    // WHOLE in bar 1
                new Segment(64, 2)),   // WHOLE in bar 2
                segs);
        assertEquals(List.of(true, true, false), TieSplitter.tieFlags(segs));
    }

    @Test
    void illegalDuration_decomposedGreedily() {
        // 11sf isn't in the legal set. Greedy: 8 (EIGHTH) + 3 (THIRTY_SECOND.dot()).
        var segs = TieSplitter.split(11, 0, 0, BAR_4_4);
        assertEquals(List.of(
                new Segment(8, 0),
                new Segment(3, 0)),
                segs);
        assertEquals(List.of(true, false), TieSplitter.tieFlags(segs));
    }

    @Test
    void illegalDuration_threePartGreedy() {
        // 7sf: greedy = 6 (SIXTEENTH.dot()) + 1 (SIXTY_FOURTH).
        var segs = TieSplitter.split(7, 0, 0, BAR_4_4);
        assertEquals(List.of(new Segment(6, 0), new Segment(1, 0)), segs);
    }

    @Test
    void overflowAndIllegal_combinedDecomposition() {
        // 20sf starting at position 56 in a 4/4 bar — 8sf left in bar 0,
        // 12sf spills into bar 1.
        var segs = TieSplitter.split(20, 56, 0, BAR_4_4);
        assertEquals(List.of(
                new Segment(8,  0),    // EIGHTH fills bar 0
                new Segment(12, 1)),   // EIGHTH.dot() in bar 1
                segs);
    }

    @Test
    void wholeNoteAtPositionZero_singleSegment() {
        var segs = TieSplitter.split(64, 0, 0, BAR_4_4);
        assertEquals(List.of(new Segment(64, 0)), segs);
    }

    @Test
    void barSizesOtherThan4_4_supported() {
        // 3/4 bar = 48sf. A QUARTER at position 32 only has 16sf left; HALF spills.
        var segs = TieSplitter.split(32, 32, 5, BAR_3_4);
        assertEquals(List.of(
                new Segment(16, 5),
                new Segment(16, 6)),
                segs);
    }

    @Test
    void startBarIndexNonZero_propagates() {
        var segs = TieSplitter.split(96, 0, 7, BAR_4_4);
        assertEquals(7, segs.get(0).barIndex());
        assertEquals(8, segs.get(1).barIndex());
    }

    @Test
    void invalidInputs_throw() {
        assertThrows(IllegalArgumentException.class,
                () -> TieSplitter.split(0, 0, 0, BAR_4_4));
        assertThrows(IllegalArgumentException.class,
                () -> TieSplitter.split(16, -1, 0, BAR_4_4));
        assertThrows(IllegalArgumentException.class,
                () -> TieSplitter.split(16, 64, 0, BAR_4_4));
        assertThrows(IllegalArgumentException.class,
                () -> TieSplitter.split(16, 0, 0, 0));
    }

    @Test
    void durationsSumToTotal_invariant() {
        // Property: across many shapes, segment durations must sum to total.
        int[][] cases = {{1, 0}, {7, 5}, {96, 50}, {200, 0}, {17, 47}};
        for (int[] c : cases) {
            int total = c[0], pos = c[1];
            int sum = TieSplitter.split(total, pos, 0, BAR_4_4).stream()
                    .mapToInt(Segment::durationSf).sum();
            assertEquals(total, sum, "total=" + total + " pos=" + pos);
        }
    }

    @Test
    void segmentRecord_rejectsIllegalDuration() {
        // 11sf is not legal; Segment must reject it directly.
        assertThrows(IllegalArgumentException.class,
                () -> new Segment(11, 0));
    }
}
