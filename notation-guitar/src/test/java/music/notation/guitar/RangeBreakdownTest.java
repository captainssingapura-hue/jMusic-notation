package music.notation.guitar;

import music.notation.duration.Duration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RangeBreakdownTest {

    private static final Meter M = Meter.FOUR_FOUR;

    /** Quarter notes on E4 (open string 1), one per beat, over the given bars (1-based), with hand position h. */
    private static List<FingeredNote> quarters(int fromBar, int toBar, int hand) {
        var out = new ArrayList<FingeredNote>();
        Duration q = Duration.of(1, 4);
        for (int b = fromBar; b <= toBar; b++) {
            for (int beat = 0; beat < 4; beat++) {
                Duration at = M.startOfBar(b - 1).plus(q.times(beat));
                out.add(new FingeredNote(at, q, 64, new FretPosition(1, 0), hand, b, beat + 1, false));
            }
        }
        return out;
    }

    @Test
    void byBar() {
        var f = new Fingering(quarters(1, 4, 5), 0);
        var ranges = RangeBreakdown.split(f, new BarRange(1, 4), M, new BreakdownSpec(BreakdownSpec.Mode.BAR, 4, 1));
        assertEquals(4, ranges.size());
        for (int i = 0; i < 4; i++) {
            assertEquals(i + 1, ranges.get(i).index());
            assertEquals(new BarRange(i + 1, i + 1), ranges.get(i).bars());
            assertEquals(4, ranges.get(i).notes().size());
            assertEquals(1.0, ranges.get(i).notesPerBeat());
        }
    }

    @Test
    void byPhrase_splitsAtSilencesAndCapsAtMaxBars() {
        // Bars 1–3 continuous, bar 4 silent, bars 5–6 continuous: two phrases; cap 2 → 3 ranges.
        var notes = new ArrayList<>(quarters(1, 3, 5));
        notes.addAll(quarters(5, 6, 9));
        var f = new Fingering(notes, 0);
        var ranges = RangeBreakdown.split(f, new BarRange(1, 6), M, new BreakdownSpec(BreakdownSpec.Mode.PHRASE, 2, 1.0));
        assertEquals(List.of(new BarRange(1, 2), new BarRange(3, 3), new BarRange(5, 6)),
                ranges.stream().map(PracticeRange::bars).toList());
        assertEquals(0, ranges.get(2).fretLow(), "open strings");
        assertEquals(0, ranges.get(2).shifts());
        assertEquals(3, ranges.get(2).index());
    }

    @Test
    void byPosition_splitsWhereTheHandMoves() {
        var notes = new ArrayList<>(quarters(1, 2, 5));
        notes.addAll(quarters(3, 4, 10));
        var f = new Fingering(notes, 0);
        var ranges = RangeBreakdown.split(f, new BarRange(1, 4), M, new BreakdownSpec(BreakdownSpec.Mode.POSITION, 8, 1.0));
        assertEquals(List.of(new BarRange(1, 2), new BarRange(3, 4)), ranges.stream().map(PracticeRange::bars).toList());
    }

    @Test
    void silentBarsAreLeftOut() {
        var f = new Fingering(quarters(3, 3, 5), 0);
        var ranges = RangeBreakdown.split(f, new BarRange(1, 5), M, new BreakdownSpec(BreakdownSpec.Mode.BAR, 1, 1.0));
        assertEquals(1, ranges.size());
        assertEquals(new BarRange(3, 3), ranges.get(0).bars());
    }

    @Test
    void summaryReadsLikeAPracticeNote() {
        var r = RangeBreakdown.split(new Fingering(quarters(7, 8, 5), 0), new BarRange(7, 8), M, BreakdownSpec.DEFAULT).get(0);
        String s = r.summary();
        assertTrue(s.contains("bars 7–8"), s);
        assertTrue(s.contains("8 notes"), s);
        assertTrue(s.contains("0 shifts"), s);
    }
}
