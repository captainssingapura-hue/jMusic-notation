package music.notation.play;

import music.notation.duration.Duration;
import music.notation.event.Instrument;
import music.notation.phrase.Bar;
import music.notation.phrase.PercussionNote;
import music.notation.phrase.PhraseNode;
import music.notation.pitch.NoteName;
import music.notation.pitch.StaffPitch;
import music.notation.event.PercussionSound;
import music.notation.phrase.PitchNode;
import music.notation.structure.DrumTrack;
import music.notation.structure.KeySignature;
import music.notation.structure.MelodicTrack;
import music.notation.structure.Mode;
import music.notation.structure.Piece;
import music.notation.structure.Tempo;
import music.notation.structure.TimeSignature;
import music.notation.structure.Track;
import org.junit.jupiter.api.Test;

import java.util.List;

import static music.notation.duration.BaseValue.QUARTER;
import static org.junit.jupiter.api.Assertions.*;

/** Pure tests for {@link ChannelSetup}'s factory + mapping rules. */
class ChannelSetupTest {

    private static final KeySignature C_MAJOR = new KeySignature(NoteName.C, Mode.MAJOR);
    private static final TimeSignature TS_4_4 = new TimeSignature(4, 4);
    private static final Tempo TEMPO_120 = new Tempo(120, QUARTER);

    private static MelodicTrack mt(String name, Instrument inst) {
        var node = PitchNode.of(StaffPitch.of(NoteName.C, 4), Duration.of(QUARTER));
        var bar = Bar.of(64, node, node, node, node);
        return MelodicTrack.of(name, inst, bar);
    }

    private static DrumTrack dt(String name) {
        PhraseNode hit = new PercussionNote(PercussionSound.BASS_DRUM, Duration.of(QUARTER));
        var bar = Bar.of(64, hit, hit, hit, hit);
        return DrumTrack.of(name, bar);
    }

    private static Piece pieceOf(Track... tracks) {
        return new Piece("T", "x", C_MAJOR, TS_4_4, TEMPO_120, List.of(tracks));
    }

    @Test
    void singlePitchedTrack_landsOnChannel0() {
        Piece piece = pieceOf(mt("Lead", Instrument.ACOUSTIC_GRAND_PIANO));
        var setup = ChannelSetup.from(piece, List.of(Instrument.FLUTE), List.of(80));

        assertEquals(1, setup.programs().size());
        assertEquals(Integer.valueOf(Instrument.FLUTE.program()), setup.programs().get(0));
        assertEquals(Integer.valueOf(80), setup.volumes().get(0));
    }

    @Test
    void mixedTracks_pitchedFillBeforeAndAfterChannel9_drumOnChannel9() {
        // 8 pitched + 1 drum → channels 0..7 + 9.
        var tracks = new Track[9];
        for (int i = 0; i < 8; i++) tracks[i] = mt("M" + i, Instrument.ACOUSTIC_GRAND_PIANO);
        tracks[8] = dt("Drums");
        Piece piece = pieceOf(tracks);

        var insList = new java.util.ArrayList<Instrument>();
        var volList = new java.util.ArrayList<Integer>();
        for (int i = 0; i < 9; i++) {
            insList.add(null);
            volList.add(null);
        }

        var setup = ChannelSetup.from(piece, insList, volList);

        for (int ch = 0; ch <= 7; ch++) {
            assertTrue(setup.programs().containsKey(ch), "channel " + ch + " should map a pitched track");
        }
        assertFalse(setup.programs().containsKey(8), "8 pitched only fill 0..7; channel 8 unused");
        assertTrue(setup.programs().containsKey(9), "drum channel 9 must map");
    }

    @Test
    void manyPitchedTracks_skipChannel9_thenContinue() {
        // 12 pitched → channels 0..8 then 10, 11, 12 (skipping 9).
        var tracks = new Track[12];
        for (int i = 0; i < 12; i++) tracks[i] = mt("M" + i, Instrument.ACOUSTIC_GRAND_PIANO);
        Piece piece = pieceOf(tracks);

        var insList = new java.util.ArrayList<Instrument>();
        var volList = new java.util.ArrayList<Integer>();
        for (int i = 0; i < 12; i++) { insList.add(null); volList.add(null); }

        var setup = ChannelSetup.from(piece, insList, volList);

        for (int ch = 0; ch <= 8; ch++) {
            assertTrue(setup.programs().containsKey(ch), "channel " + ch + " missing");
        }
        assertFalse(setup.programs().containsKey(9), "drum channel must NOT map a pitched track");
        for (int ch = 10; ch <= 12; ch++) {
            assertTrue(setup.programs().containsKey(ch), "channel " + ch + " missing");
        }
    }

