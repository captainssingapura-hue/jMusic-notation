package music.notation.performance;

import music.notation.duration.Duration;
import music.notation.performance.OnsetGrouper.GroupedEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OnsetGrouperTest {

    /**
     * Musical position for a 120 bpm ms literal (whole note = 2000 ms):
     * 250 → 1/8, 480 → 6/25, 8 → 1/250. Keeps the original fixtures'
     * relative spacing exact under the Duration model.
     */
    private static Duration ms(long ms) { return Duration.of(ms, 2000); }

    @Test
    void emptyInput_returnsEmptyList() {
        assertTrue(OnsetGrouper.group(List.of()).isEmpty());
    }

    @Test
    void singleNote_becomesOneEvent() {
        var notes = List.of(new PitchedNote(ms(0), ms(250), 60));
        var events = OnsetGrouper.group(notes);
        assertEquals(1, events.size());
        assertEquals(List.of(60), events.get(0).pitches());
    }

    @Test
    void threeSameOnsetSameDuration_coalesceIntoChord() {
        var notes = List.of(
                new PitchedNote(ms(480), ms(250), 67),
                new PitchedNote(ms(480), ms(250), 60),
                new PitchedNote(ms(480), ms(250), 64));
        var events = OnsetGrouper.group(notes);
        assertEquals(1, events.size());
        assertEquals(List.of(60, 64, 67), events.get(0).pitches(),
                "pitches should be sorted ascending");
    }

    @Test
    void differentDurations_areNotCoalesced() {
        var notes = List.of(
                new PitchedNote(ms(480), ms(1000), 60),
                new PitchedNote(ms(480), ms(250), 64));
        var events = OnsetGrouper.group(notes);
        assertEquals(2, events.size(), "different duration ⇒ separate voices, not a chord");
    }

    @Test
    void differentOnsets_areNotCoalesced() {
        var notes = List.of(
                new PitchedNote(ms(0), ms(250), 60),
                new PitchedNote(ms(250), ms(250), 64));
        var events = OnsetGrouper.group(notes);
        assertEquals(2, events.size());
    }

    @Test
    void inputOrderIndependent_resultIsDeterministic() {
        var a = OnsetGrouper.group(List.of(
                new PitchedNote(ms(480), ms(250), 67),
                new PitchedNote(ms(480), ms(250), 60),
                new PitchedNote(ms(480), ms(250), 64)));
        var b = OnsetGrouper.group(List.of(
                new PitchedNote(ms(480), ms(250), 60),
                new PitchedNote(ms(480), ms(250), 64),
                new PitchedNote(ms(480), ms(250), 67)));
        assertEquals(a, b);
    }

    @Test
    void jitterTolerance_groupsNearSimultaneousNotes() {
        // Two notes 8ms apart with 5ms duration jitter — within a 10ms tolerance
        // (all expressed as musical positions at 120 bpm).
        var notes = List.of(
                new PitchedNote(ms(480), ms(250), 60),
                new PitchedNote(ms(488), ms(245), 64));
        var withoutJitter = OnsetGrouper.group(notes, Duration.zero());
        assertEquals(2, withoutJitter.size(), "exact match required without jitter");

        var withJitter = OnsetGrouper.group(notes, ms(10));
        assertEquals(1, withJitter.size(), "10ms tolerance should coalesce");
        assertEquals(List.of(60, 64), withJitter.get(0).pitches());
    }

    @Test
    void resultsSortedByOnset() {
        var notes = List.of(
                new PitchedNote(ms(960), ms(100), 60),
                new PitchedNote(ms(0), ms(100), 64),
                new PitchedNote(ms(480), ms(100), 67));
        var events = OnsetGrouper.group(notes);
        assertTrue(ms(0).equalsDuration(events.get(0).at()));
        assertTrue(ms(480).equalsDuration(events.get(1).at()));
        assertTrue(ms(960).equalsDuration(events.get(2).at()));
    }

    @Test
    void groupedEvent_pitchAccessors() {
        var ev = new GroupedEvent(Duration.zero(), ms(100), List.of(60, 64, 67));
        assertEquals(60, ev.lowestPitch());
        assertEquals(67, ev.highestPitch());
        // Centroid: (60 + 64 + 67) / 3 = 63.666...
        assertEquals(63.666, ev.centroid(), 0.01);
    }

    @Test
    void groupedEvent_rejectsEmptyPitches() {
        assertThrows(IllegalArgumentException.class,
                () -> new GroupedEvent(Duration.zero(), ms(100), List.of()));
    }
}
