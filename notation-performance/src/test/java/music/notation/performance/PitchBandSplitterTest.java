package music.notation.performance;

import music.notation.performance.OnsetGrouper.GroupedEvent;
import music.notation.performance.PitchBandSplitter.SplitResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PitchBandSplitterTest {

    private static GroupedEvent ev(long onset, long dur, int... pitches) {
        var list = new java.util.ArrayList<Integer>();
        for (int p : pitches) list.add(p);
        return new GroupedEvent(onset, dur, list);
    }

    @Test
    void emptyInput_yieldsTwoEmptyBands() {
        SplitResult r = PitchBandSplitter.split(List.of());
        assertFalse(r.hasHigh());
        assertFalse(r.hasLow());
    }

    @Test
    void allHighRegister_goesToHigh() {
        var events = List.of(ev(0, 100, 64), ev(100, 100, 67), ev(200, 100, 72));
        SplitResult r = PitchBandSplitter.split(events);
        assertEquals(3, r.high().size());
        assertEquals(0, r.low().size());
    }

    @Test
    void allLowRegister_goesToLow() {
        var events = List.of(ev(0, 100, 36), ev(100, 100, 48), ev(200, 100, 55));
        SplitResult r = PitchBandSplitter.split(events);
        assertEquals(0, r.high().size());
        assertEquals(3, r.low().size());
    }

    @Test
    void cutoffIsInclusiveOnTheHighSide() {
        // Default cutoff = 60. A note exactly at pitch 60 goes to 'high'.
        var r = PitchBandSplitter.split(List.of(ev(0, 100, 60)));
        assertEquals(1, r.high().size());
        assertEquals(0, r.low().size());
    }

    @Test
    void straddlingChord_splitsIntoTwoEvents() {
        // C-major voicing C3-G3-C4-E4 (48,55,60,64) with cutoff 60:
        //   high = [60, 64]; low = [48, 55].
        var ch = ev(480, 250, 48, 55, 60, 64);
        var r = PitchBandSplitter.split(List.of(ch));
        assertEquals(1, r.high().size());
        assertEquals(1, r.low().size());
        assertEquals(List.of(60, 64), r.high().get(0).pitches());
        assertEquals(List.of(48, 55), r.low().get(0).pitches());
        // Onset and duration preserved on both sides.
        assertEquals(480, r.high().get(0).onsetMs());
        assertEquals(250, r.high().get(0).durationMs());
        assertEquals(480, r.low().get(0).onsetMs());
        assertEquals(250, r.low().get(0).durationMs());
    }

    @Test
    void chordEntirelyAboveCutoff_notSplit() {
        var ch = ev(0, 100, 64, 67, 72);
        var r = PitchBandSplitter.split(List.of(ch));
        assertEquals(1, r.high().size());
        assertEquals(0, r.low().size());
        assertEquals(List.of(64, 67, 72), r.high().get(0).pitches());
    }

    @Test
    void customCutoff_overridesDefault() {
        // Cutoff = 65. Pitches 60 and 64 now fall in 'low'; 65 and 67 in 'high'.
        var ch = ev(0, 100, 60, 64, 65, 67);
        var r = PitchBandSplitter.split(List.of(ch), 65);
        assertEquals(List.of(65, 67), r.high().get(0).pitches());
        assertEquals(List.of(60, 64), r.low() .get(0).pitches());
    }

    @Test
    void mixedSequence_preservesRelativeOrderInEachBand() {
        var events = List.of(
                ev(0,   100, 70),                // high
                ev(100, 100, 50),                // low
                ev(200, 100, 60, 65),            // both high
                ev(300, 100, 48, 64));           // straddle
        var r = PitchBandSplitter.split(events);
        // High order: 70, 60-65 chord, 64 (from straddle)
        assertEquals(70,  r.high().get(0).highestPitch());
        assertEquals(200, r.high().get(1).onsetMs());
        assertEquals(300, r.high().get(2).onsetMs());
        // Low order: 50, 48 (from straddle)
        assertEquals(50,  r.low().get(0).highestPitch());
        assertEquals(48,  r.low().get(1).highestPitch());
    }

    @Test
    void straddlingChordWithOnePitchEachSide_emitsTwoSinglePitchEvents() {
        var ch = ev(0, 100, 59, 60);
        var r = PitchBandSplitter.split(List.of(ch));
        assertEquals(List.of(60), r.high().get(0).pitches());
        assertEquals(List.of(59), r.low().get(0).pitches());
    }
}
