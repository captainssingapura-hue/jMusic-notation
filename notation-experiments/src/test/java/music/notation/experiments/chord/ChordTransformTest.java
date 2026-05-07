package music.notation.experiments.chord;

import music.notation.core.model.transformation.Transformer;
import music.notation.experiments.chinese.gong.GongConcretizer;
import music.notation.experiments.chinese.gong.GongDegree;
import music.notation.experiments.chinese.gong.GongNote;
import music.notation.experiments.hirajoshi.HirajoshiConcretizer;
import music.notation.experiments.hirajoshi.HirajoshiDegree;
import music.notation.experiments.hirajoshi.HirajoshiNote;
import music.notation.performance.ConcreteNote;
import music.notation.performance.PitchedNote;
import music.notation.performance.Track;
import music.notation.expressivity.TrackId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chord abstract model tests - now that concretization produces a
 * unified {@link Track}, assertions are on track contents rather than
 * a dedicated chord type.
 */
class ChordTransformTest {

    private static final TrackId TID = new TrackId("chord");

    // -- Block chord -> track --

    @Test
    void blockChord_concretizesToSimultaneousNoteEvents() {
        var chord = ScaleChord.block(1000,
                GongNote.of(GongDegree.I,   4),
                GongNote.of(GongDegree.III, 4),
                GongNote.of(GongDegree.V,   4));

        var track = new ChordConcretizer<>(GongConcretizer.inC(), TID).concretize(chord);

        var notes = onlyNotes(track);
        assertEquals(3, notes.size());
        for (var n : notes) {
            assertEquals(0, n.tickMs());
            assertEquals(1000, n.offTickMs());
        }
        assertEquals(List.of(60, 64, 67), notes.stream().map(PitchedNote::midi).toList());
    }

    @Test
    void arpeggioUp_staggerEventsAtEqualSlots() {
        var chord = new ScaleChord<>(
                List.of(GongNote.of(GongDegree.I,   4),
                        GongNote.of(GongDegree.III, 4),
                        GongNote.of(GongDegree.V,   4)),
                900, ChordShape.ARPEGGIO_UP);

        var notes = onlyNotes(new ChordConcretizer<>(GongConcretizer.inC(), TID).concretize(chord));

        assertEquals(3, notes.size());
        assertEquals(60, notes.get(0).midi()); assertEquals(0,   notes.get(0).tickMs());
        assertEquals(64, notes.get(1).midi()); assertEquals(300, notes.get(1).tickMs());
        assertEquals(67, notes.get(2).midi()); assertEquals(600, notes.get(2).tickMs());
        assertEquals(300, notes.get(0).durationMs());
        assertEquals(300, notes.get(1).durationMs());
        assertEquals(300, notes.get(2).durationMs());
    }

    @Test
    void arpeggioDown_reversesVoiceOrder() {
        var chord = new ScaleChord<>(
                List.of(GongNote.of(GongDegree.I,   4),
                        GongNote.of(GongDegree.III, 4),
                        GongNote.of(GongDegree.V,   4)),
                900, ChordShape.ARPEGGIO_DOWN);

        var notes = onlyNotes(new ChordConcretizer<>(GongConcretizer.inC(), TID).concretize(chord));

        assertEquals(67, notes.get(0).midi());
        assertEquals(64, notes.get(1).midi());
        assertEquals(60, notes.get(2).midi());
    }

    @Test
    void arpeggio_absorbsDurationRemainderIntoLastSlot() {
        var chord = new ScaleChord<>(
                List.of(GongNote.of(GongDegree.I,   4),
                        GongNote.of(GongDegree.III, 4),
                        GongNote.of(GongDegree.V,   4)),
                1000, ChordShape.ARPEGGIO_UP);

        var notes = onlyNotes(new ChordConcretizer<>(GongConcretizer.inC(), TID).concretize(chord));

        assertEquals(333, notes.get(0).durationMs());
        assertEquals(333, notes.get(1).durationMs());
        assertEquals(334, notes.get(2).durationMs());
        assertEquals(1000, notes.get(2).offTickMs());
    }

    @Test
    void changeChordShape_blockToArpeggio_andBack() {
        var block = ScaleChord.block(1000,
                GongNote.of(GongDegree.I,   4),
                GongNote.of(GongDegree.III, 4),
                GongNote.of(GongDegree.V,   4));

        Transformer<ScaleChord<GongNote>, ScaleChord<GongNote>> t =
                ChangeChordShape.blockToArpeggioUp();

        var arp = t.forward(block);
        assertEquals(ChordShape.ARPEGGIO_UP, arp.shape());
        assertEquals(block.voices(),     arp.voices());
        assertEquals(block.durationMs(), arp.durationMs());

        assertEquals(block, t.reverse(arp));
    }

