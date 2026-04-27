package music.notation.play;

import music.notation.duration.Duration;
import music.notation.event.ChordEvent;
import music.notation.event.Instrument;
import music.notation.event.PercussionSound;
import music.notation.performance.Articulations;
import music.notation.performance.ConcreteNote;
import music.notation.performance.DrumNote;
import music.notation.performance.InstrumentControl;
import music.notation.performance.Instrumentation;
import music.notation.performance.Performance;
import music.notation.performance.PitchedNote;
import music.notation.performance.Score;
import music.notation.performance.TempoChange;
import music.notation.performance.TempoTrack;
import music.notation.performance.Track;
import music.notation.performance.TrackId;
import music.notation.performance.TrackKind;
import music.notation.phrase.*;
import music.notation.pitch.NoteName;
import music.notation.pitch.Pitch;
import music.notation.structure.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static music.notation.duration.BaseValue.*;
import static org.junit.jupiter.api.Assertions.*;

class PieceConcretizerTest {

    private static final KeySignature C_MAJOR = new KeySignature(NoteName.C, Mode.MAJOR);
    private static final TimeSignature TS_4_4 = new TimeSignature(4, 4);
    private static final Tempo TEMPO_120 = new Tempo(120, QUARTER);

    private static PhraseMarking attacca() { return new PhraseMarking(PhraseConnection.ATTACCA, false); }
    private static PhraseMarking breath()  { return new PhraseMarking(PhraseConnection.BREATH, false); }
    private static PhraseMarking caesura() { return new PhraseMarking(PhraseConnection.CAESURA, false); }

    private static Piece pieceOf(Tempo tempo, music.notation.structure.Track... tracks) {
        return new Piece("Test", "T", C_MAJOR, TS_4_4, tempo, List.of(tracks));
    }

    private static music.notation.structure.Track track(String name, Instrument inst, Phrase... phrases) {
        return new music.notation.structure.Track(name, inst, List.of(phrases), List.of());
    }

    @Test
    void singleNotePiece_concretizes() {
        // C4 quarter at 120bpm = 500 ms
        var phrase = new MelodicPhrase(
                List.of(PitchNode.of(Pitch.of(NoteName.C, 4), Duration.of(QUARTER))),
                attacca());
        var piece = pieceOf(TEMPO_120, track("M", Instrument.ACOUSTIC_GRAND_PIANO, phrase));

        Performance p = PieceConcretizer.concretize(piece);

        assertEquals(1, p.score().tracks().size());
        Track t = p.score().tracks().get(0);
        assertEquals("M", t.id().name());
        assertEquals(TrackKind.PITCHED, t.kind());
        assertEquals(1, t.notes().size());
        PitchedNote n = (PitchedNote) t.notes().get(0);
        assertEquals(0, n.tickMs());
        assertEquals(500, n.durationMs());
        assertEquals(60, n.midi());
    }

    @Test
    void chordPiece_concretizes() {
        var poly = PitchNode.poly(Duration.of(QUARTER),
                Pitch.of(NoteName.C, 4), Pitch.of(NoteName.E, 4), Pitch.of(NoteName.G, 4));
        var phrase = new MelodicPhrase(List.of(poly), attacca());
        var piece = pieceOf(TEMPO_120, track("M", Instrument.ACOUSTIC_GRAND_PIANO, phrase));

        Performance p = PieceConcretizer.concretize(piece);
        Track t = p.score().tracks().get(0);
        assertEquals(3, t.notes().size());
        for (ConcreteNote cn : t.notes()) {
            PitchedNote pn = (PitchedNote) cn;
            assertEquals(0, pn.tickMs());
            assertEquals(500, pn.durationMs());
        }
        // Sorted canonical: by midi ascending at same tick
        assertEquals(60, ((PitchedNote) t.notes().get(0)).midi());
        assertEquals(64, ((PitchedNote) t.notes().get(1)).midi());
        assertEquals(67, ((PitchedNote) t.notes().get(2)).midi());
    }

    @Test
    void multiTrackPiece_concretizes() {
        var p1 = new MelodicPhrase(
                List.of(PitchNode.of(Pitch.of(NoteName.C, 4), Duration.of(QUARTER))), attacca());
        var p2 = new MelodicPhrase(
                List.of(PitchNode.of(Pitch.of(NoteName.G, 4), Duration.of(QUARTER))), attacca());
        var piece = pieceOf(TEMPO_120,
                track("Lead", Instrument.FLUTE, p1),
                track("Bass", Instrument.ACOUSTIC_BASS, p2));

        Performance p = PieceConcretizer.concretize(piece);
        assertEquals(2, p.score().tracks().size());
        assertEquals(Instrument.FLUTE.program(),
                p.instruments().byTrack().get(new TrackId("Lead")).changes().get(0).program());
        assertEquals(Instrument.ACOUSTIC_BASS.program(),
                p.instruments().byTrack().get(new TrackId("Bass")).changes().get(0).program());
    }

