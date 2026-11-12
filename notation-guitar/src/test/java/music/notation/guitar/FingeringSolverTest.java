package music.notation.guitar;

import music.notation.duration.Duration;
import music.notation.performance.PitchedNote;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FingeringSolverTest {

    private static final Meter M = Meter.FOUR_FOUR;

    private static FingeringSolver solver() {
        return new FingeringSolver(Tuning.STANDARD, HandConstraints.DEFAULT, CostWeights.DEFAULT, M);
    }

    private static FingeringSolver solver(HandConstraints hand) {
        return new FingeringSolver(Tuning.STANDARD, hand, CostWeights.DEFAULT, M);
    }

    /** Notes of equal length, one after another, starting at 0. */
    private static List<PitchedNote> run(Duration step, int... midis) {
        var out = new ArrayList<PitchedNote>();
        Duration at = Duration.zero();
        for (int m : midis) { out.add(new PitchedNote(at, step, m)); at = at.plus(step); }
        return out;
    }

    private static void assertSounds(Fingering f, Tuning t) {
        for (FingeredNote n : f.notes()) {
            assertEquals(n.midi(), t.pitchAt(n.position().string(), n.position().fret()), "wrong pitch at " + n.at());
            assertTrue(n.position().fret() <= HandConstraints.DEFAULT.maxFret());
            assertTrue(n.position().fret() == 0 || (n.position().fret() >= n.handPosition()
                    && n.position().fret() < n.handPosition() + HandConstraints.DEFAULT.span()
                    || n.position().fret() - n.handPosition() <= HandConstraints.DEFAULT.maxStretch()),
                    "fret " + n.position().fret() + " outside hand at " + n.handPosition());
        }
    }

    @Test
    void everyNoteSoundsAtItsWrittenPitch() {
        var f = solver().solve(run(Duration.of(1, 8), 58, 61, 63, 64, 65, 68, 70, 73, 75, 77, 80, 82));
        assertEquals(12, f.notes().size());
        assertSounds(f, Tuning.STANDARD);
        // Time order is preserved and bar/beat are filled in.
        assertEquals(1, f.notes().get(0).bar()); assertEquals(1.0, f.notes().get(0).beat());
        assertEquals(2, f.notes().get(8).bar()); assertEquals(1.0, f.notes().get(8).beat());
        assertEquals(1.5, f.notes().get(1).beat());
    }

    @Test
    void aFastRunInsideOnePositionNeedsNoShift() {
        // B♭3 … F4 chromatically in sixteenths: eight notes, seven semitones — one position covers it.
        var f = solver().solve(run(Duration.of(1, 16), 58, 59, 60, 61, 62, 63, 64, 65));
        assertEquals(0, f.shifts(), "chromatic run within a hand span should stay put: " + f.notes());
        assertSounds(f, Tuning.STANDARD);
    }

    @Test
    void aSweepArpeggioStaysInOnePositionAcrossStrings() {
        // B♭7 arpeggio two octaves up (as in the blues solos) in sixteenths.
        var f = solver().solve(run(Duration.of(1, 16), 58, 62, 65, 68, 70, 74, 77, 80, 82));
        assertSounds(f, Tuning.STANDARD);
        assertTrue(f.shifts() <= 1, "a two-octave sweep needs at most one shift: " + f.shifts());
        // It uses several strings rather than running up one.
        var strings = new HashSet<Integer>();
        for (FingeredNote n : f.notes()) strings.add(n.position().string());
        assertTrue(strings.size() >= 4, "sweep should spread across strings: " + strings);
    }

    @Test
    void shiftingIsDearerBetweenFastNotesThanSlowOnes() {
        // A2 → E5 is an unavoidable shift.
        var fast = solver().solve(run(Duration.of(1, 16), 45, 76));
        var slow = solver().solve(run(Duration.of(1, 1), 45, 76));
        assertTrue(fast.cost() > slow.cost(), "fast " + fast.cost() + " vs slow " + slow.cost());
    }

    @Test
    void chordsUseDistinctStringsWithinTheStretch() {
        Duration at = Duration.zero(), q = Duration.of(1, 4);
        var notes = List.of(new PitchedNote(at, q, 60), new PitchedNote(at, q, 64), new PitchedNote(at, q, 67));
        var f = solver().solve(notes);
        assertEquals(3, f.notes().size());
        var strings = new HashSet<Integer>();
        int lo = Integer.MAX_VALUE, hi = 0;
        for (FingeredNote n : f.notes()) {
            assertTrue(strings.add(n.position().string()), "two notes on one string");
            if (n.position().fret() > 0) { lo = Math.min(lo, n.position().fret()); hi = Math.max(hi, n.position().fret()); }
        }
        if (lo != Integer.MAX_VALUE) assertTrue(hi - lo <= HandConstraints.DEFAULT.maxStretch());
        assertSounds(f, Tuning.STANDARD);
        // Highest pitch first within the event.
        assertEquals(67, f.notes().get(0).midi());
    }

    @Test
    void tiedNotesKeepTheirPosition() {
        Duration q = Duration.of(1, 4);
        // A high G4 tied across, then a leap down: the tie must not move even if the leap would prefer it.
        var notes = List.of(
                new PitchedNote(Duration.zero(), q, 67, true),
                new PitchedNote(q, q, 67, false),
                new PitchedNote(q.times(2), q, 45, false));
        var f = solver().solve(notes);
        assertEquals(f.notes().get(0).position(), f.notes().get(1).position());
    }

    @Test
    void notesOffTheNeckAreRejected_orFoldedOnRequest() {
        var low = run(Duration.of(1, 4), 30, 64);
        var ex = assertThrows(UnplayableNotesException.class, () -> solver().solve(low));
        assertEquals(1, ex.notes().size());
        assertEquals(30, ex.notes().get(0).midi());

        var folding = new HandConstraints(22, 4, 5, 15, true, true);
        var f = solver(folding).solve(low);
        assertEquals(42, f.notes().get(0).midi(), "folded up an octave");
        assertTrue(f.notes().get(0).folded());
        assertFalse(f.notes().get(1).folded());
    }

    @Test
    void tooManySimultaneousNotesAreRejected() {
        Duration at = Duration.zero(), q = Duration.of(1, 4);
        var notes = new ArrayList<PitchedNote>();
        for (int m = 50; m < 57; m++) notes.add(new PitchedNote(at, q, m));
        assertThrows(UnplayableNotesException.class, () -> solver().solve(notes));
    }

    @Test
    void noOpenStringsWhenForbidden() {
        var noOpen = new HandConstraints(22, 4, 5, 15, false, false);
        var f = solver(noOpen).solve(run(Duration.of(1, 4), 64, 59, 55, 50, 45, 40 + 1));
        for (FingeredNote n : f.notes()) assertTrue(n.position().fret() > 0, "open string used at " + n.at());
    }

    @Test
    void highFretsArePenalised() {
        // Middle C alone: the solver has s2 f1, s3 f5, s4 f10, s5 f15, s6 f20. Nothing forces high; expect ≤ preferred.
        var f = solver().solve(run(Duration.of(1, 4), 60));
        assertTrue(f.notes().get(0).position().fret() <= HandConstraints.DEFAULT.preferredMaxFret());
    }

    @Test
    void deterministic() {
        var notes = run(Duration.of(1, 16), 58, 62, 65, 68, 70, 74, 77, 80, 82, 80, 77, 74, 70, 68, 65, 62);
        assertEquals(solver().solve(notes), solver().solve(notes));
    }

    @Test
    void emptyInputGivesEmptyFingering() {
        assertTrue(solver().solve(List.of()).isEmpty());
    }
}
