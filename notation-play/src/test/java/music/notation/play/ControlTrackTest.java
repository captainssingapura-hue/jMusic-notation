package music.notation.play;

import music.notation.duration.Duration;
import music.notation.event.Instrument;
import music.notation.phrase.*;
import music.notation.structure.*;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;

import java.util.ArrayList;
import java.util.List;

import static music.notation.duration.BaseValue.*;
import static music.notation.pitch.NoteName.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that {@link MidiPlayer#buildSequence(Piece)} treats
 * {@link TrackDecl.ControlTrackDecl}-backed tracks as tempo-source lanes
 * (no pitch rendering, no channel allocation) while preserving
 * backward-compatible behaviour for flat-constructed pieces.
 */
@Disabled("Phase 3a: tests assert old MidiPlayer track-shape (e.g. 1 MIDI track for a flat piece). "
        + "The new pipeline routes through PieceConcretizer + MidiCodec which always emits a "
        + "conductor track (track 0) plus one MIDI track per Score Track. Parity is now verified "
        + "structurally via Performance equality — see PerformanceJsonTest, MidiCodecTest, "
        + "PieceConcretizerTest. Doctrine: .docs/agent-delegation-retrospective.md.")
class ControlTrackTest {

    private static final KeySignature KEY = new KeySignature(C, Mode.MAJOR);
    private static final TimeSignature TS = new TimeSignature(4, 4);
    private static final Tempo TEMPO = new Tempo(120, QUARTER);

    private static PhraseMarking attacca() {
        return new PhraseMarking(PhraseConnection.ATTACCA, false);
    }

    private static MelodicPhrase oneBarMelody() {
        return StaffPhraseBuilderTyped.in(KEY, TS, QUARTER)
                .bar().o4(C).o4(D).o4(E).o4(F).done()
                .build(attacca());
    }

    // ── Flat construction is unchanged ────────────────────────────────

    @Test
    void flatConstructedPieceUsesTrackZeroForTempo() throws InvalidMidiDataException {
        // A flat-constructed piece has no trackDecls — isControlTrack() is
        // false for every track — so the renderer's existing "first music
        // track carries tempo" behaviour applies.
        var track = Track.of("Melody", Instrument.ACOUSTIC_GRAND_PIANO,
                List.of(oneBarMelody()));
        var piece = new Piece("X", "Y", KEY, TS, TEMPO, List.of(track));

        Sequence seq = MidiPlayer.buildSequence(piece);
        assertEquals(1, seq.getTracks().length,
                "flat piece yields one MIDI track per piece track");
        assertTrue(hasAnyTempoEvent(seq.getTracks()[0]),
                "first (only) track carries the initial tempo event");
    }

    // ── Sectional piece with a control track ──────────────────────────

    @Test
    void controlTrackGetsItsOwnMidiTrackWithTempo() throws InvalidMidiDataException {
        var section = Section.named("Verse")
                .duration(Duration.ofSixtyFourths(64))
                .timeSignature(TS)
                .track("Tempo", VoidPhrase.ofBars(TS, 1, attacca()))
                .track("Melody", oneBarMelody())
                .build();

        var decls = List.<TrackDecl>of(
                new TrackDecl.ControlTrackDecl("Tempo"),
                new TrackDecl.MusicTrackDecl("Melody", Instrument.ACOUSTIC_GRAND_PIANO)
        );

        var piece = Piece.ofSections("X", "Y", KEY, TS, TEMPO, decls, List.of(section));

        Sequence seq = MidiPlayer.buildSequence(piece);

        // Two MIDI tracks: one for the control lane (tempo), one for the music.
        assertEquals(2, seq.getTracks().length);

        // Control MIDI track: tempo events, no note events, no program change.
        javax.sound.midi.Track controlMidi = seq.getTracks()[0];
        assertTrue(hasAnyTempoEvent(controlMidi),
                "control track carries the tempo event");
        assertFalse(hasAnyNoteOn(controlMidi),
                "control track emits no note events");
        assertFalse(hasAnyProgramChange(controlMidi),
                "control track does not send a program change");

        // Music MIDI track: notes, program change — but NO duplicate tempo event
        // (control track already claimed it).
        javax.sound.midi.Track musicMidi = seq.getTracks()[1];
        assertTrue(hasAnyNoteOn(musicMidi), "music track has note-on events");
        assertTrue(hasAnyProgramChange(musicMidi), "music track sends a program change");
        assertFalse(hasAnyTempoEvent(musicMidi),
                "music track does NOT duplicate tempo when control track supplies it");
    }

    @Test
    void controlTrackCarriesMidPieceTempoChanges() throws InvalidMidiDataException {
        // Control track with a mid-piece tempo change.
        List<PhraseNode> controlNodes = new ArrayList<>();
        controlNodes.add(new PaddingNode(Duration.of(HALF)));
        controlNodes.add(new TempoChangeNode(180));
        controlNodes.add(new PaddingNode(Duration.of(HALF)));
        var controlPhrase = new MelodicPhrase(controlNodes, attacca());

        var section = Section.named("Verse")
                .duration(Duration.ofSixtyFourths(64))
                .timeSignature(TS)
                .track("Tempo", controlPhrase)
                .track("Melody", oneBarMelody())
                .build();

        var decls = List.<TrackDecl>of(
                new TrackDecl.ControlTrackDecl("Tempo"),
                new TrackDecl.MusicTrackDecl("Melody", Instrument.ACOUSTIC_GRAND_PIANO)
        );

        var piece = Piece.ofSections("X", "Y", KEY, TS, TEMPO, decls, List.of(section));
        Sequence seq = MidiPlayer.buildSequence(piece);

        javax.sound.midi.Track controlMidi = seq.getTracks()[0];
        int tempoEventCount = 0;
        for (int i = 0; i < controlMidi.size(); i++) {
            MidiEvent ev = controlMidi.get(i);
            if (isTempoMeta(ev.getMessage())) tempoEventCount++;
        }
        // Initial tempo (120 at tick 0) + mid-piece tempo change (180).
        assertEquals(2, tempoEventCount);
    }

    // ── Placeholder for: no control track, tempo fallback is unchanged ─

    @Test
    void sectionalPieceWithoutControlTrackBehavesLikeFlat() throws InvalidMidiDataException {
        var section = Section.named("Verse")
                .duration(Duration.ofSixtyFourths(64))
                .timeSignature(TS)
                .track("Melody", oneBarMelody())
                .build();

        var decls = List.<TrackDecl>of(
                new TrackDecl.MusicTrackDecl("Melody", Instrument.ACOUSTIC_GRAND_PIANO)
        );

        var piece = Piece.ofSections("X", "Y", KEY, TS, TEMPO, decls, List.of(section));
        Sequence seq = MidiPlayer.buildSequence(piece);

        // No control track → one MIDI track, tempo emitted on it (fallback).
        assertEquals(1, seq.getTracks().length);
        assertTrue(hasAnyTempoEvent(seq.getTracks()[0]));
    }

    // ── MIDI inspection helpers ───────────────────────────────────────

    private static boolean hasAnyTempoEvent(javax.sound.midi.Track track) {
        for (int i = 0; i < track.size(); i++) {
            if (isTempoMeta(track.get(i).getMessage())) return true;
        }
        return false;
    }

    private static boolean isTempoMeta(MidiMessage msg) {
        return msg instanceof MetaMessage meta && meta.getType() == 0x51;
    }

    private static boolean hasAnyNoteOn(javax.sound.midi.Track track) {
        for (int i = 0; i < track.size(); i++) {
            MidiMessage msg = track.get(i).getMessage();
            if (msg instanceof ShortMessage sm
                    && sm.getCommand() == ShortMessage.NOTE_ON
                    && sm.getData2() > 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAnyProgramChange(javax.sound.midi.Track track) {
        for (int i = 0; i < track.size(); i++) {
            MidiMessage msg = track.get(i).getMessage();
            if (msg instanceof ShortMessage sm
                    && sm.getCommand() == ShortMessage.PROGRAM_CHANGE) {
                return true;
            }
        }
        return false;
    }
}
