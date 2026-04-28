package music.notation.structure;

import music.notation.duration.Duration;
import music.notation.event.Instrument;
import music.notation.phrase.Bar;
import music.notation.phrase.BarPhrase;
import music.notation.phrase.PitchNode;
import music.notation.phrase.RestNode;
import music.notation.pitch.NoteName;
import music.notation.pitch.Pitch;
import org.junit.jupiter.api.Test;

import java.util.List;

import static music.notation.duration.BaseValue.QUARTER;
import static music.notation.duration.BaseValue.WHOLE;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 4a: locks the construction shape of the new
 * {@link MelodicTrack} and {@link DrumTrack} types. Not wired into the
 * build or playback pipeline yet.
 */
class MelodicTrackTest {

    private static Bar oneNoteBar() {
        return Bar.of(64, PitchNode.of(Pitch.of(NoteName.C, 4), Duration.of(WHOLE)));
    }

    private static Bar oneBeatRestBar() {
        return Bar.of(16, new RestNode(Duration.of(QUARTER)));
    }

    // ── MelodicTrack ──────────────────────────────────────────────

    @Test
    void melodicTrack_constructsWithBars() {
        var t = MelodicTrack.of("Melody", Instrument.ACOUSTIC_GRAND_PIANO, oneNoteBar());
        assertEquals("Melody", t.name());
        assertEquals(Instrument.ACOUSTIC_GRAND_PIANO, t.defaultInstrument());
        assertEquals(1, t.bars().size());
        assertTrue(t.auxTracks().isEmpty());
    }

    @Test
    void melodicTrack_acceptsAuxTracks() {
        var aux = MelodicTrack.of("Harmony", Instrument.STRING_ENSEMBLE_1, oneNoteBar());
        var main = new MelodicTrack(
                "Lead", Instrument.ACOUSTIC_GRAND_PIANO,
                BarPhrase.of(oneNoteBar()), List.of(aux));
        assertEquals(1, main.auxTracks().size());
        assertEquals("Harmony", main.auxTracks().get(0).name());
    }

    @Test
    void melodicTrack_rejectsBlankName() {
        assertThrows(IllegalArgumentException.class,
                () -> MelodicTrack.of("", Instrument.ACOUSTIC_GRAND_PIANO, oneNoteBar()));
        assertThrows(IllegalArgumentException.class,
                () -> MelodicTrack.of("   ", Instrument.ACOUSTIC_GRAND_PIANO, oneNoteBar()));
    }

    @Test
    void melodicTrack_rejectsDrumKitInstrument() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> MelodicTrack.of("Bad", Instrument.DRUM_KIT, oneNoteBar()));
        assertTrue(ex.getMessage().contains("DRUM_KIT"));
        assertTrue(ex.getMessage().contains("DrumTrack"));
    }

    @Test
    void melodicTrack_recordEquality() {
        var t1 = MelodicTrack.of("M", Instrument.ACOUSTIC_GRAND_PIANO, oneNoteBar());
        var t2 = MelodicTrack.of("M", Instrument.ACOUSTIC_GRAND_PIANO, oneNoteBar());
        assertEquals(t1, t2);
    }

    @Test
    void melodicTrack_defensivelyCopiesBars() {
        var bars = new java.util.ArrayList<Bar>();
        bars.add(oneNoteBar());
        var t = MelodicTrack.of("M", Instrument.ACOUSTIC_GRAND_PIANO, bars);
        bars.add(oneBeatRestBar()); // mutate after construction
        assertEquals(1, t.bars().size(), "Track must have copied the list");
    }

    // ── DrumTrack ─────────────────────────────────────────────────

    @Test
    void drumTrack_constructsWithBars() {
        var bar = Bar.of(16, new RestNode(Duration.of(QUARTER)));
        var t = DrumTrack.of("Drums", bar);
        assertEquals("Drums", t.name());
        assertEquals(1, t.bars().size());
        assertTrue(t.auxTracks().isEmpty());
    }

    @Test
    void drumTrack_acceptsAuxTracks() {
        var bar = Bar.of(16, new RestNode(Duration.of(QUARTER)));
        var aux = DrumTrack.of("DrumsAux", bar);
        var main = new DrumTrack("Drums", BarPhrase.of(bar), List.of(aux));
        assertEquals(1, main.auxTracks().size());
        assertEquals("DrumsAux", main.auxTracks().get(0).name());
    }

    @Test
    void drumTrack_rejectsBlankName() {
        var bar = Bar.of(16, new RestNode(Duration.of(QUARTER)));
        assertThrows(IllegalArgumentException.class, () -> DrumTrack.of("", bar));
    }

    @Test
    void drumTrack_recordEquality() {
        var bar = Bar.of(16, new RestNode(Duration.of(QUARTER)));
        var t1 = DrumTrack.of("D", bar);
        var t2 = DrumTrack.of("D", bar);
        assertEquals(t1, t2);
    }
}
