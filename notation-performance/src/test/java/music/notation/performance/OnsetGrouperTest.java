package music.notation.performance;

import music.notation.performance.OnsetGrouper.GroupedEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OnsetGrouperTest {

    @Test
    void emptyInput_returnsEmptyList() {
        assertTrue(OnsetGrouper.group(List.of()).isEmpty());
    }

    @Test
    void singleNote_becomesOneEvent() {
        var notes = List.of(new PitchedNote(0, 250, 60));
        var events = OnsetGrouper.group(notes);
        assertEquals(1, events.size());
        assertEquals(List.of(60), events.get(0).pitches());
    }

    @Test
    void threeSameOnsetSameDuration_coalesceIntoChord() {
        var notes = List.of(
                new PitchedNote(480, 250, 67),
                new PitchedNote(480, 250, 60),
                new PitchedNote(480, 250, 64));
        var events = OnsetGrouper.group(notes);
        assertEquals(1, events.size());
        assertEquals(List.of(60, 64, 67), events.get(0).pitches(),
                "pitches should be sorted ascending");
    }

    @Test
    void differentDurations_areNotCoalesced() {
        var notes = List.of(
                new PitchedNote(480, 1000, 60),
                new PitchedNote(480, 250,  64));
        var events = OnsetGrouper.group(notes);
        assertEquals(2, events.size(), "different duration ⇒ separate voices, not a chord");
    }

    @Test
    void differentOnsets_areNotCoalesced() {
        var notes = List.of(
                new PitchedNote(0,   250, 60),
                new PitchedNote(250, 250, 64));
        var events = OnsetGrouper.group(notes);
        assertEquals(2, events.size());
    }

    @Test
    void inputOrderIndependent_resultIsDeterministic() {
        var a = OnsetGrouper.group(List.of(
                new PitchedNote(480, 250, 67),
                new PitchedNote(480, 250, 60),
                new PitchedNote(480, 250, 64)));
        var b = OnsetGrouper.group(List.of(
                new PitchedNote(480, 250, 60),
                new PitchedNote(480, 250, 64),
                new PitchedNote(480, 250, 67)));
        assertEquals(a, b);
    }

    @Test
    void jitterTolerance_groupsNearSimultaneousNotes() {
        // Two notes 8ms apart with 5ms duration jitter — within 10ms tolerance.
        var notes = List.of(
                new PitchedNote(480, 250, 60),
                new PitchedNote(488, 245, 64));
        var withoutJitter = OnsetGrouper.group(notes, 0);
        assertEquals(2, withoutJitter.size(), "exact match required without jitter");

        var withJitter = OnsetGrouper.group(notes, 10);
        assertEquals(1, withJitter.size(), "10ms tolerance should coalesce");
        assertEquals(List.of(60, 64), withJitter.get(0).pitches());
    }

    @Test
    void resultsSortedByOnset() {
        var notes = List.of(
                new PitchedNote(960, 100, 60),
                new PitchedNote(0,   100, 64),
                new PitchedNote(480, 100, 67));
        var events = OnsetGrouper.group(notes);
        assertEquals(0,   events.get(0).onsetMs());
        assertEquals(480, events.get(1).onsetMs());
        assertEquals(960, events.get(2).onsetMs());
    }

    @Test
    void groupedEvent_pitchAccessors() {
        var ev = new GroupedEvent(0, 100, List.of(60, 64, 67));
        assertEquals(60, ev.lowestPitch());
        assertEquals(67, ev.highestPitch());
        // Centroid: (60 + 64 + 67) / 3 = 63.666...
        assertEquals(63.666, ev.centroid(), 0.01);
    }

    @Test
    void groupedEvent_rejectsEmptyPitches() {
        assertThrows(IllegalArgumentException.class,
                () -> new GroupedEvent(0, 100, List.of()));
    }
}
