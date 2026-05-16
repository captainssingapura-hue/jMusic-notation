package music.notation.performance;

import music.notation.expressivity.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Coverage for {@link TransposeTransform#apply(Performance,
 * TransposeTransform.Params)}.
 *
 * <p>Verifies the strictly-linear contract: every pitched note shifts
 * by exactly the same integer semitone amount, duration and tick are
 * preserved, drums pass through, side-channels pass through, and
 * out-of-range notes drop without throwing.</p>
 */
class TransposeTransformTest {

    /** A 3-note piano piece at modest pitches; safe to transpose ±12 within range. */
    private static Performance pianoPiece() {
        var notes = List.<ConcreteNote>of(
                new PitchedNote(0,    500, 60),   // C4
                new PitchedNote(500,  500, 62),   // D4
                new PitchedNote(1000, 500, 64));  // E4
        var track = new Track(new TrackId("Piano"), TrackKind.PITCHED, notes);
        return new Performance(
                new Score(List.of(track)),
                TempoTrack.constant(120),
                Instrumentation.empty(), Volume.empty(),
                Articulations.empty(), Pedaling.empty(),
                Velocities.empty());
    }

    /** A piece with pitched + drum tracks; for verifying drum-immunity. */
    private static Performance pianoPlusDrums() {
        var pitched = new Track(
                new TrackId("Piano"), TrackKind.PITCHED,
                List.of(new PitchedNote(0,   500, 60),
                        new PitchedNote(500, 500, 64)));
        var drums = new Track(
                new TrackId("Drums"), TrackKind.DRUM,
                List.of(new DrumNote(0,   100, 36),    // kick
                        new DrumNote(500, 100, 38)));  // snare
        return new Performance(
                new Score(List.of(pitched, drums)),
                TempoTrack.constant(120),
                Instrumentation.empty(), Volume.empty(),
                Articulations.empty(), Pedaling.empty(),
                Velocities.empty());
    }

    @Test
    void offIsIdentity() {
        Performance perf = pianoPiece();
        assertSame(perf, TransposeTransform.apply(perf, TransposeTransform.Params.NONE));
    }

    @Test
    void zeroShiftIsIdentity() {
        Performance perf = pianoPiece();
        assertSame(perf, TransposeTransform.apply(perf, new TransposeTransform.Params(0)));
    }

    @Test
    void nullParamsIsIdentity() {
        Performance perf = pianoPiece();
        assertSame(perf, TransposeTransform.apply(perf, null));
    }

    @Test
    void nullPerformanceReturnsNull() {
        assertNull(TransposeTransform.apply(null, new TransposeTransform.Params(5)));
    }

    @Test
    void positiveShiftRaisesEveryPitchedNote() {
        Performance perf = pianoPiece();
        Performance up5 = TransposeTransform.apply(perf, new TransposeTransform.Params(5));

        var origNotes = perf.score().tracks().get(0).notes();
        var newNotes = up5.score().tracks().get(0).notes();
        assertEquals(origNotes.size(), newNotes.size());
        for (int i = 0; i < origNotes.size(); i++) {
            int orig = ((PitchedNote) origNotes.get(i)).midi();
            int next = ((PitchedNote) newNotes.get(i)).midi();
            assertEquals(orig + 5, next, "note " + i + " should shift by exactly +5");
        }
    }

    @Test
    void negativeShiftLowersEveryPitchedNote() {
        Performance perf = pianoPiece();
        Performance down7 = TransposeTransform.apply(perf, new TransposeTransform.Params(-7));

        var origNotes = perf.score().tracks().get(0).notes();
        var newNotes = down7.score().tracks().get(0).notes();
        for (int i = 0; i < origNotes.size(); i++) {
            int orig = ((PitchedNote) origNotes.get(i)).midi();
            int next = ((PitchedNote) newNotes.get(i)).midi();
            assertEquals(orig - 7, next, "note " + i + " should shift by exactly -7");
        }
    }

    @Test
    void shiftPreservesTickAndDuration() {
        Performance perf = pianoPiece();
        Performance shifted = TransposeTransform.apply(perf,
                new TransposeTransform.Params(3));

        var origNotes = perf.score().tracks().get(0).notes();
        var newNotes = shifted.score().tracks().get(0).notes();
        for (int i = 0; i < origNotes.size(); i++) {
            assertEquals(origNotes.get(i).tickMs(), newNotes.get(i).tickMs(),
                    "tickMs preserved");
            assertEquals(origNotes.get(i).durationMs(), newNotes.get(i).durationMs(),
                    "durationMs preserved");
        }
    }

