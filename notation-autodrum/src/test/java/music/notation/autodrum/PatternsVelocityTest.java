package music.notation.autodrum;

import music.notation.duration.BarDuration;
import music.notation.duration.BaseValue;
import music.notation.duration.Duration;
import music.notation.event.Instrument;
import music.notation.event.PercussionSound;
import music.notation.expressivity.VelocityChange;
import music.notation.expressivity.VelocityControl;
import music.notation.phrase.Bar;
import music.notation.phrase.Phrase;
import music.notation.phrase.PhraseNode;
import music.notation.phrase.RestNode;
import music.notation.phrase.SimplePitchNode;
import music.notation.pitch.NoteName;
import music.notation.pitch.Pitch;
import music.notation.structure.KeySignature;
import music.notation.structure.MelodicTrack;
import music.notation.structure.Mode;
import music.notation.structure.Piece;
import music.notation.structure.Tempo;
import music.notation.structure.TimeSignature;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Coverage for {@link Patterns#generateTrackWithVelocities} —
 * the velocity-aware drum bake.
 */
class PatternsVelocityTest {

    private static final BarDuration BD = new BarDuration(4, BaseValue.QUARTER);

    private static Bar standardBar() {
        var nodes = new ArrayList<PhraseNode>();
        for (int i = 0; i < 8; i++) {
            nodes.add(new SimplePitchNode(Pitch.of(NoteName.C, 4),
                    Duration.of(BaseValue.EIGHTH),
                    Optional.empty(), List.of(), false, false));
        }
        return Bar.of(BD, nodes.toArray(new PhraseNode[0]));
    }

    private static Piece pieceAt120Bpm(int bars) {
        var barList = new ArrayList<Bar>();
        for (int i = 0; i < bars; i++) barList.add(standardBar());
        var melody = new MelodicTrack("Melody", Instrument.ACOUSTIC_GRAND_PIANO,
                Phrase.of(barList));
        return new Piece("Test", "Test",
                new KeySignature(NoteName.C, Mode.MAJOR),
                new TimeSignature(4, 4),
                new Tempo(120, BaseValue.QUARTER),
                List.of(melody));
    }

    /** Spec without velocities — bake should produce empty velocity timeline. */
    @Test
    void specWithoutVelocitiesYieldsEmptyControl() {
        PatternSpec plain = new PatternSpec(BaseValue.QUARTER,
                new PercussionSound[] {
                        PercussionSound.BASS_DRUM, PercussionSound.ACOUSTIC_SNARE,
                        PercussionSound.BASS_DRUM, PercussionSound.ACOUSTIC_SNARE });

        var result = Patterns.generateTrackWithVelocities(
                "Auto Drum", pieceAt120Bpm(2), Energy.MEDIUM,
                (bd, e, f, i) -> plain);
        assertTrue(result.isPresent());
        assertEquals(VelocityControl.empty(), result.get().velocities());
    }

    /** Spec WITH velocities — every non-rest slot becomes a VelocityChange at the right ms. */
    @Test
    void slotVelocitiesEmitChangesAtCorrectMsPositions() {
        // 4/4 at 120bpm → 2000ms per bar, 500ms per quarter slot.
        // 4-slot quarter pattern: kick/snare alternating, distinct velocities.
        PatternSpec withVel = new PatternSpec(BaseValue.QUARTER,
                new PercussionSound[] {
                        PercussionSound.BASS_DRUM, PercussionSound.ACOUSTIC_SNARE,
                        PercussionSound.BASS_DRUM, PercussionSound.ACOUSTIC_SNARE },
                new int[] { 100, 115, 95, 115 });

        var result = Patterns.generateTrackWithVelocities(
                "Auto Drum", pieceAt120Bpm(1), Energy.MEDIUM,
                (bd, e, f, i) -> withVel);
        assertTrue(result.isPresent());
        var changes = result.get().velocities().changes();

        // 4 entries — one per slot.
        assertEquals(4, changes.size());
        assertEquals(new VelocityChange(0,    100), changes.get(0));
        assertEquals(new VelocityChange(500,  115), changes.get(1));
        assertEquals(new VelocityChange(1000, 95),  changes.get(2));
        assertEquals(new VelocityChange(1500, 115), changes.get(3));
    }

    /** Rest slots (null in sequence) don't produce velocity entries. */
    @Test
    void restSlotsAreSkipped() {
        // 4-slot quarter pattern with rests in slots 1 and 3.
        PatternSpec withRests = new PatternSpec(BaseValue.QUARTER,
                new PercussionSound[] {
                        PercussionSound.BASS_DRUM, null,
                        PercussionSound.ACOUSTIC_SNARE, null },
                // velocity at rest slots is ignored — but must still pass validation.
                new int[] { 100, 64, 110, 64 });

        var result = Patterns.generateTrackWithVelocities(
                "Auto Drum", pieceAt120Bpm(1), Energy.MEDIUM,
                (bd, e, f, i) -> withRests);
        assertTrue(result.isPresent());
        var changes = result.get().velocities().changes();

        // Only the two non-rest slots produce VelocityChanges.
        assertEquals(2, changes.size());
        assertEquals(new VelocityChange(0,    100), changes.get(0));
        assertEquals(new VelocityChange(1000, 110), changes.get(1));
    }

    /** Multi-bar piece — slot ms positions accumulate across bars. */
    @Test
    void slotPositionsAccumulateAcrossBars() {
        // Per-bar resolver: bar 0 emits vel 100, bar 1 vel 110, bar 2 vel 120
        // — distinct values so VelocityControl's same-velocity dedup
        // preserves all three entries.
        var bar0 = new PatternSpec(BaseValue.QUARTER,
                new PercussionSound[] { PercussionSound.BASS_DRUM, null, null, null },
                new int[] { 100, 64, 64, 64 });
        var bar1 = new PatternSpec(BaseValue.QUARTER,
                new PercussionSound[] { PercussionSound.BASS_DRUM, null, null, null },
                new int[] { 110, 64, 64, 64 });
        var bar2 = new PatternSpec(BaseValue.QUARTER,
                new PercussionSound[] { PercussionSound.BASS_DRUM, null, null, null },
                new int[] { 120, 64, 64, 64 });

        var result = Patterns.generateTrackWithVelocities(
                "Auto Drum", pieceAt120Bpm(3), Energy.MEDIUM,
                (bd, e, f, i) -> switch (i) {
                    case 0 -> bar0;
                    case 1 -> bar1;
                    case 2 -> bar2;
                    default -> bar0;
                });
        assertTrue(result.isPresent());
        var changes = result.get().velocities().changes();

        // One entry per bar at varying velocity → all three survive dedup.
        assertEquals(3, changes.size());
        assertEquals(new VelocityChange(0,    100), changes.get(0));
        assertEquals(new VelocityChange(2000, 110), changes.get(1));
        assertEquals(new VelocityChange(4000, 120), changes.get(2));
    }

    /** PatternSpec rejects out-of-range velocities at construction. */
    @Test
    void invalidVelocityRejected() {
        assertThrows(IllegalArgumentException.class, () -> new PatternSpec(
                BaseValue.QUARTER,
                new PercussionSound[] { PercussionSound.BASS_DRUM },
                new int[] { 0 }));   // 0 invalid (NOTE_OFF synonym)
        assertThrows(IllegalArgumentException.class, () -> new PatternSpec(
                BaseValue.QUARTER,
                new PercussionSound[] { PercussionSound.BASS_DRUM },
                new int[] { 128 })); // 128 out of MIDI range
    }

    /**
     * Bass-alignment boost — when a kick slot lines up with a source
     * bass onset, its velocity is bumped by Patterns.BASS_ALIGN_BOOST.
     * Slots that don't line up pass through unchanged.
     */
    @Test
    void kickGetsBassAlignmentBoostWhenSourceBassLandsOnSameBeat() {
        // Build a piece where the melody has a low (octave 2) note on
        // beat 1 — that's a bass onset at fractional position 0.0.
        var lowC = new SimplePitchNode(Pitch.of(NoteName.C, 2),
                Duration.of(BaseValue.QUARTER),
                Optional.empty(), List.of(), false, false);
        var rests = List.<PhraseNode>of(
                lowC,
                new music.notation.phrase.RestNode(Duration.of(BaseValue.QUARTER)),
                new music.notation.phrase.RestNode(Duration.of(BaseValue.QUARTER)),
                new music.notation.phrase.RestNode(Duration.of(BaseValue.QUARTER)));
        var bar = Bar.of(BD, rests.toArray(new PhraseNode[0]));
        var melody = new MelodicTrack("Melody", Instrument.ACOUSTIC_GRAND_PIANO,
                Phrase.of(List.of(bar)));
        var piece = new Piece("Test", "Test",
                new KeySignature(NoteName.C, Mode.MAJOR),
                new TimeSignature(4, 4),
                new Tempo(120, BaseValue.QUARTER),
                List.of(melody));

        // Pattern: kick on beats 1 and 3, snare on 2 and 4. All vel 100.
        // Beat 1 should get boost (bass aligned); beat 3 should not.
        PatternSpec spec = new PatternSpec(BaseValue.QUARTER,
                new PercussionSound[] {
                        PercussionSound.BASS_DRUM,
                        PercussionSound.ACOUSTIC_SNARE,
                        PercussionSound.BASS_DRUM,
                        PercussionSound.ACOUSTIC_SNARE },
                new int[] { 100, 100, 100, 100 });

        var result = Patterns.generateTrackWithVelocities(
                "Auto Drum", piece, Energy.MEDIUM,
                (bd, e, f, i) -> spec);
        assertTrue(result.isPresent());
        var ctrl = result.get().velocities();

        // 4/4 at 120bpm: 500ms per quarter slot.
        // Beat 1 kick aligned with source bass → +5 boost = 105
        assertEquals(105, ctrl.velocityAt(0),    "kick on 1 should get bass-align boost");
        // Beat 2 snare — no boost (snare not in scope)
        assertEquals(100, ctrl.velocityAt(500),  "snare on 2 unchanged");
        // Beat 3 kick — no source bass onset there → no boost
        assertEquals(100, ctrl.velocityAt(1000), "kick on 3 should NOT get boost");
        // Beat 4 snare — no boost
        assertEquals(100, ctrl.velocityAt(1500), "snare on 4 unchanged");
    }

    /** PatternSpec rejects velocity-array length mismatch. */
    @Test
    void velocityArrayLengthMustMatchSequence() {
        assertThrows(IllegalArgumentException.class, () -> new PatternSpec(
                BaseValue.QUARTER,
                new PercussionSound[] {
                        PercussionSound.BASS_DRUM, PercussionSound.ACOUSTIC_SNARE },
                new int[] { 100 }));   // length 1 vs sequence length 2
    }
}
