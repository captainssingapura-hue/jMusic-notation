package music.notation.transcribe;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NoteSmootherTest {

    private static final NoteSmoother SMOOTHER = new NoteSmoother(SmoothingSpec.DEFAULT);

    /** Notes laid end to end from 0 ms: each {midi, lengthMs} or {midi, lengthMs, cents}; touching. */
    private static List<TranscribedNote> run(double[]... spec) {
        var out = new ArrayList<TranscribedNote>();
        double t = 0;
        for (double[] s : spec) {
            out.add(new TranscribedNote(t, t + s[1], (int) s[0], s.length > 2 ? s[2] : 0, 0.9, 0.2));
            t += s[1];
        }
        return out;
    }

    private static List<Integer> midis(List<TranscribedNote> notes) {
        return notes.stream().map(TranscribedNote::midi).toList();
    }

    // ── octave ─────────────────────────────────────────────────────────

    @Test
    void shortOctaveErrorBetweenNeighboursIsFolded() {
        var r = SMOOTHER.fixShapes(run(new double[]{67, 400}, new double[]{79, 120}, new double[]{69, 400}));
        assertEquals(List.of(67, 67, 69), midis(r.notes()));
        assertEquals(List.of(new SmoothingEdit(SmoothingEdit.Kind.OCTAVE, 400, 520, 79, 67)), r.edits());
        var down = SMOOTHER.fixShapes(run(new double[]{72, 400}, new double[]{59, 150}, new double[]{71, 400}));
        assertEquals(List.of(72, 71, 71), midis(down.notes()));
    }

    @Test
    void realLeapsStay() {
        // Long enough to be meant.
        assertEquals(List.of(67, 79, 69), midis(SMOOTHER.fixShapes(
                run(new double[]{67, 400}, new double[]{79, 400}, new double[]{69, 400})).notes()));
        // Close to one neighbour: a leap up to where the tune goes next.
        assertEquals(List.of(67, 79, 77), midis(SMOOTHER.fixShapes(
                run(new double[]{67, 400}, new double[]{79, 120}, new double[]{77, 400})).notes()));
        // At the end of a phrase there is only one neighbour.
        assertEquals(List.of(67, 79), midis(SMOOTHER.fixShapes(
                run(new double[]{67, 400}, new double[]{79, 120})).notes()));
    }

    // ── blips ──────────────────────────────────────────────────────────

    @Test
    void slideFragmentJoinsTheNoteSlidInto() {
        var r = SMOOTHER.fixShapes(run(new double[]{67, 400}, new double[]{71, 70}, new double[]{74, 400}));
        assertEquals(List.of(67, 74), midis(r.notes()));
        assertEquals(400, r.notes().get(1).startMs(), 1e-9, "the fragment's time goes to the target");
        assertEquals(870, r.notes().get(1).endMs(), 1e-9);
        assertEquals(List.of(new SmoothingEdit(SmoothingEdit.Kind.BLIP, 400, 470, 71, 74)), r.edits());
    }

    @Test
    void scoopIntoARepeatedNoteJoinsTheRepeat() {
        // G, a scoop from F# (as a singer re-attacks), G again: still two Gs.
        var r = SMOOTHER.fixShapes(run(new double[]{67, 400}, new double[]{66, 90, 37}, new double[]{67, 300},
                new double[]{64, 400}));
        assertEquals(List.of(67, 67, 64), midis(r.notes()));
        assertEquals(400, r.notes().get(0).endMs(), 1e-9);
        assertEquals(400, r.notes().get(1).startMs(), 1e-9, "the scoop's time goes to the re-attack");
        assertEquals(List.of(new SmoothingEdit(SmoothingEdit.Kind.BLIP, 400, 490, 66, 67)), r.edits());
    }

    @Test
    void fastEvenRunsAndSeparatedNotesStay() {
        // Equal sixteenths: each as long as its neighbours, so none is a blip.
        var scale = run(new double[]{60, 110}, new double[]{62, 110}, new double[]{64, 110},
                new double[]{65, 110}, new double[]{67, 110});
        assertEquals(scale, SMOOTHER.fixShapes(scale).notes());
        // A short note set apart by gaps is articulated, not slid through.
        var gapped = List.of(new TranscribedNote(0, 400, 67, 0, 0.9, 0.2),
                new TranscribedNote(500, 570, 71, 0, 0.9, 0.2),
                new TranscribedNote(670, 1070, 74, 0, 0.9, 0.2));
        assertEquals(gapped, SMOOTHER.fixShapes(gapped).notes());
        // A neighbour note a minor third off a held pitch is an ornament the singer meant.
        var turn = run(new double[]{67, 400}, new double[]{70, 60}, new double[]{67, 400});
        assertEquals(turn, SMOOTHER.fixShapes(turn).notes());
    }

    // ── snapping ───────────────────────────────────────────────────────

    @Test
    void ambiguousOutOfKeyNotesSnapTowardsTheKey() {
        var cMajor = Keys.parse("C major");
        var r = SMOOTHER.snap(run(new double[]{66, 300, 40}, new double[]{66, 300, -40}, new double[]{66, 300, 10},
                new double[]{70, 300, 35}, new double[]{61, 300, -35}, new double[]{64, 300, 45}), cMajor);
        assertEquals(List.of(67, 65, 66, 71, 60, 64), midis(r.notes()));
        assertEquals(-60, r.notes().get(0).cents(), 1e-9, "cents re-measured from the new pitch");
        assertEquals(60, r.notes().get(1).cents(), 1e-9);
        assertEquals(4, r.edits().size());
        assertTrue(r.edits().stream().allMatch(e -> e.kind() == SmoothingEdit.Kind.SNAP));
    }

    @Test
    void snappingFollowsTheMode() {
        // A minor keeps its leading note G#; Bb leaning up goes to B.
        var aMinor = Keys.parse("A minor");
        var r = SMOOTHER.snap(run(new double[]{68, 300, 40}, new double[]{70, 300, 40}), aMinor);
        assertEquals(List.of(68, 71), midis(r.notes()));
        // In C major, C# leaning up lands on D and D# leaning up on E.
        var cMajor = Keys.parse("C major");
        assertEquals(List.of(62, 64), midis(SMOOTHER.snap(run(new double[]{61, 300, 40}, new double[]{63, 300, 40}), cMajor).notes()));
        assertTrue(NoteSmoother.scale(Keys.parse("C major"))[0]);
        assertFalse(NoteSmoother.scale(Keys.parse("C major"))[1]);
        assertTrue(NoteSmoother.scale(Keys.parse("D major"))[6], "F# in D major");
    }

    @Test
    void passesSwitchOff() {
        var notes = run(new double[]{67, 400}, new double[]{79, 120}, new double[]{71, 70}, new double[]{74, 400},
                new double[]{66, 300, 40});
        var off = new NoteSmoother(SmoothingSpec.OFF);
        assertEquals(notes, off.fixShapes(notes).notes());
        assertEquals(notes, off.snap(notes, Keys.parse("C major")).notes());
        assertTrue(off.fixShapes(notes).edits().isEmpty());
    }

    // ── through the pipeline ───────────────────────────────────────────

    @Test
    void pipelineSnapsASharpSungNoteAndReportsIt() {
        // C D E, then an F# sung 40¢ sharp (between F# and G), then G — in C major.
        int rate = 16_000, bpm = 100;
        var melody = Synth.melody(0.3, bpm, 0.06, new double[][]{{60, 1}, {62, 1}, {64, 1}, {66.4, 1}, {67, 2}});
        var clip = new AudioClip(rate, Synth.sing(rate, 4.5, Synth.Voice.PLAIN, melody), 0);
        var timing = new TimingSpec(bpm, 0.3, null, "C major", null);

        var on = Transcriber.transcribe(new TranscriptionRequest("x.wav", null, null, null, timing, "x"), clip);
        assertEquals(List.of(60, 62, 64, 67, 67), on.quantized().stream().map(QuantizedNote::midi).toList());
        assertEquals(1, on.smoothing().size());
        assertEquals(SmoothingEdit.Kind.SNAP, on.smoothing().getFirst().kind());
        String report = TranscriptionReport.render(on);
        assertTrue(report.contains("1 snap"), report);
        assertTrue(report.contains("F#4 → G4"), report);

        var off = Transcriber.transcribe(new TranscriptionRequest("x.wav", null, null, null, SmoothingSpec.OFF, timing, "x"), clip);
        assertEquals(List.of(60, 62, 64, 66, 67), off.quantized().stream().map(QuantizedNote::midi).toList());
        assertTrue(TranscriptionReport.render(off).contains("Smoothing: off"));
    }

    @Test
    void jsonDefaultsAndRoundTrip() {
        var r = TranscriptionJson.requestFromJson("""
                { "audio": "a.wav", "smoothing": { "snapCents": 0 } }
                """);
        assertEquals(new SmoothingSpec(0, 250, 100, 60), r.smoothing());
        assertEquals(SmoothingSpec.DEFAULT, TranscriptionJson.requestFromJson("{ \"audio\": \"a.wav\" }").smoothing());
        assertEquals(r, TranscriptionJson.requestFromJson(TranscriptionJson.toJson(r)));
        assertThrows(IllegalArgumentException.class, () -> new SmoothingSpec(50, 0, 0, 0));
    }
}