    @Test
    void changeChordShape_rejectsMismatchedCurrentShape() {
        var arp = new ScaleChord<>(
                List.of(GongNote.of(GongDegree.I, 4)),
                500, ChordShape.ARPEGGIO_UP);
        var t = ChangeChordShape.<GongNote>blockToArpeggioUp();

        assertThrows(IllegalStateException.class, () -> t.forward(arp));
    }

    @Test
    void shapeChange_preservesPitchContent_onlyRearrangesTiming() {
        var voices = List.of(
                GongNote.of(GongDegree.I,   4),
                GongNote.of(GongDegree.III, 4),
                GongNote.of(GongDegree.V,   4));
        var concretizer = new ChordConcretizer<>(GongConcretizer.inC(), TID);

        var block = new ScaleChord<>(voices, 900, ChordShape.BLOCK);
        var up    = new ScaleChord<>(voices, 900, ChordShape.ARPEGGIO_UP);
        var down  = new ScaleChord<>(voices, 900, ChordShape.ARPEGGIO_DOWN);

        assertEquals(midiSet(concretizer.concretize(block)),
                     midiSet(concretizer.concretize(up)));
        assertEquals(midiSet(concretizer.concretize(block)),
                     midiSet(concretizer.concretize(down)));
        assertEquals(List.of(60, 64, 67), midiSet(concretizer.concretize(block)));
    }

    @Test
    void chordConcretizer_worksForAnyScale_hirajoshiExample() {
        var chord = ScaleChord.block(1000,
                HirajoshiNote.of(HirajoshiDegree.I,   4),
                HirajoshiNote.of(HirajoshiDegree.III, 4),
                HirajoshiNote.of(HirajoshiDegree.V,   4));

        var track = new ChordConcretizer<>(new HirajoshiConcretizer(9), TID)
                .concretize(chord);

        assertEquals(List.of(69, 72, 76), midiSet(track));
    }

    @Test
    void demoProgression_hasFourChords_threeVoicesEach() {
        var block = ChordProgression.demoIn(ChordShape.BLOCK);
        assertEquals(4, block.size());
        for (var c : block) {
            assertEquals(3, c.voiceCount());
            assertEquals(ChordShape.BLOCK, c.shape());
        }
    }

    @Test
    void demoProgression_roundTripsBlockArpUpAndBack() {
        var original = ChordProgression.demoIn(ChordShape.BLOCK);
        var t = ChangeChordShape.<GongNote>blockToArpeggioUp();

        var forwarded = original.stream().map(t::forward).toList();
        var reversed  = forwarded.stream().map(t::reverse).toList();

        assertEquals(original, reversed);
    }

    @Test
    void concretized_chordTracks_areValueEqual_whenVoicesAndShapeMatch() {
        var voices = List.of(
                GongNote.of(GongDegree.I,   4),
                GongNote.of(GongDegree.III, 4),
                GongNote.of(GongDegree.V,   4));
        var c = new ChordConcretizer<>(GongConcretizer.inC(), TID);

        var a = c.concretize(new ScaleChord<>(voices, 1000, ChordShape.BLOCK));
        var b = c.concretize(new ScaleChord<>(voices, 1000, ChordShape.BLOCK));

        assertEquals(a, b, "structural equality on concrete tracks");
    }

    @Test
    void differentShapes_produceDifferentTracks() {
        var voices = List.of(
                GongNote.of(GongDegree.I,   4),
                GongNote.of(GongDegree.III, 4),
                GongNote.of(GongDegree.V,   4));
        var c = new ChordConcretizer<>(GongConcretizer.inC(), TID);

        var block = c.concretize(new ScaleChord<>(voices, 900, ChordShape.BLOCK));
        var up    = c.concretize(new ScaleChord<>(voices, 900, ChordShape.ARPEGGIO_UP));

        assertNotEquals(block, up, "block vs arpeggio tracks must differ");
    }

    // -- helpers --

    private static List<PitchedNote> onlyNotes(Track t) {
        return t.notes().stream()
                .filter(n -> n instanceof PitchedNote)
                .map(n -> (PitchedNote) n)
                .toList();
    }

    private static List<Integer> midiSet(Track t) {
        return onlyNotes(t).stream().map(PitchedNote::midi).sorted().toList();
    }
}
