package music.notation.play;

import music.notation.phrase.*;
import music.notation.pitch.StaffPitch;
import music.notation.structure.*;
import org.junit.jupiter.api.Test;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequence;
import java.util.List;

import static music.notation.duration.BaseValue.*;
import static music.notation.event.Instrument.ACOUSTIC_GRAND_PIANO;
import static music.notation.pitch.NoteName.*;
import static org.junit.jupiter.api.Assertions.*;

class TempoPlaybackTest {

    private static final KeySignature KEY = new KeySignature(C, Mode.MAJOR);
    private static final TimeSignature TS = new TimeSignature(4, 4);

    @Test
    void instantTempoChangeEmitsEvent() {
        var nodes = List.<PhraseNode>of(
                new TempoChangeNode(140),
                NoteNode.of(p(C, 4), QUARTER),
                NoteNode.of(p(D, 4), QUARTER),
                new RestNode(HALF)
        );
        var phrase = new MelodicPhrase(nodes, attacca());

        var interp = new PhraseInterpreter(0, 80, 120);
        interp.interpret(phrase);

        var tempoEvents = tempoEvents(interp);

        assertEquals(1, tempoEvents.size());
        assertEquals(0, tempoEvents.getFirst().tick());
        assertEquals(140, tempoEvents.getFirst().bpm());
    }

    @Test
    void tempoChangeAtMidPhrase() {
        var nodes = List.<PhraseNode>of(
                NoteNode.of(p(C, 4), QUARTER),
                new TempoChangeNode(90),
                NoteNode.of(p(D, 4), QUARTER),
                new RestNode(HALF)
        );
        var phrase = new MelodicPhrase(nodes, attacca());

        var interp = new PhraseInterpreter(0, 80, 120);
        interp.interpret(phrase);

        var tempoEvents = tempoEvents(interp);

        assertEquals(1, tempoEvents.size());
        assertEquals(MidiMapper.TICKS_PER_QUARTER, tempoEvents.getFirst().tick());
        assertEquals(90, tempoEvents.getFirst().bpm());
    }

    @Test
    void linearTransitionEmitsInterpolatedEvents() {
        // 4 quarter notes with transition from 120 to 60
        var nodes = List.<PhraseNode>of(
                new TempoTransitionStartNode(),
                NoteNode.of(p(C, 4), QUARTER),
                NoteNode.of(p(D, 4), QUARTER),
                NoteNode.of(p(E, 4), QUARTER),
                NoteNode.of(p(F, 4), QUARTER),
                new TempoTransitionEndNode(60, TransitionMethod.LINEAR)
        );
        var phrase = new MelodicPhrase(nodes, attacca());

        var interp = new PhraseInterpreter(0, 80, 120);
        interp.interpret(phrase);

        var tempoEvents = tempoEvents(interp);

        // 4 quarter notes = 4 * 480 = 1920 ticks range
        // Steps = 1920 / 480 = 4, so i = 0..4 -> 5 events
        assertEquals(5, tempoEvents.size());

        assertEquals(0, tempoEvents.getFirst().tick());
        assertEquals(120, tempoEvents.getFirst().bpm());

        assertEquals(4 * MidiMapper.TICKS_PER_QUARTER, tempoEvents.getLast().tick());
        assertEquals(60, tempoEvents.getLast().bpm());

        // Linear: 120, 105, 90, 75, 60
        for (int i = 0; i < tempoEvents.size(); i++) {
            int expectedBpm = 120 + (60 - 120) * i / 4;
            assertEquals(expectedBpm, tempoEvents.get(i).bpm(),
                    "Step " + i + " BPM mismatch");
        }
    }

    @Test
    void transitionAcrossPhraseBoundary() {
        var nodes1 = List.<PhraseNode>of(
                new TempoTransitionStartNode(),
                NoteNode.of(p(C, 4), WHOLE)
        );
        var phrase1 = new MelodicPhrase(nodes1, attacca());

        var nodes2 = List.<PhraseNode>of(
                NoteNode.of(p(D, 4), WHOLE),
                new TempoTransitionEndNode(80, TransitionMethod.LINEAR)
        );
        var phrase2 = new MelodicPhrase(nodes2, attacca());

        var interp = new PhraseInterpreter(0, 80, 120);
        interp.interpret(phrase1);
        interp.interpret(phrase2);

        var tempoEvents = tempoEvents(interp);

        assertTrue(tempoEvents.size() > 1, "Should emit multiple tempo events");
        assertEquals(120, tempoEvents.getFirst().bpm());
        assertEquals(80, tempoEvents.getLast().bpm());
    }

    @Test
    void shiftedPhrasePreservesTempoEvents() {
        var nodes = List.<PhraseNode>of(
                new TempoChangeNode(100),
                NoteNode.of(p(C, 4), WHOLE)
        );
        var inner = new MelodicPhrase(nodes, attacca());
        var shifted = new ShiftedPhrase(inner,
                new KeySignature(C, Mode.MAJOR),
                new KeySignature(D, Mode.MAJOR));

        var interp = new PhraseInterpreter(0, 80, 120);
        interp.interpret(shifted);

        var tempoEvents = tempoEvents(interp);

        assertEquals(1, tempoEvents.size());
        assertEquals(100, tempoEvents.getFirst().bpm());
    }

    @Test
    void buildSequenceEmitsTempoMetaMessages() throws InvalidMidiDataException {
        var P = StaffPhraseBuilderTyped.in(KEY, TS, QUARTER);
        var phrase = P.bar().tempo(140).o4(C).o4(D).o4(E).o4(F).done()
                .build(attacca());

        var piece = new Piece("Test", "Test", KEY, TS, new Tempo(120, QUARTER),
                List.of(Track.of("Piano", ACOUSTIC_GRAND_PIANO, List.of(phrase))));

        Sequence seq = MidiPlayer.buildSequence(piece);

        // Count tempo meta-messages (type 0x51) across all MIDI tracks
        int tempoMsgCount = 0;
        for (var track : seq.getTracks()) {
            for (int i = 0; i < track.size(); i++) {
                MidiEvent event = track.get(i);
                if (event.getMessage().getStatus() == 0xFF) {
                    byte[] data = event.getMessage().getMessage();
                    if (data.length >= 2 && data[1] == 0x51) {
                        tempoMsgCount++;
                    }
                }
            }
        }

        assertTrue(tempoMsgCount >= 1, "Should have at least one tempo meta-message");
    }

    @Test
    void unpairedTransitionStartIsHarmless() {
        var nodes = List.<PhraseNode>of(
                new TempoTransitionStartNode(),
                NoteNode.of(p(C, 4), WHOLE)
        );
        var phrase = new MelodicPhrase(nodes, attacca());

        var interp = new PhraseInterpreter(0, 80, 120);
        interp.interpret(phrase);

        var tempoEvents = tempoEvents(interp);
        assertTrue(tempoEvents.isEmpty(), "Unpaired start should not emit tempo events");
    }

    // ── Helpers ──

    private static StaffPitch p(music.notation.pitch.NoteName name, int octave) {
        return StaffPitch.of(name, octave);
    }

    private static PhraseMarking attacca() {
        return new PhraseMarking(PhraseConnection.ATTACCA, true);
    }

    private static List<PlayEvent.TempoChange> tempoEvents(PhraseInterpreter interp) {
        return interp.getEvents().stream()
                .filter(e -> e instanceof PlayEvent.TempoChange)
                .map(e -> (PlayEvent.TempoChange) e)
                .toList();
    }
}