    @Test
    void drumTrackPiece_concretizes() {
        var dp = new DrumPhrase(List.of(
                new PercussionNote(PercussionSound.BASS_DRUM, Duration.of(QUARTER)),
                new PercussionNote(PercussionSound.ACOUSTIC_SNARE, Duration.of(QUARTER))
        ), attacca());
        var piece = pieceOf(TEMPO_120, track("D", Instrument.DRUM_KIT, dp));

        Performance p = PieceConcretizer.concretize(piece);
        Track t = p.score().tracks().get(0);
        assertEquals(TrackKind.DRUM, t.kind());
        assertEquals(2, t.notes().size());
        DrumNote n0 = (DrumNote) t.notes().get(0);
        DrumNote n1 = (DrumNote) t.notes().get(1);
        assertEquals(PercussionSound.BASS_DRUM.midiNote(), n0.piece());
        assertEquals(0, n0.tickMs());
        assertEquals(500, n0.durationMs());
        assertEquals(PercussionSound.ACOUSTIC_SNARE.midiNote(), n1.piece());
        assertEquals(500, n1.tickMs());
    }

    @Test
    void tempoChangePiece_concretizes() {
        // Quarter at 120 = 500ms; then change to 90; quarter at 90 = ~666ms
        var phrase = new MelodicPhrase(List.of(
                PitchNode.of(Pitch.of(NoteName.C, 4), Duration.of(QUARTER)),
                new TempoChangeNode(90),
                PitchNode.of(Pitch.of(NoteName.D, 4), Duration.of(QUARTER))
        ), attacca());
        var piece = pieceOf(TEMPO_120, track("M", Instrument.ACOUSTIC_GRAND_PIANO, phrase));

        Performance p = PieceConcretizer.concretize(piece);
        TempoTrack tt = p.tempo();
        assertEquals(2, tt.changes().size(), "expected initial+changed tempo entries");
        assertEquals(0, tt.changes().get(0).tickMs());
        assertEquals(120, tt.changes().get(0).bpm());
        assertEquals(500, tt.changes().get(1).tickMs());
        assertEquals(90, tt.changes().get(1).bpm());
        // Second note starts at 500ms, lasts 666 or 667ms (quarter at 90 bpm)
        Track t = p.score().tracks().get(0);
        PitchedNote n2 = (PitchedNote) t.notes().get(1);
        assertEquals(500, n2.tickMs());
        long expected = Math.round(60_000.0 / 90.0); // 1 quarter @ 90bpm
        assertEquals(expected, n2.durationMs(), 1);
    }

    @Test
    void restPhraseAdvancesCursor() {
        // Quarter, then quarter rest, then quarter — second note at 500+500=1000ms
        var p1 = new MelodicPhrase(
                List.of(PitchNode.of(Pitch.of(NoteName.C, 4), Duration.of(QUARTER))), attacca());
        var rest = new RestPhrase(Duration.of(QUARTER), attacca());
        var p2 = new MelodicPhrase(
                List.of(PitchNode.of(Pitch.of(NoteName.D, 4), Duration.of(QUARTER))), attacca());
        var piece = pieceOf(TEMPO_120, track("M", Instrument.ACOUSTIC_GRAND_PIANO, p1, rest, p2));

        Performance p = PieceConcretizer.concretize(piece);
        Track t = p.score().tracks().get(0);
        assertEquals(2, t.notes().size());
        assertEquals(0, ((PitchedNote) t.notes().get(0)).tickMs());
        assertEquals(1000, ((PitchedNote) t.notes().get(1)).tickMs());
    }

    @Test
    void boundaryGap_breath_caesura_attacca() {
        // Build three pieces with each connection between two quarter-note phrases.
        // BREATH = TICKS/4 = 120 ticks = 125ms at 120bpm
        // CAESURA = TICKS = 480 ticks = 500ms at 120bpm
        // ATTACCA = 0
        long quarterMs = 500;
        for (var spec : List.of(
                new Object[]{PhraseConnection.BREATH, 125L},
                new Object[]{PhraseConnection.CAESURA, 500L},
                new Object[]{PhraseConnection.ATTACCA, 0L}
        )) {
            PhraseConnection conn = (PhraseConnection) spec[0];
            long expectedGap = (Long) spec[1];

            var p1 = new MelodicPhrase(
                    List.of(PitchNode.of(Pitch.of(NoteName.C, 4), Duration.of(QUARTER))),
                    new PhraseMarking(conn, false));
            var p2 = new MelodicPhrase(
                    List.of(PitchNode.of(Pitch.of(NoteName.D, 4), Duration.of(QUARTER))), attacca());
            var piece = pieceOf(TEMPO_120, track("M", Instrument.ACOUSTIC_GRAND_PIANO, p1, p2));

            Performance p = PieceConcretizer.concretize(piece);
            Track t = p.score().tracks().get(0);
            long secondTick = ((PitchedNote) t.notes().get(1)).tickMs();
            assertEquals(quarterMs + expectedGap, secondTick,
                    "for connection " + conn);
        }
    }

