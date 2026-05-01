package music.notation.structure;

import music.notation.duration.Duration;
import music.notation.event.Instrument;
import music.notation.phrase.Bar;
import music.notation.phrase.Phrase;
import music.notation.phrase.PitchNode;
import music.notation.phrase.RestNode;
import music.notation.pitch.NoteName;
import music.notation.pitch.Pitch;
import org.junit.jupiter.api.Test;

import static music.notation.duration.BaseValue.QUARTER;
import static music.notation.duration.BaseValue.WHOLE;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Construction-shape tests for {@link MelodicTrack} and {@link DrumTrack}.
 * Aux behaviour now lives on {@link Phrase}; assertions about aux content
 * are in {@code LeafPhraseAuxTest} / {@code JoinedPhraseAuxTest}.
 */
class MelodicTrackTest {

    private static Bar oneNoteBar() {
        return Bar.of(64, PitchNode.of(Pitch.of(NoteName.C, 4), Duration.of(WHOLE)));
    }

    private static Bar oneBeatRestBar() {
        return Bar.of(16, new RestNode(Duration.of(QUARTER)));
    }

    @Test
    void melodicTrack_constructsWithBars() {
        var t = MelodicTrack.of("Melody", Instrument.ACOUSTIC_GRAND_PIANO, oneNoteBar());
        assertEquals("Melody", t.name());
        assertEquals(Instrument.ACOUSTIC_GRAND_PIANO, t.defaultInstrument());
        assertEquals(1, t.bars().size());
        assertTrue(t.auxBars().isEmpty());
    }

    @Test
    void melodicTrack_delegatesAuxToPhrase() {
        var phrase = Phrase.of(java.util.List.of(oneNoteBar(), oneNoteBar()),
                java.util.Map.of("Harmony", java.util.Map.of(0, oneNoteBar())));
        var track = new MelodicTrack("Lead", Instrument.ACOUSTIC_GRAND_PIANO, phrase);
        var dense = track.auxBars();
        assertEquals(1, dense.size());
        assertEquals(2, dense.get("Harmony").size());
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
    void drumTrack_constructsWithBars() {
        var bar = Bar.of(16, new RestNode(Duration.of(QUARTER)));
        var t = DrumTrack.of("Drums", bar);
        assertEquals("Drums", t.name());
        assertEquals(1, t.bars().size());
    }

    @Test
    void drumTrack_rejectsBlankName() {
        var bar = Bar.of(16, new RestNode(Duration.of(QUARTER)));
        assertThrows(IllegalArgumentException.class, () -> DrumTrack.of("", bar));
    }
}
