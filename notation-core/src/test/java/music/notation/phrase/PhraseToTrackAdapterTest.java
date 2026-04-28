package music.notation.phrase;

import music.notation.duration.Duration;
import music.notation.event.ChordEvent;
import music.notation.event.Instrument;
import music.notation.event.PercussionSound;
import music.notation.pitch.NoteName;
import music.notation.pitch.Pitch;
import music.notation.pitch.StaffPitch;
import music.notation.structure.DrumTrack;
import music.notation.structure.MelodicTrack;
import music.notation.structure.TimeSignature;
import org.junit.jupiter.api.Test;

import java.util.List;

import static music.notation.duration.BaseValue.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 4b: locks the adapter contracts that convert the old
 * {@link AuthorPhrase} subtypes to the new {@link MelodicTrack} /
 * {@link DrumTrack} shapes.
 *
 * <p>The adapters are <b>lossy by design</b>: voices, phrase markings,
 * and chord-event articulations are dropped because the new Track types
 * don't model them yet. The structural content — bars, notes, chord
 * pitches, drum hits — round-trips faithfully.</p>
 */
class PhraseToTrackAdapterTest {

    private static final TimeSignature TS_4_4 = new TimeSignature(4, 4);
    private static final PhraseMarking ATTACCA = new PhraseMarking(PhraseConnection.ATTACCA, false);

    private static Pitch p(NoteName n, int octave) { return StaffPitch.of(n, octave); }

    // ── MelodicPhrase → MelodicTrack ────────────────────────────────

    @Test
    void melodicPhrase_toMelodicTrack_preservesBars() {
        var bar1 = Bar.of(64,
                PitchNode.of(p(NoteName.C, 4), Duration.of(QUARTER)),
                PitchNode.of(p(NoteName.D, 4), Duration.of(QUARTER)),
                PitchNode.of(p(NoteName.E, 4), Duration.of(HALF)));
        var bar2 = Bar.of(64,
                PitchNode.of(p(NoteName.F, 4), Duration.of(WHOLE)));
        var phrase = MelodicPhrase.fromBars(TS_4_4, ATTACCA, bar1, bar2);

        var track = phrase.toMelodicTrack("Lead", Instrument.ACOUSTIC_GRAND_PIANO);

        assertEquals("Lead", track.name());
        assertEquals(Instrument.ACOUSTIC_GRAND_PIANO, track.defaultInstrument());
        assertEquals(2, track.bars().size());
        assertEquals(bar1, track.bars().get(0));
        assertEquals(bar2, track.bars().get(1));
        assertTrue(track.auxTracks().isEmpty());
    }

    @Test
    void melodicPhrase_withoutBarStructure_wrapsNodesIntoSingleBar() {
        var phrase = new MelodicPhrase(
                List.of(
                        PitchNode.of(p(NoteName.C, 4), Duration.of(QUARTER)),
                        PitchNode.of(p(NoteName.D, 4), Duration.of(QUARTER))),
                ATTACCA);

        var track = phrase.toMelodicTrack("M", Instrument.ACOUSTIC_GRAND_PIANO);

        assertEquals(1, track.bars().size(), "loose nodes wrap into one bar");
        Bar onlyBar = track.bars().get(0);
        assertEquals(32, onlyBar.expectedSixtyFourths(), "two quarters = 32/64");
        assertEquals(2, onlyBar.nodes().size());
    }

    @Test
    void melodicPhrase_rejectsDrumKitInstrument() {
        var phrase = MelodicPhrase.fromBars(TS_4_4, ATTACCA,
                Bar.of(64, PitchNode.of(p(NoteName.C, 4), Duration.of(WHOLE))));
        assertThrows(IllegalArgumentException.class,
                () -> phrase.toMelodicTrack("Bad", Instrument.DRUM_KIT));
    }

    // ── ChordPhrase → MelodicTrack ──────────────────────────────────

    @Test
    void chordPhrase_toMelodicTrack_convertsChordsToPolyPitchNodes() {
        var phrase = new ChordPhrase(List.of(
                new ChordEvent(
                        List.of(p(NoteName.C, 4), p(NoteName.E, 4), p(NoteName.G, 4)),
                        Duration.of(QUARTER), List.of()),
                new ChordEvent(
                        List.of(p(NoteName.D, 4), p(NoteName.F, 4), p(NoteName.A, 4)),
                        Duration.of(HALF), List.of())
        ), ATTACCA);

        var track = phrase.toMelodicTrack("Chords", Instrument.ACOUSTIC_GRAND_PIANO);

        assertEquals(1, track.bars().size());
        Bar bar = track.bars().get(0);
        assertEquals(48, bar.expectedSixtyFourths(), "quarter + half = 16 + 32 = 48/64");
        assertEquals(2, bar.nodes().size());

        PolyPitchNode first = (PolyPitchNode) bar.nodes().get(0);
        assertEquals(3, first.pitches().size());
        assertEquals(NoteName.C, ((StaffPitch) first.pitches().get(0)).noteName());

        PolyPitchNode second = (PolyPitchNode) bar.nodes().get(1);
        assertEquals(3, second.pitches().size());
        assertEquals(NoteName.D, ((StaffPitch) second.pitches().get(0)).noteName());
    }

    @Test
    void chordPhrase_rejectsDrumKitInstrument() {
        var phrase = new ChordPhrase(List.of(
                new ChordEvent(List.of(p(NoteName.C, 4), p(NoteName.E, 4)),
                        Duration.of(QUARTER), List.of())
        ), ATTACCA);
        assertThrows(IllegalArgumentException.class,
                () -> phrase.toMelodicTrack("Bad", Instrument.DRUM_KIT));
    }

    // ── RestPhrase → MelodicTrack / DrumTrack ───────────────────────

    @Test
    void restPhrase_toMelodicTrack_singleBarWithOneRest() {
        var phrase = new RestPhrase(Duration.of(HALF), ATTACCA);

        var track = phrase.toMelodicTrack("Rest", Instrument.ACOUSTIC_GRAND_PIANO);

        assertEquals("Rest", track.name());
        assertEquals(1, track.bars().size());
        Bar bar = track.bars().get(0);
        assertEquals(32, bar.expectedSixtyFourths(), "half = 32/64");
        assertEquals(1, bar.nodes().size());
        assertTrue(bar.nodes().get(0) instanceof RestNode);
    }

    @Test
    void restPhrase_toDrumTrack_singleBarWithOneRest() {
        var phrase = new RestPhrase(Duration.of(QUARTER), ATTACCA);
        var track = phrase.toDrumTrack("Drums");
        assertEquals(1, track.bars().size());
        assertEquals(16, track.bars().get(0).expectedSixtyFourths());
        assertTrue(track.bars().get(0).nodes().get(0) instanceof RestNode);
    }

    @Test
    void restPhrase_rejectsDrumKitInstrument() {
        var phrase = new RestPhrase(Duration.of(QUARTER), ATTACCA);
        assertThrows(IllegalArgumentException.class,
                () -> phrase.toMelodicTrack("Bad", Instrument.DRUM_KIT));
    }

}