    @Test
    void voiceOverlay_emitsOverlappingNotes() {
        // Main: one bar of 4 quarters of C4. Overlay: one bar of 4 quarters of E4.
        var mainBar = Bar.of(64,
                PitchNode.of(Pitch.of(NoteName.C, 4), Duration.of(QUARTER)),
                PitchNode.of(Pitch.of(NoteName.C, 4), Duration.of(QUARTER)),
                PitchNode.of(Pitch.of(NoteName.C, 4), Duration.of(QUARTER)),
                PitchNode.of(Pitch.of(NoteName.C, 4), Duration.of(QUARTER)));
        var overlayBar = Bar.of(64,
                PitchNode.of(Pitch.of(NoteName.E, 4), Duration.of(QUARTER)),
                PitchNode.of(Pitch.of(NoteName.E, 4), Duration.of(QUARTER)),
                PitchNode.of(Pitch.of(NoteName.E, 4), Duration.of(QUARTER)),
                PitchNode.of(Pitch.of(NoteName.E, 4), Duration.of(QUARTER)));
        var voice = new VoiceOverlay(List.of(java.util.Optional.of(overlayBar)));
        var phrase = new MelodicPhrase(mainBar.nodes(), List.of(mainBar), attacca(), List.of(voice));
        var piece = pieceOf(TEMPO_120, track("M", Instrument.ACOUSTIC_GRAND_PIANO, phrase));

        Performance p = PieceConcretizer.concretize(piece);
        Track t = p.score().tracks().get(0);
        // 4 main + 4 overlay = 8 notes on same track, overlapping at every 500ms
        assertEquals(8, t.notes().size());
        // At tick 0, both 60 and 64 should appear (canonical sort: 60 before 64)
        PitchedNote at0Lo = (PitchedNote) t.notes().get(0);
        PitchedNote at0Hi = (PitchedNote) t.notes().get(1);
        assertEquals(0, at0Lo.tickMs());
        assertEquals(60, at0Lo.midi());
        assertEquals(0, at0Hi.tickMs());
        assertEquals(64, at0Hi.midi());
    }

    @Test
    void tieFlag_propagatesToConcrete_pairSurvivesWithFlag() {
        // Tie merge has been removed (Phase 3-tie-cleanup): both source
        // PitchNodes survive into the Performance, with the first carrying
        // tiedToNext = true. Codec-level coalescing of tied chains is a
        // future phase; until then, tied notes are emitted as separate
        // re-articulated notes at MIDI render time — sonic regression
        // accepted. See .docs/agent-delegation-retrospective.md.
        var bar = Bar.of(64,
                PitchNode.of(Pitch.of(NoteName.C, 4), Duration.of(QUARTER)).withTiedToNext(),
                PitchNode.of(Pitch.of(NoteName.C, 4), Duration.of(QUARTER)),
                PitchNode.of(Pitch.of(NoteName.D, 4), Duration.of(HALF)));
        var phrase = MelodicPhrase.fromBars(TS_4_4, attacca(), bar);
        var piece = pieceOf(TEMPO_120, track("M", Instrument.ACOUSTIC_GRAND_PIANO, phrase));

        Performance p = PieceConcretizer.concretize(piece);
        Track t = p.score().tracks().get(0);
        assertEquals(3, t.notes().size(),
                "tied pair survives as two separate notes plus the trailing D");

        PitchedNote first = (PitchedNote) t.notes().get(0);
        PitchedNote second = (PitchedNote) t.notes().get(1);
        PitchedNote tail = (PitchedNote) t.notes().get(2);

        assertEquals(0, first.tickMs());
        assertEquals(500, first.durationMs(), "quarter at 120bpm = 500ms");
        assertEquals(60, first.midi());
        assertTrue(first.tiedToNext(), "first carries the tie flag");

        assertEquals(500, second.tickMs(), "second starts where first ends");
        assertEquals(500, second.durationMs());
        assertEquals(60, second.midi());
        assertFalse(second.tiedToNext());

        assertEquals(1000, tail.tickMs());
        assertEquals(1000, tail.durationMs(), "D half-note = 1000ms");
        assertEquals(62, tail.midi());
    }

    @Test
    void emptyPiece_returnsEmptyPerformance() {
        var piece = pieceOf(TEMPO_120);
        Performance p = PieceConcretizer.concretize(piece);
        assertEquals(0, p.score().tracks().size());
    }
}