    @Test
    void overflow_throws() {
        // 16 pitched: 0..8 + 10..15 = 15 slots; the 16th overflows.
        var tracks = new Track[16];
        for (int i = 0; i < 16; i++) tracks[i] = mt("M" + i, Instrument.ACOUSTIC_GRAND_PIANO);
        Piece piece = pieceOf(tracks);

        var insList = new java.util.ArrayList<Instrument>();
        var volList = new java.util.ArrayList<Integer>();
        for (int i = 0; i < 16; i++) { insList.add(null); volList.add(null); }

        var ex = assertThrows(IllegalStateException.class,
                () -> ChannelSetup.from(piece, insList, volList));
        assertTrue(ex.getMessage().contains("Too many pitched"));
    }

    @Test
    void nullEntries_fallBackToTrackDefault_andVolume100() {
        Piece piece = pieceOf(mt("Lead", Instrument.FRENCH_HORN));
        var setup = ChannelSetup.from(piece, List.of(), List.of());

        assertEquals(Integer.valueOf(Instrument.FRENCH_HORN.program()),
                setup.programs().get(0));
        assertEquals(Integer.valueOf(100), setup.volumes().get(0));
    }

    @Test
    void volumeIsClampedToMidiRange() {
        Piece piece = pieceOf(mt("Lead", Instrument.ACOUSTIC_GRAND_PIANO));
        var hi = ChannelSetup.from(piece, List.of(Instrument.FLUTE), List.of(200));
        var lo = ChannelSetup.from(piece, List.of(Instrument.FLUTE), List.of(-5));
        assertEquals(Integer.valueOf(127), hi.volumes().get(0));
        assertEquals(Integer.valueOf(0), lo.volumes().get(0));
    }

    @Test
    void recordEquality() {
        Piece piece = pieceOf(mt("Lead", Instrument.ACOUSTIC_GRAND_PIANO));
        var a = ChannelSetup.from(piece, List.of(Instrument.FLUTE), List.of(100));
        var b = ChannelSetup.from(piece, List.of(Instrument.FLUTE), List.of(100));
        assertEquals(a, b);
    }

    @Test
    void fromInstrumentsLeavesBanksEmpty() {
        Piece piece = pieceOf(mt("Lead", Instrument.ACOUSTIC_GRAND_PIANO));
        var setup = ChannelSetup.from(piece, List.of(Instrument.FLUTE), List.of(100));
        assertTrue(setup.banks().isEmpty(),
                "GM-only setup should not record any bank overrides");
    }

    @Test
    void fromPatchesGmOnlyMatchesFromInstruments() {
        Piece piece = pieceOf(mt("Lead", Instrument.ACOUSTIC_GRAND_PIANO));
        var fromIns = ChannelSetup.from(piece, List.of(Instrument.FLUTE), List.of(100));
        var fromPatches = ChannelSetup.fromPatches(piece,
                List.of(PatchRef.gm(Instrument.FLUTE)), List.of(100), null);
        assertEquals(fromIns.programs(), fromPatches.programs());
        assertEquals(fromIns.volumes(), fromPatches.volumes());
        assertTrue(fromPatches.banks().isEmpty());
    }

    @Test
    void fromPatchesCustomRecordsBank() {
        Piece piece = pieceOf(mt("Lead", Instrument.ACOUSTIC_GRAND_PIANO));
        var ref = PatchRef.custom(Instrument.ACOUSTIC_GRAND_PIANO, 1, 0, "Steinway");
        var setup = ChannelSetup.fromPatches(piece, List.of(ref), List.of(100), null);
        assertEquals(1, setup.banks().get(0));
        assertEquals(0, setup.programs().get(0));
    }

    @Test
    void fromPatchesDrumChannelOmitsBank() {
        Piece piece = pieceOf(mt("Lead", Instrument.ACOUSTIC_GRAND_PIANO), dt("D"));
        var refs = List.of(
                PatchRef.gm(Instrument.ACOUSTIC_GRAND_PIANO),
                PatchRef.custom(Instrument.DRUM_KIT, 128, 8, "Room Kit"));
        var setup = ChannelSetup.fromPatches(piece, refs, List.of(100, 100), null);
        // Drum on channel 9 — bank not recorded (GM ignores bank-select on rhythm).
        assertFalse(setup.banks().containsKey(9));
        // The kit's program (8 = Room Kit) does ride on channel 9.
        assertEquals(8, setup.programs().get(9));
    }
}
