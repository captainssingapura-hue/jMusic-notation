package music.notation.experiments.performance;

import music.notation.experiments.chinese.gong.GongConcretizer;
import music.notation.experiments.chinese.gong.GongDegree;
import music.notation.experiments.chinese.gong.GongNote;
import music.notation.experiments.chord.ChordConcretizer;
import music.notation.experiments.chord.ChordProgression;
import music.notation.experiments.chord.ChordShape;
import music.notation.experiments.chord.ScaleChord;
import music.notation.performance.ConcreteNote;
import music.notation.performance.Articulations;
import music.notation.performance.Instrumentation;
import music.notation.performance.MidiCodec;
import music.notation.performance.Performance;
import music.notation.performance.PitchedNote;
import music.notation.performance.Score;
import music.notation.performance.TempoTrack;
import music.notation.performance.Track;
import music.notation.performance.TrackId;
import music.notation.performance.TrackKind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * MIDI round-trip checks that depend on experiment-only abstractions
 * (chord progressions, scale concretizers). Lives in notation-experiments
 * because notation-performance must not depend on experiments.
 */
class MidiCodecChordTest {

    @Test
    void wholeChordProgression_roundTrips() {
        var whole = buildProgression(ChordShape.BLOCK, /*program=*/ 24);
        assertEquals(whole, MidiCodec.fromMidi(MidiCodec.toMidi(whole)));
    }

    @Test
    void arpeggioProgression_roundTrips() {
        var whole = buildProgression(ChordShape.ARPEGGIO_UP, /*program=*/ -1);
        assertEquals(whole, MidiCodec.fromMidi(MidiCodec.toMidi(whole)));
    }

    @Test
    void abstractChord_toMidi_andBack_preservesStructure() {
        var chord = ScaleChord.block(1000,
                GongNote.of(GongDegree.I,   4),
                GongNote.of(GongDegree.III, 4),
                GongNote.of(GongDegree.V,   4));

        var trackId = new TrackId("chord");
        var directTrack = new ChordConcretizer<>(GongConcretizer.inC(), trackId).concretize(chord);
        var direct = new Performance(
                new Score(List.of(directTrack)),
                TempoTrack.empty(), Instrumentation.empty(), Articulations.empty());

        var viaMidi = MidiCodec.fromMidi(MidiCodec.toMidi(direct));
        assertEquals(direct, viaMidi);
    }

    private static Performance buildProgression(ChordShape shape, int program) {
        var trackId = new TrackId("chord");
        var concretizer = new ChordConcretizer<>(GongConcretizer.inC(), trackId);
        var notes = new ArrayList<ConcreteNote>();
        long cursor = 0;
        for (ScaleChord<GongNote> chord : ChordProgression.demoIn(shape)) {
            for (ConcreteNote n : concretizer.concretize(chord).notes()) {
                var pn = (PitchedNote) n;
                notes.add(new PitchedNote(pn.tickMs() + cursor, pn.durationMs(), pn.midi()));
            }
            cursor += chord.durationMs();
        }
        var track = new Track(trackId, TrackKind.PITCHED, notes);
        var instr = program >= 0
                ? Instrumentation.single(trackId, program)
                : Instrumentation.empty();
        return new Performance(new Score(List.of(track)), TempoTrack.empty(), instr, Articulations.empty());
    }
}
