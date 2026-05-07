package music.notation.performance;

import music.notation.expressivity.*;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validation and canonicalisation tests for the new performance-layer
 * value objects: TrackId, Track, Score, Performance, and the per-event
 * records (PitchedNote, DrumNote, TempoChange, InstrumentChange).
 */
class ScoreTest {

    private static final TrackId LEAD = new TrackId("lead");
    private static final TrackId BASS = new TrackId("bass");
    private static final TrackId DRUMS = new TrackId("drums");

    // ── TrackId ──────────────────────────────────────────────────────

    @Test
    void trackId_rejectsBlankAndNullName() {
        assertThrows(NullPointerException.class, () -> new TrackId(null));
        assertThrows(IllegalArgumentException.class, () -> new TrackId(""));
        assertThrows(IllegalArgumentException.class, () -> new TrackId("   "));
    }

    // ── PitchedNote / DrumNote ───────────────────────────────────────

    @Test
    void pitchedNote_rejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> new PitchedNote(-1, 500, 60));
        assertThrows(IllegalArgumentException.class, () -> new PitchedNote(0, 0, 60));
        assertThrows(IllegalArgumentException.class, () -> new PitchedNote(0, -100, 60));
        assertThrows(IllegalArgumentException.class, () -> new PitchedNote(0, 500, -1));
        assertThrows(IllegalArgumentException.class, () -> new PitchedNote(0, 500, 128));
    }

    @Test
    void drumNote_rejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> new DrumNote(-1, 250, 38));
        assertThrows(IllegalArgumentException.class, () -> new DrumNote(0, 0, 38));
        assertThrows(IllegalArgumentException.class, () -> new DrumNote(0, 250, -1));
        assertThrows(IllegalArgumentException.class, () -> new DrumNote(0, 250, 128));
    }

    @Test
    void offTickMs_isDerivedFromTickAndDuration() {
        assertEquals(800, new PitchedNote(500, 300, 60).offTickMs());
        assertEquals(800, new DrumNote(500, 300, 38).offTickMs());
    }

    // ── Track ────────────────────────────────────────────────────────

    @Test
    void pitchedTrack_rejectsDrumNotes() {
        assertThrows(IllegalArgumentException.class, () ->
                new Track(LEAD, TrackKind.PITCHED, List.of(new DrumNote(0, 250, 38))));
    }

    @Test
    void drumTrack_rejectsPitchedNotes() {
        assertThrows(IllegalArgumentException.class, () ->
                new Track(DRUMS, TrackKind.DRUM, List.of(new PitchedNote(0, 500, 60))));
    }

    @Test
    void emptyTrack_isLegal() {
        var t = Track.empty(LEAD, TrackKind.PITCHED);
        assertEquals(0, t.notes().size());
    }

    @Test
    void track_canonicalisesNotesByTick() {
        var t = new Track(LEAD, TrackKind.PITCHED, List.of(
                new PitchedNote(1000, 500, 64),
                new PitchedNote(0,    500, 60),
                new PitchedNote(500,  500, 62)));
        assertEquals(0,    t.notes().get(0).tickMs());
        assertEquals(500,  t.notes().get(1).tickMs());
        assertEquals(1000, t.notes().get(2).tickMs());
    }

    // ── Score ────────────────────────────────────────────────────────

    @Test
    void score_rejectsDuplicateTrackIds() {
        var t1 = Track.empty(LEAD, TrackKind.PITCHED);
        var t2 = new Track(LEAD, TrackKind.PITCHED, List.of(new PitchedNote(0, 500, 60)));
        assertThrows(IllegalArgumentException.class, () -> new Score(List.of(t1, t2)));
    }

    @Test
    void score_rejectsMultipleDrumTracks() {
        var d1 = new Track(new TrackId("kit_a"), TrackKind.DRUM, List.of());
        var d2 = new Track(new TrackId("kit_b"), TrackKind.DRUM, List.of());
        assertThrows(IllegalArgumentException.class, () -> new Score(List.of(d1, d2)));
    }

    @Test
    void score_drumTrackSinksToLast() {
        var lead = Track.empty(LEAD, TrackKind.PITCHED);
        var drums = Track.empty(DRUMS, TrackKind.DRUM);
        var bass = Track.empty(BASS, TrackKind.PITCHED);
        var s = new Score(List.of(lead, drums, bass));
        assertEquals(LEAD,  s.tracks().get(0).id());
        assertEquals(BASS,  s.tracks().get(1).id());
        assertEquals(DRUMS, s.tracks().get(2).id());
    }

    @Test
    void score_preservesAuthoringOrderWithinKind() {
        var bass = Track.empty(BASS, TrackKind.PITCHED);
        var lead = Track.empty(LEAD, TrackKind.PITCHED);
        var s = new Score(List.of(bass, lead));
        // Authoring order: bass then lead. Drum is absent so order is preserved.
        assertEquals(BASS, s.tracks().get(0).id());
        assertEquals(LEAD, s.tracks().get(1).id());
    }

    @Test
    void score_emptyIsLegal() {
        assertEquals(0, Score.empty().tracks().size());
    }

    // ── Performance ──────────────────────────────────────────────────

    @Test
    void performance_rejectsInstrumentationKeyNotInScore() {
        var score = new Score(List.of(Track.empty(LEAD, TrackKind.PITCHED)));
        var rogueId = new TrackId("missing");
        var instr = new Instrumentation(Map.of(rogueId, InstrumentControl.constant(0)));
        assertThrows(IllegalArgumentException.class, () ->
                new Performance(score, TempoTrack.empty(), instr, Articulations.empty()));
    }

    @Test
    void performance_rejectsArticulationsKeyNotInScore() {
        var score = new Score(List.of(Track.empty(LEAD, TrackKind.PITCHED)));
        var rogueId = new TrackId("missing");
        var arts = new Articulations(Map.of(rogueId, ArticulationControl.constant(Articulation.LEGATO)));
        assertThrows(IllegalArgumentException.class, () ->
                new Performance(score, TempoTrack.empty(), Instrumentation.empty(), arts));
    }

    @Test
    void performance_of_buildsWithDefaults() {
        var score = new Score(List.of(Track.empty(LEAD, TrackKind.PITCHED)));
        var p = Performance.of(score);
        assertEquals(0, p.tempo().changes().size());
        assertEquals(0, p.instruments().byTrack().size());
        assertEquals(0, p.articulations().byTrack().size());
    }

    // ── Tempo / Instrument record validation ─────────────────────────

    @Test
    void tempoChange_rejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> new TempoChange(-1, 120));
        assertThrows(IllegalArgumentException.class, () -> new TempoChange(0, 0));
        assertThrows(IllegalArgumentException.class, () -> new TempoChange(0, 1000));
    }

    @Test
    void instrumentChange_rejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> new InstrumentChange(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new InstrumentChange(0, -1));
        assertThrows(IllegalArgumentException.class, () -> new InstrumentChange(0, 128));
    }

    @Test
    void tempoTrack_dedupesConsecutiveSameBpm() {
        var tt = new TempoTrack(List.of(
                new TempoChange(0, 120),
                new TempoChange(1000, 120),       // dropped (same as previous)
                new TempoChange(2000, 140),
                new TempoChange(3000, 140)));     // dropped
        assertEquals(2, tt.changes().size());
        assertEquals(120, tt.changes().get(0).bpm());
        assertEquals(140, tt.changes().get(1).bpm());
    }

    @Test
    void instrumentation_dropsEmptyControls() {
        var i = new Instrumentation(Map.of(
                LEAD, InstrumentControl.empty(),
                BASS, InstrumentControl.constant(40)));
        assertFalse(i.byTrack().containsKey(LEAD));
        assertTrue(i.byTrack().containsKey(BASS));
    }
}
