package music.notation.transcribe;

import music.notation.input.PitchEstimate;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

import static org.junit.jupiter.api.Assertions.*;

/** Segmentation on hand-built pitch curves (10 ms frames), so each rule is tested in isolation. */
class NoteSegmenterTest {

    /** Builds a pitch track piece by piece. */
    private static final class Curve {
        final List<PitchEstimate> frames = new ArrayList<>();

        /** {@code ms} of pitch given by {@code f(t)} (t in ms from the piece start), at loudness {@code rms}. */
        Curve pitch(int ms, DoubleUnaryOperator f, double rms) {
            for (int i = 0; i < ms / 10; i++) frames.add(new PitchEstimate(frames.size() * 10L, f.applyAsDouble(i * 10), 0.9, rms));
            return this;
        }
        Curve hold(int ms, double midi) { return pitch(ms, t -> midi, 0.2); }
        Curve silence(int ms) {
            for (int i = 0; i < ms / 10; i++) frames.add(PitchEstimate.silent(frames.size() * 10L, 0.0005));
            return this;
        }
        /** The detector lost the pitch, but the voice is still sounding. */
        Curve dropout(int ms) {
            for (int i = 0; i < ms / 10; i++) frames.add(PitchEstimate.silent(frames.size() * 10L, 0.2));
            return this;
        }
        List<TranscribedNote> notes() { return notes(SegmentationSpec.DEFAULT); }
        List<TranscribedNote> notes(SegmentationSpec s) { return new NoteSegmenter(s).segment(new PitchTrack(10, frames)); }
    }

    private static List<Integer> pitches(List<TranscribedNote> notes) {
        return notes.stream().map(TranscribedNote::midi).toList();
    }

    @Test
    void steadyNotesWithGapsBetween() {
        var n = new Curve().silence(100).hold(300, 60).silence(80).hold(200, 64).silence(100).notes();
        assertEquals(List.of(60, 64), pitches(n));
        assertEquals(95, n.get(0).startMs(), 0.01);
        assertEquals(395, n.get(0).endMs(), 0.01);
        assertEquals(0, n.get(0).cents(), 1e-9);
    }

    @Test
    void vibratoWithinTheBandStaysOneNote() {
        var n = new Curve().pitch(1000, t -> 62 + 0.45 * Math.sin(2 * Math.PI * 5.5 * t / 1000), 0.2).notes();
        assertEquals(List.of(62), pitches(n));
        assertEquals(0, n.get(0).cents(), 10);
    }

    @Test
    void flatSingingIsReportedInCents() {
        var n = new Curve().hold(400, 66.7).notes();
        assertEquals(List.of(67), pitches(n));
        assertEquals(-30, n.get(0).cents(), 0.01);
    }

    @Test
    void legatoStepSplitsWithoutAGap() {
        var n = new Curve().hold(300, 60).hold(300, 62).hold(300, 64).notes();
        assertEquals(List.of(60, 62, 64), pitches(n));
        assertEquals(n.get(0).endMs(), n.get(1).startMs(), 10.01);
    }

    @Test
    void octaveBlipsAndSpikesAreSmoothedAway() {
        var c = new Curve().hold(150, 57);
        c.hold(20, 69);                                                       // two-frame octave error
        c.hold(150, 57);
        assertEquals(List.of(57), pitches(c.notes()));
    }

    @Test
    void aSlideIntoANoteIsPartOfThatNote() {
        // 100 ms glide C4 → E4, then E4 held: one E (after the C).
        var n = new Curve().hold(300, 60).pitch(100, t -> 60 + 4 * t / 100, 0.2).hold(400, 64).notes();
        assertEquals(List.of(60, 64), pitches(n));
        // A scoop from below into a single note: still a single note.
        var scoop = new Curve().silence(50).pitch(80, t -> 63 + t / 80, 0.2).hold(400, 64).notes();
        assertEquals(List.of(64), pitches(scoop));
    }

    @Test
    void shortDropoutsAreBridgedButRealGapsSplit() {
        var dropout = new Curve().hold(300, 60).dropout(20).hold(300, 60).notes();
        assertEquals(1, dropout.size(), "20 ms detector dropout");
        var tooLong = new Curve().hold(300, 60).dropout(60).hold(300, 60).notes();
        assertEquals(2, tooLong.size(), "60 ms is longer than the bridge");
        var gap = new Curve().hold(300, 60).silence(80).hold(300, 60).notes();
        assertEquals(2, gap.size(), "80 ms gap: the same pitch sung twice");
    }

    @Test
    void aLoudnessDipSplitsARepeatedSyllable() {
        var c = new Curve().hold(250, 65);
        c.pitch(30, t -> 65, 0.03);                                           // consonant: pitch carries on, level drops
        c.hold(250, 65);
        assertEquals(List.of(65, 65), pitches(c.notes()));
        var noSplit = new SegmentationSpec(35, 50, 60, 60, 30, 15, 200, 0, 0.2);
        assertEquals(List.of(65), pitches(c.notes(noSplit)));
    }

    @Test
    void quietPitchedNoiseIsGatedAndBlipsAreDropped() {
        var c = new Curve().hold(300, 60).silence(100);
        c.pitch(300, t -> 71, 0.001);                                         // 46 dB down: background
        c.silence(100).hold(30, 72).silence(100);                             // 30 ms isolated blip
        assertEquals(List.of(60), pitches(c.notes()));
    }

    @Test
    void emptyTrackGivesNoNotes() {
        assertTrue(new NoteSegmenter(SegmentationSpec.DEFAULT).segment(new PitchTrack(10, List.of())).isEmpty());
        assertTrue(new Curve().silence(500).notes().isEmpty());
    }
}