    @Test
    void shiftPreservesTiedToNextFlag() {
        var notes = List.<ConcreteNote>of(
                new PitchedNote(0,   500, 60, true),    // tied
                new PitchedNote(500, 500, 60, false));  // not tied
        var track = new Track(new TrackId("Piano"), TrackKind.PITCHED, notes);
        Performance perf = new Performance(
                new Score(List.of(track)),
                TempoTrack.constant(120),
                Instrumentation.empty(), Volume.empty(),
                Articulations.empty(), Pedaling.empty(),
                Velocities.empty());

        Performance shifted = TransposeTransform.apply(perf,
                new TransposeTransform.Params(2));
        var newNotes = shifted.score().tracks().get(0).notes();
        assertTrue(((PitchedNote) newNotes.get(0)).tiedToNext(),
                "tied flag preserved on first note");
        assertFalse(((PitchedNote) newNotes.get(1)).tiedToNext(),
                "tied flag preserved on second note");
    }

    @Test
    void drumTracksPassThroughUnchanged() {
        Performance perf = pianoPlusDrums();
        Performance shifted = TransposeTransform.apply(perf,
                new TransposeTransform.Params(5));

        // Drum track must be byte-identical (same Track object reused).
        Track origDrums = perf.score().tracks().get(1);
        Track newDrums = shifted.score().tracks().get(1);
        assertSame(origDrums, newDrums,
                "DRUM-kind tracks must pass through identically — "
                        + "drum 'pitch' is a kit selector, not a pitch");
    }

    @Test
    void sideChannelsPassThroughUnchanged() {
        // Build a Performance with non-empty side-channels.
        var pianoId = new TrackId("Piano");
        var track = new Track(pianoId, TrackKind.PITCHED,
                List.of(new PitchedNote(0, 500, 60)));
        var volMap = java.util.Map.of(pianoId,
                new VolumeControl(List.of(new VolumeChange(0, 80))));
        var velMap = java.util.Map.of(pianoId,
                new VelocityControl(List.of(new VelocityChange(0, 80))));
        var pedMap = java.util.Map.of(pianoId,
                new PedalControl(List.of(new PedalChange(0, PedalState.DOWN))));

        Performance perf = new Performance(
                new Score(List.of(track)),
                TempoTrack.constant(140),
                Instrumentation.empty(),
                new Volume(volMap),
                Articulations.empty(),
                new Pedaling(pedMap),
                new Velocities(velMap));

        Performance shifted = TransposeTransform.apply(perf,
                new TransposeTransform.Params(7));

        assertSame(perf.tempo(), shifted.tempo(), "tempo passes through");
        assertEquals(perf.volume(), shifted.volume(), "volume passes through");
        assertEquals(perf.velocities(), shifted.velocities(),
                "velocities pass through");
        assertEquals(perf.pedaling(), shifted.pedaling(), "pedaling passes through");
    }

    @Test
    void outOfRangePositiveDrops() {
        var notes = List.<ConcreteNote>of(
                new PitchedNote(0,    500, 100),   // valid before, valid after +20 = 120
                new PitchedNote(500,  500, 110),   // valid before, valid after +20 = 130 → DROPPED
                new PitchedNote(1000, 500, 60));   // safe shift
        var track = new Track(new TrackId("Piano"), TrackKind.PITCHED, notes);
        Performance perf = new Performance(
                new Score(List.of(track)),
                TempoTrack.constant(120),
                Instrumentation.empty(), Volume.empty(),
                Articulations.empty(), Pedaling.empty(),
                Velocities.empty());

        Performance shifted = TransposeTransform.apply(perf,
                new TransposeTransform.Params(20));
        var newNotes = shifted.score().tracks().get(0).notes();

        assertEquals(2, newNotes.size(),
                "one out-of-range note should be dropped");
        assertEquals(120, ((PitchedNote) newNotes.get(0)).midi());
        assertEquals(80,  ((PitchedNote) newNotes.get(1)).midi());
    }

    @Test
    void outOfRangeNegativeDrops() {
        var notes = List.<ConcreteNote>of(
                new PitchedNote(0,    500, 5),    // 5 - 10 = -5 → DROPPED
                new PitchedNote(500,  500, 60));  // safe
        var track = new Track(new TrackId("Piano"), TrackKind.PITCHED, notes);
        Performance perf = new Performance(
                new Score(List.of(track)),
                TempoTrack.constant(120),
                Instrumentation.empty(), Volume.empty(),
                Articulations.empty(), Pedaling.empty(),
                Velocities.empty());

        Performance shifted = TransposeTransform.apply(perf,
                new TransposeTransform.Params(-10));
        var newNotes = shifted.score().tracks().get(0).notes();

        assertEquals(1, newNotes.size());
        assertEquals(50, ((PitchedNote) newNotes.get(0)).midi());
    }

