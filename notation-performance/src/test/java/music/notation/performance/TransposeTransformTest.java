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
 * <p>After the refactor to the {@link ShiftedNote} wrap representation:
 * {@code apply} produces {@link ShiftedNote}s wrapping the original
 * {@link PitchedNote}s, not new PitchedNotes at shifted MIDI values.
 * Tests verify the effective MIDI (via {@link PitchedLike#midi()})
 * matches the linear-shift contract <em>and</em> that the original is
 * still recoverable from each shifted note.</p>
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
                List.of(new DrumNote(0,   100, 36),
                        new DrumNote(500, 100, 38)));
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
            int orig = ((PitchedLike) origNotes.get(i)).midi();
            int next = ((PitchedLike) newNotes.get(i)).midi();
            assertEquals(orig + 5, next, "note " + i + " effective midi should shift by exactly +5");
        }
    }

    @Test
    void negativeShiftLowersEveryPitchedNote() {
        Performance perf = pianoPiece();
        Performance down7 = TransposeTransform.apply(perf, new TransposeTransform.Params(-7));

        var origNotes = perf.score().tracks().get(0).notes();
        var newNotes = down7.score().tracks().get(0).notes();
        for (int i = 0; i < origNotes.size(); i++) {
            int orig = ((PitchedLike) origNotes.get(i)).midi();
            int next = ((PitchedLike) newNotes.get(i)).midi();
            assertEquals(orig - 7, next, "note " + i + " effective midi should shift by exactly -7");
        }
    }

    @Test
    void shiftedNotesAreShiftedNoteInstances() {
        Performance perf = pianoPiece();
        Performance up5 = TransposeTransform.apply(perf, new TransposeTransform.Params(5));

        for (ConcreteNote n : up5.score().tracks().get(0).notes()) {
            assertInstanceOf(ShiftedNote.class, n,
                    "after non-zero shift, pitched notes should be ShiftedNote wrappers");
            ShiftedNote s = (ShiftedNote) n;
            assertEquals(5, s.semitoneShift(), "wrap carries the shift");
        }
    }

    @Test
    void originalIsRecoverableFromShiftedNote() {
        Performance perf = pianoPiece();
        Performance up5 = TransposeTransform.apply(perf, new TransposeTransform.Params(5));

        var origNotes = perf.score().tracks().get(0).notes();
        var newNotes = up5.score().tracks().get(0).notes();
        for (int i = 0; i < origNotes.size(); i++) {
            ShiftedNote s = (ShiftedNote) newNotes.get(i);
            PitchedNote orig = (PitchedNote) origNotes.get(i);
            assertEquals(orig.midi(), s.originalMidi(),
                    "ShiftedNote.originalMidi() recovers authored value");
            assertEquals(orig, s.original(),
                    "ShiftedNote.original() is the source PitchedNote");
            assertEquals(orig.midi() + 5, s.midi(),
                    "ShiftedNote.midi() returns effective value");
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
                    "tickMs preserved (delegated to original)");
            assertEquals(origNotes.get(i).durationMs(), newNotes.get(i).durationMs(),
                    "durationMs preserved (delegated to original)");
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
        assertTrue(((ShiftedNote) newNotes.get(0)).tiedToNext(),
                "tied flag preserved on first note (delegated through wrap)");
        assertFalse(((ShiftedNote) newNotes.get(1)).tiedToNext(),
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
                new PitchedNote(0,    500, 100),   // 100+20 = 120 valid
                new PitchedNote(500,  500, 110),   // 110+20 = 130 → DROPPED
                new PitchedNote(1000, 500, 60));   // 60+20  = 80  valid
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
        assertEquals(120, ((PitchedLike) newNotes.get(0)).midi());
        assertEquals(80,  ((PitchedLike) newNotes.get(1)).midi());
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
        assertEquals(50, ((PitchedLike) newNotes.get(0)).midi());
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
        assertEquals(75, ((PitchedLike) shifted.score().tracks().get(0).notes().get(0)).midi());
        assertEquals(51, ((PitchedLike) shifted.score().tracks().get(1).notes().get(0)).midi());
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
    void linearCompositionFlattens() {
        // (+3) then (+4) should produce ShiftedNote with shift=+7 (flattened),
        // NOT a nested ShiftedNote(ShiftedNote(...)). The original is preserved.
        Performance perf = pianoPiece();
        Performance a = TransposeTransform.apply(perf, new TransposeTransform.Params(3));
        Performance ab = TransposeTransform.apply(a, new TransposeTransform.Params(4));
        Performance b = TransposeTransform.apply(perf, new TransposeTransform.Params(7));

        // Effective MIDI matches a single +7 shift.
        var abNotes = ab.score().tracks().get(0).notes();
        var bNotes = b.score().tracks().get(0).notes();
        for (int i = 0; i < abNotes.size(); i++) {
            assertEquals(((PitchedLike) abNotes.get(i)).midi(),
                    ((PitchedLike) bNotes.get(i)).midi(),
                    "+3 then +4 composes to +7 at note " + i);
        }

        // Flattening: result is ShiftedNote with single shift=+7, not nested.
        for (ConcreteNote n : abNotes) {
            ShiftedNote s = (ShiftedNote) n;
            assertEquals(7, s.semitoneShift(),
                    "successive shifts flatten into a single semitoneShift");
            assertInstanceOf(PitchedNote.class, s.original(),
                    "ShiftedNote.original() must remain a PitchedNote, never nested");
        }
    }

    @Test
    void shiftedNoteIsAlsoPitchedLikeAndConcreteNote() {
        // Type-membership assertions — locks the sealed hierarchy in tests
        // so accidental re-parenting elsewhere would be caught.
        var sn = new ShiftedNote(new PitchedNote(0, 500, 60), 5);
        assertInstanceOf(PitchedLike.class, sn);
        assertInstanceOf(ConcreteNote.class, sn);
    }

    @Test
    void shiftedNoteConstructorRejectsOutOfRange() {
        // Validation at construction matches PitchedNote's stance.
        assertThrows(IllegalArgumentException.class,
                () -> new ShiftedNote(new PitchedNote(0, 500, 120), 20));
        assertThrows(IllegalArgumentException.class,
                () -> new ShiftedNote(new PitchedNote(0, 500, 5), -10));
    }

    @Test
    void shiftedNoteFactoryShortCircuitsZero() {
        PitchedLike base = new PitchedNote(0, 500, 60);
        assertSame(base, ShiftedNote.of(base, 0),
                "shift of 0 returns input unchanged");
    }
}
