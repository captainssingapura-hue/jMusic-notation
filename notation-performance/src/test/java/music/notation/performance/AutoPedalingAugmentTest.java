package music.notation.performance;

import music.notation.expressivity.*;
import music.notation.structure.TimeSignature;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Coverage for {@link AutoPedaling#augment(Performance, TimeSignature)}
 * — the pure functional transform that replaces the legacy
 * {@code generate} + {@code PedalInjector} two-step pipeline.
 *
 * <p>Two responsibilities under test:</p>
 * <ol>
 *   <li>Returns the input {@link Performance} unchanged when it
 *       already declares any pedaling (user-authored wins).</li>
 *   <li>Filters auto-generated pedaling to sustain-receptive
 *       instruments only — the fix for the
 *       <em>auto-pedal applies to non-sustain instruments</em>
 *       defect logged in {@code bugs/performance/p1.md}.</li>
 * </ol>
 */
class AutoPedalingAugmentTest {

    private static final TimeSignature FOUR_FOUR = new TimeSignature(4, 4);

    /** A simple 6-second 4/4 piece with one piano-typed track. */
    private static Performance pianoPiece(long lastNoteEndMs) {
        var note = new PitchedNote(0, lastNoteEndMs, 60);
        var track = new Track(new TrackId("Piano"), TrackKind.PITCHED, List.of(note));
        return new Performance(
                new Score(List.of(track)),
                TempoTrack.constant(120),
                Instrumentation.empty(),
                Volume.empty(),
                Articulations.empty(),
                Pedaling.empty(),
                Velocities.empty());
    }

    /** A piece whose only track has the given GM program declared on it. */
    private static Performance pieceWithProgram(int program) {
        var note = new PitchedNote(0, 6000, 60);
        var id = new TrackId("OnlyTrack");
        var track = new Track(id, TrackKind.PITCHED, List.of(note));
        Map<TrackId, InstrumentControl> instMap = new LinkedHashMap<>();
        instMap.put(id, new InstrumentControl(List.of(new InstrumentChange(0L, program))));
        return new Performance(
                new Score(List.of(track)),
                TempoTrack.constant(120),
                new Instrumentation(instMap),
                Volume.empty(),
                Articulations.empty(),
                Pedaling.empty(),
                Velocities.empty());
    }

    @Test
    void augmentAddsPedalingForBareEmptyPianoPiece() {
        // Empty Instrumentation → primaryProgramOf defaults to 0 (piano)
        // → SUSTAIN_FRIENDLY → pedal applied.
        Performance before = pianoPiece(6000);
        Performance after = AutoPedaling.augment(before, FOUR_FOUR);

        assertNotSame(before, after, "augment should return a new Performance");
        assertTrue(before.pedaling().byTrack().isEmpty());
        assertFalse(after.pedaling().byTrack().isEmpty(),
                "piano track should receive auto-pedal");
        assertEquals(1, after.pedaling().byTrack().size());
    }

    @Test
    void augmentLeavesUserAuthoredPedalingUntouched() {
        Performance bare = pianoPiece(6000);
        // Construct a Performance with explicit, non-empty pedaling
        // (a single user-authored DOWN-only timeline).
        var trackId = bare.score().tracks().get(0).id();
        var explicit = new Pedaling(Map.of(
                trackId,
                new PedalControl(List.of(new PedalChange(0L, PedalState.DOWN)))));
        Performance withUserPedal = bare.withPedaling(explicit);

        Performance after = AutoPedaling.augment(withUserPedal, FOUR_FOUR);
        assertSame(withUserPedal, after,
                "user-authored pedaling must not be overwritten by auto");
        assertEquals(1, after.pedaling().byTrack().get(trackId).changes().size(),
                "the original single PedalChange must be preserved verbatim");
    }

    @Test
    void violinTrackGetsNoPedal() {
        // GM program 40 = Violin — not in SUSTAIN_FRIENDLY.
        Performance before = pieceWithProgram(40);
        Performance after = AutoPedaling.augment(before, FOUR_FOUR);
        assertTrue(after.pedaling().byTrack().isEmpty(),
                "violin (program 40) must not get auto-pedal");
    }

    @Test
    void brassTrackGetsNoPedal() {
        // GM program 56 = Trumpet.
        Performance after = AutoPedaling.augment(pieceWithProgram(56), FOUR_FOUR);
        assertTrue(after.pedaling().byTrack().isEmpty());
    }

    @Test
    void woodwindTrackGetsNoPedal() {
        // GM program 73 = Flute.
        Performance after = AutoPedaling.augment(pieceWithProgram(73), FOUR_FOUR);
        assertTrue(after.pedaling().byTrack().isEmpty());
    }

    @Test
    void voiceTrackGetsNoPedal() {
        // GM program 53 = Voice Oohs.
        Performance after = AutoPedaling.augment(pieceWithProgram(53), FOUR_FOUR);
        assertTrue(after.pedaling().byTrack().isEmpty());
    }

    @Test
    void electricPianoGetsPedal() {
        // GM program 4 = Electric Piano 1 — sustain-receptive.
        Performance after = AutoPedaling.augment(pieceWithProgram(4), FOUR_FOUR);
        assertEquals(1, after.pedaling().byTrack().size());
    }

    @Test
    void harpsichordGetsPedal() {
        // GM program 6 = Harpsichord.
        Performance after = AutoPedaling.augment(pieceWithProgram(6), FOUR_FOUR);
        assertEquals(1, after.pedaling().byTrack().size());
    }

    @Test
    void vibraphoneGetsPedal() {
        // GM program 11 = Vibraphone — has a sustain pedal in real life.
        Performance after = AutoPedaling.augment(pieceWithProgram(11), FOUR_FOUR);
        assertEquals(1, after.pedaling().byTrack().size());
    }

    @Test
    void mixedPianoAndStringsTrack_OnlyPianoGetsPedal() {
        // Two pitched tracks: one declared as piano (program 0),
        // one as violin (program 40). Both share the same Score; only
        // the piano-tagged track should receive the auto-pedal.
        var pianoNote = new PitchedNote(0, 6000, 60);
        var violinNote = new PitchedNote(0, 6000, 72);
        var pianoId = new TrackId("Piano");
        var violinId = new TrackId("Violin");
        var pianoTrack = new Track(pianoId, TrackKind.PITCHED, List.of(pianoNote));
        var violinTrack = new Track(violinId, TrackKind.PITCHED, List.of(violinNote));

        Map<TrackId, InstrumentControl> instMap = new LinkedHashMap<>();
        instMap.put(pianoId, new InstrumentControl(List.of(new InstrumentChange(0L, 0))));
        instMap.put(violinId, new InstrumentControl(List.of(new InstrumentChange(0L, 40))));
        Performance before = new Performance(
                new Score(List.of(pianoTrack, violinTrack)),
                TempoTrack.constant(120),
                new Instrumentation(instMap),
                Volume.empty(),
                Articulations.empty(),
                Pedaling.empty(),
                Velocities.empty());

        Performance after = AutoPedaling.augment(before, FOUR_FOUR);
        assertEquals(1, after.pedaling().byTrack().size(),
                "only one track should receive pedal");
        assertTrue(after.pedaling().byTrack().containsKey(pianoId),
                "piano track must have pedal");
        assertFalse(after.pedaling().byTrack().containsKey(violinId),
                "violin track must NOT have pedal");
    }

    @Test
    void organGetsPedal() {
        // GM program 19 = Church Organ — included in SUSTAIN_FRIENDLY
        // for now (organs occasionally benefit from pedal-like phrasing
        // on a soft synth). Re-evaluate if a real organ score sounds wrong.
        Performance after = AutoPedaling.augment(pieceWithProgram(19), FOUR_FOUR);
        assertEquals(1, after.pedaling().byTrack().size());
    }

    @Test
    void nullPerformanceReturnsNull() {
        assertNull(AutoPedaling.augment(null, FOUR_FOUR));
    }

    @Test
    void nullTimeSigReturnsInputUnchanged() {
        // generate(perf, null) returns Pedaling.empty(); augment then
        // returns the input perf unchanged (no-op).
        Performance before = pianoPiece(6000);
        Performance after = AutoPedaling.augment(before, null);
        assertSame(before, after);
    }
}