    @Test
    void allNotesOutOfRangeProducesEmptyTrack() {
        var notes = List.<ConcreteNote>of(
                new PitchedNote(0,    500, 120),
                new PitchedNote(500,  500, 122));
        var track = new Track(new TrackId("Piano"), TrackKind.PITCHED, notes);
        Performance perf = new Performance(
                new Score(List.of(track)),
                TempoTrack.constant(120),
                Instrumentation.empty(), Volume.empty(),
                Articulations.empty(), Pedaling.empty(),
                Velocities.empty());

        Performance shifted = TransposeTransform.apply(perf,
                new TransposeTransform.Params(20));
        var newNotes = shifted.score().tracks().get(0).notes();
        assertTrue(newNotes.isEmpty(),
                "all notes out of range → empty track (transform does not throw)");
    }

    @Test
    void countOutOfRangeMatchesActualDrops() {
        // 5 pitched notes, 2 will go out of range after +20.
        List<ConcreteNote> notes = new ArrayList<>();
        notes.add(new PitchedNote(0,   500, 100));   // 120 — valid
        notes.add(new PitchedNote(500, 500, 110));   // 130 — DROPPED
        notes.add(new PitchedNote(1000,500, 60));    // 80  — valid
        notes.add(new PitchedNote(1500,500, 115));   // 135 — DROPPED
        notes.add(new PitchedNote(2000,500, 50));    // 70  — valid

        var track = new Track(new TrackId("Piano"), TrackKind.PITCHED, notes);
        Performance perf = new Performance(
                new Score(List.of(track)),
                TempoTrack.constant(120),
                Instrumentation.empty(), Volume.empty(),
                Articulations.empty(), Pedaling.empty(),
                Velocities.empty());

        int count = TransposeTransform.countOutOfRange(perf, 20);
        assertEquals(2, count, "pre-flight count matches actual drops");

        Performance shifted = TransposeTransform.apply(perf,
                new TransposeTransform.Params(20));
        assertEquals(notes.size() - count,
                shifted.score().tracks().get(0).notes().size(),
                "apply drops exactly count many notes");
    }

    @Test
    void countOutOfRangeIgnoresDrums() {
        Performance perf = pianoPlusDrums();
        // Even an absurd shift on drum-piece values doesn't count
        // toward the out-of-range tally — drums are exempt.
        int count = TransposeTransform.countOutOfRange(perf, 100);
        // Only the pitched notes (60, 64) — 60+100=160 dropped, 64+100=164 dropped.
        assertEquals(2, count,
                "drums excluded from out-of-range count");
    }

    @Test
    void multiplePitchedTracksAllShifted() {
        var t1 = new Track(new TrackId("RH"), TrackKind.PITCHED,
                List.of(new PitchedNote(0, 500, 72)));
        var t2 = new Track(new TrackId("LH"), TrackKind.PITCHED,
                List.of(new PitchedNote(0, 500, 48)));
        Performance perf = new Performance(
                new Score(List.of(t1, t2)),
                TempoTrack.constant(120),
                Instrumentation.empty(), Volume.empty(),
                Articulations.empty(), Pedaling.empty(),
                Velocities.empty());

        Performance shifted = TransposeTransform.apply(perf,
                new TransposeTransform.Params(3));
        assertEquals(75, ((PitchedNote) shifted.score().tracks().get(0).notes().get(0)).midi());
        assertEquals(51, ((PitchedNote) shifted.score().tracks().get(1).notes().get(0)).midi());
    }

    @Test
    void emptyScoreTransposesToEmptyScore() {
        Performance perf = Performance.of(Score.empty());
        Performance shifted = TransposeTransform.apply(perf,
                new TransposeTransform.Params(5));
        // Empty input — should pass through (no change to make).
        assertSame(perf, shifted);
    }

    @Test
    void linearComposition() {
        // (+3) then (+4) should equal (+7) applied in one step.
        Performance perf = pianoPiece();
        Performance a = TransposeTransform.apply(
                TransposeTransform.apply(perf, new TransposeTransform.Params(3)),
                new TransposeTransform.Params(4));
        Performance b = TransposeTransform.apply(perf,
                new TransposeTransform.Params(7));

        // Compare pitched MIDIs note-by-note.
        var aNotes = a.score().tracks().get(0).notes();
        var bNotes = b.score().tracks().get(0).notes();
        assertEquals(aNotes.size(), bNotes.size());
        for (int i = 0; i < aNotes.size(); i++) {
            assertEquals(((PitchedNote) aNotes.get(i)).midi(),
                    ((PitchedNote) bNotes.get(i)).midi(),
                    "composition holds at note " + i);
        }
    }
}
