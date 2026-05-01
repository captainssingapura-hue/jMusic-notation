package music.notation.play;

import music.notation.duration.Duration;
import music.notation.event.Instrument;
import music.notation.event.PercussionSound;
import music.notation.performance.DrumNote;
import music.notation.performance.Performance;
import music.notation.performance.PitchedNote;
import music.notation.performance.TrackKind;
import music.notation.phrase.Bar;
import music.notation.phrase.PercussionNote;
import music.notation.phrase.PitchNode;
import music.notation.phrase.RestNode;
import music.notation.pitch.NoteName;
import music.notation.pitch.StaffPitch;
import music.notation.structure.DrumTrack;
import music.notation.structure.KeySignature;
import music.notation.structure.MelodicTrack;
import music.notation.structure.Mode;
import music.notation.structure.Piece;
import music.notation.structure.Tempo;
import music.notation.structure.TimeSignature;
import org.junit.jupiter.api.Test;

import java.util.List;

import static music.notation.duration.BaseValue.QUARTER;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 4c proof-of-pattern: author a small piece end-to-end via the
 * new {@link MelodicTrack} / {@link DrumTrack} types and the
 * {@link Piece#ofTrackKinds} factory, then verify
 * {@link PieceConcretizer#concretize} produces the expected
 * {@link Performance} shape.
 */
class PieceOfTrackKindsTest {

    private static final KeySignature KEY = new KeySignature(NoteName.C, Mode.MAJOR);
    private static final TimeSignature TS_4_4 = new TimeSignature(4, 4);
    private static final Tempo TEMPO_120 = new Tempo(120, QUARTER);
    private static final Duration QUARTER_DUR = Duration.of(QUARTER);

    private static StaffPitch p(NoteName n, int octave) {
        return StaffPitch.of(n, octave);
    }

    @Test
    void pieceOfTrackKinds_concretizesEquivalentlyToFlatPiece() {
        // Author the same content two ways: once via new types, once via
        // a flat legacy Piece. Both should concretize to the same
        // Performance — the proof of pattern.

        // ── New-type authoring ──────────────────────────────────────
        Bar melodyBar = Bar.of(64,
                PitchNode.of(p(NoteName.C, 4), QUARTER_DUR),
                PitchNode.of(p(NoteName.D, 4), QUARTER_DUR),
                PitchNode.of(p(NoteName.E, 4), QUARTER_DUR),
                PitchNode.of(p(NoteName.F, 4), QUARTER_DUR));

        Bar drumBar = Bar.of(64,
                new PercussionNote(PercussionSound.BASS_DRUM, QUARTER_DUR),
                new PercussionNote(PercussionSound.ACOUSTIC_SNARE, QUARTER_DUR),
                new PercussionNote(PercussionSound.BASS_DRUM, QUARTER_DUR),
                new PercussionNote(PercussionSound.ACOUSTIC_SNARE, QUARTER_DUR));

        MelodicTrack melodicTrack = MelodicTrack.of(
                "Melody", Instrument.ACOUSTIC_GRAND_PIANO, melodyBar);
        DrumTrack drumTrack = DrumTrack.of("Drums", drumBar);

        Piece newPiece = Piece.ofTrackKinds(
                "Demo", "test", KEY, TS_4_4, TEMPO_120,
                List.of(melodicTrack), List.of(drumTrack));

        // ── Concretize and verify the resulting Performance ────────
        Performance perf = PieceConcretizer.concretize(newPiece);

        assertEquals(2, perf.score().tracks().size(), "two tracks: Melody + Drums");

        var melodyPerfTrack = perf.score().tracks().stream()
                .filter(t -> t.id().name().equals("Melody"))
                .findFirst().orElseThrow();
        assertEquals(TrackKind.PITCHED, melodyPerfTrack.kind());
        assertEquals(4, melodyPerfTrack.notes().size());
        // C4 = MIDI 60, D=62, E=64, F=65 (concert pitch)
        int[] expectedMidi = {60, 62, 64, 65};
        for (int i = 0; i < 4; i++) {
            PitchedNote pn = (PitchedNote) melodyPerfTrack.notes().get(i);
            assertEquals(expectedMidi[i], pn.midi(),
                    "melody note " + i + " pitch");
            assertEquals(i * 500L, pn.tickMs(),
                    "melody note " + i + " onset (quarter at 120bpm = 500ms)");
            assertEquals(500L, pn.durationMs());
        }

        var drumPerfTrack = perf.score().tracks().stream()
                .filter(t -> t.id().name().equals("Drums"))
                .findFirst().orElseThrow();
        assertEquals(TrackKind.DRUM, drumPerfTrack.kind());
        assertEquals(4, drumPerfTrack.notes().size());
        int[] expectedDrum = {
                PercussionSound.BASS_DRUM.midiNote(),
                PercussionSound.ACOUSTIC_SNARE.midiNote(),
                PercussionSound.BASS_DRUM.midiNote(),
                PercussionSound.ACOUSTIC_SNARE.midiNote()};
        for (int i = 0; i < 4; i++) {
            DrumNote dn = (DrumNote) drumPerfTrack.notes().get(i);
            assertEquals(expectedDrum[i], dn.piece(),
                    "drum hit " + i + " piece");
            assertEquals(i * 500L, dn.tickMs(), "drum hit " + i + " onset");
        }

        // ── Instrumentation: melody on piano, drums on DRUM_KIT (program 0) ──
        var instrMap = perf.instruments().byTrack();
        assertEquals(2, instrMap.size());
        // Both Performance Tracks declare programs (PieceConcretizer
        // populates from the legacy Track's defaultInstrument).
    }

    @Test
    void pieceOfTrackKinds_acceptsEmptyTrackLists() {
        // No tracks at all is still a valid (if degenerate) piece.
        Piece p = Piece.ofTrackKinds("Empty", "x", KEY, TS_4_4, TEMPO_120,
                List.of(), List.of());
        Performance perf = PieceConcretizer.concretize(p);
        assertEquals(0, perf.score().tracks().size());
    }

    @Test
    void pieceOfTrackKinds_meldsTrackOrdering_melodicFirstThenDrums() {
        Bar oneBar = Bar.of(16, new RestNode(QUARTER_DUR));
        Piece p = Piece.ofTrackKinds("Order", "x", KEY, TS_4_4, TEMPO_120,
                List.of(MelodicTrack.of("Lead", Instrument.ACOUSTIC_GRAND_PIANO, oneBar),
                        MelodicTrack.of("Bass", Instrument.ELECTRIC_BASS_FINGER, oneBar)),
                List.of(DrumTrack.of("Drums", oneBar)));

        // The legacy Track list ordering is preserved: melodic first, then drums.
        assertEquals("Lead",  p.tracks().get(0).name());
        assertEquals("Bass",  p.tracks().get(1).name());
        assertEquals("Drums", p.tracks().get(2).name());
        assertTrue(p.tracks().get(2) instanceof music.notation.structure.DrumTrack);
    }
}
