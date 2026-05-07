package music.notation.performance;

import music.notation.performance.OnsetGrouper.GroupedEvent;
import music.notation.performance.OverlapVoiceSplitter.Config;
import music.notation.performance.OverlapVoiceSplitter.SplitResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OverlapVoiceSplitterTest {

    private static GroupedEvent ev(long onset, long dur, int... pitches) {
        var list = new java.util.ArrayList<Integer>();
        for (int p : pitches) list.add(p);
        return new GroupedEvent(onset, dur, list);
    }

    @Test
    void emptyInput_yieldsNoVoices() {
        SplitResult r = OverlapVoiceSplitter.split(List.of());
        assertEquals(0, r.size());
    }

    @Test
    void monophonicInput_oneVoice() {
        // 4 quarter-notes back-to-back, none overlapping.
        var events = List.of(
                ev(0,   250, 60),
                ev(250, 250, 62),
                ev(500, 250, 64),
                ev(750, 250, 65));
        SplitResult r = OverlapVoiceSplitter.split(events);
        assertEquals(1, r.size());
        assertEquals(4, r.voices().get(0).size());
    }

    @Test
    void padPlusMelody_twoVoices() {
        // Whole-note pad (1000 ms) + 4 eighths above (each 250 ms).
        var events = List.of(
                ev(0,   1000, 60, 64),       // pad: C4-E4 chord
                ev(0,   250,  72),           // melody: C5
                ev(250, 250,  74),
                ev(500, 250,  76),
                ev(750, 250,  77));
        SplitResult r = OverlapVoiceSplitter.split(events);
        assertEquals(2, r.size(), "pad + melody should split into 2 voices");
        // Voice 0 should be the higher one (melody).
        assertTrue(r.voices().get(0).get(0).highestPitch() > 70,
                "voice 0 should be the melody (higher mean pitch)");
        assertEquals(4, r.voices().get(0).size(), "melody = 4 events");
        assertEquals(1, r.voices().get(1).size(), "pad = 1 event");
    }

    @Test
    void threeWayCounterpoint_threeVoices() {
        // Three monophonic streams interleaved.
        var events = List.of(
                ev(0,   500, 72),    // soprano
                ev(0,   500, 64),    // alto
                ev(0,   500, 48),    // bass
                ev(500, 500, 74),
                ev(500, 500, 65),
                ev(500, 500, 50));
        SplitResult r = OverlapVoiceSplitter.split(events);
        assertEquals(3, r.size());
        // Mean pitch ordering: soprano > alto > bass.
        assertTrue(r.voices().get(0).get(0).highestPitch() >= 72);
        assertTrue(r.voices().get(2).get(0).highestPitch() <  60);
    }

    @Test
    void voiceOrdering_descendingMeanPitch() {
        var events = List.of(
                ev(0, 400, 50),
                ev(0, 400, 80));
        SplitResult r = OverlapVoiceSplitter.split(events);
        assertTrue(r.voices().get(0).get(0).highestPitch()
                > r.voices().get(1).get(0).highestPitch());
    }

    @Test
    void maxVoicesCap_forcesNearest() {
        var cfg = new Config(2, 1.0, 16.0);
        // Three simultaneous notes with cap = 2.
        var events = List.of(
                ev(0, 500, 80),
                ev(0, 500, 60),
                ev(0, 500, 40));
        SplitResult r = OverlapVoiceSplitter.split(events, cfg);
        assertEquals(2, r.size());
        // Total events preserved (no drops).
        int total = r.voices().stream().mapToInt(List::size).sum();
        assertEquals(3, total);
    }

    @Test
    void backToBackNotes_stayInOneVoice() {
        // First note ends exactly when second begins — no overlap.
        var events = List.of(
                ev(0,   250, 60),
                ev(250, 250, 62));
        SplitResult r = OverlapVoiceSplitter.split(events);
        assertEquals(1, r.size());
    }

    @Test
    void overlappingOnsets_butNoChord_treatedAsSeparateVoices() {
        // Same onset, different duration → Tier 0 wouldn't have coalesced.
        // Tier 2 sees them as overlapping (one ends after the other starts).
        var events = List.of(
                ev(0, 1000, 60),
                ev(0, 250,  72));
        SplitResult r = OverlapVoiceSplitter.split(events);
        assertEquals(2, r.size());
    }

    @Test
    void unsortedInput_handledByDefensiveSort() {
        var events = List.of(
                ev(500, 250, 64),
                ev(0,   250, 60),
                ev(250, 250, 62));
        SplitResult r = OverlapVoiceSplitter.split(events);
        assertEquals(1, r.size());
        // Output should still be in onset order.
        var v = r.voices().get(0);
        assertEquals(0,   v.get(0).onsetMs());
        assertEquals(250, v.get(1).onsetMs());
        assertEquals(500, v.get(2).onsetMs());
    }

    @Test
    void config_validatesArguments() {
        assertThrows(IllegalArgumentException.class, () -> new Config(0, 1.0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new Config(1, -1, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new Config(1, 1.0, -1));
    }
}
