package music.notation.transcribe;

import music.notation.input.PitchEstimate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * RMVPE with the real weights. Skipped unless the model is found — run
 * with {@code -Dnotation.rmvpe.model=…/rmvpe.onnx} (or the
 * {@code NOTATION_RMVPE_MODEL} variable, or the default location).
 */
class RmvpeTrackDetectorTest {

    private static final int RATE = Transcriber.ANALYSIS_RATE;
    private static final PitchDetectorSpec.Rmvpe SPEC = PitchDetectorSpec.Rmvpe.DEFAULT;

    @BeforeAll
    static void needsModel() {
        assumeTrue(RmvpeModel.available(null), "RMVPE model not found at " + RmvpeModel.locate(null));
    }

    private static PitchTrack detect(float[] x, double offsetSec) {
        return new RmvpeTrackDetector(SPEC).detect(new AudioClip(RATE, x, offsetSec));
    }

    private static double medianMidi(PitchTrack t, double fromSec, double toSec) {
        double[] v = t.frames().stream()
                .filter(e -> e.tickMs() >= fromSec * 1000 && e.tickMs() < toSec * 1000 && e.hasPitch())
                .mapToDouble(PitchEstimate::midiFloat).sorted().toArray();
        assertTrue(v.length > 0, "no voiced frames in " + fromSec + "–" + toSec + " s");
        return v[v.length / 2];
    }

    @Test
    void steadyNotesAcrossTheVocalRange() {
        double[] midis = {45, 52, 57, 64, 69, 76, 81};                    // A2 … A5
        var notes = new ArrayList<Synth.Note>();
        for (int i = 0; i < midis.length; i++) notes.add(new Synth.Note(0.3 + i, 0.8, midis[i]));
        PitchTrack t = detect(Synth.sing(RATE, midis.length + 0.5, Synth.Voice.PLAIN, notes), 0);
        assertEquals(10.0, t.hopMs(), 1e-12);
        for (int i = 0; i < midis.length; i++) {
            assertEquals(midis[i], medianMidi(t, 0.45 + i, 1.0 + i), 0.15, "note " + i);
        }
        // Gaps between the notes are unvoiced.
        long voicedInGaps = t.frames().stream()
                .filter(e -> e.hasPitch() && (e.tickMs() % 1000) > 150 && (e.tickMs() % 1000) < 280 && e.tickMs() > 1000)
                .count();
        assertTrue(voicedInGaps < 10, "voiced frames in the gaps: " + voicedInGaps);
    }

    @Test
    void framesLineUpWithYinAndCarryFileTime() {
        float[] x = Synth.sing(RATE, 2.0, Synth.Voice.SINGER, List.of(new Synth.Note(0.2, 1.5, 62)));
        PitchTrack r = detect(x, 4.0);
        PitchTrack y = new YinTrackDetector(PitchDetectorSpec.Yin.DEFAULT).detect(new AudioClip(RATE, x, 4.0));
        assertEquals(y.frames().size(), r.frames().size());
        for (int i = 0; i < r.frames().size(); i++) {
            assertEquals(y.frames().get(i).tickMs(), r.frames().get(i).tickMs());
            var f = r.frames().get(i);
            double plain = y.frames().get(i).rms();
            assertTrue(f.rms() <= plain + 1e-12, "level never exceeds the plain RMS");
            if (f.hasPitch()) assertEquals(plain * f.confidence(), f.rms(), 1e-9, "level = RMS × peak salience");
        }
        assertEquals(4000, r.frames().getFirst().tickMs());
        assertEquals(62, medianMidi(r, 4.5, 5.6), 0.2);
    }

    @Test
    void longClipsAreChunkedWithoutSeams() {
        // One held note across the first chunk boundary (20.48 s).
        double boundary = RmvpeTrackDetector.CHUNK * 0.01;
        float[] x = Synth.sing(RATE, 24, Synth.Voice.PLAIN,
                List.of(new Synth.Note(boundary - 2, 4, 67), new Synth.Note(1, 2, 60)));
        PitchTrack t = detect(x, 0);
        assertEquals(1 + x.length / 160, t.frames().size());
        double[] around = t.frames().stream()
                .filter(e -> Math.abs(e.tickMs() - boundary * 1000) <= 500)
                .mapToDouble(PitchEstimate::midiFloat).toArray();
        assertTrue(Arrays.stream(around).noneMatch(Double::isNaN), "dropouts at the chunk seam");
        assertTrue(Arrays.stream(around).allMatch(m -> Math.abs(m - 67) < 0.3), Arrays.toString(around));
        assertEquals(60, medianMidi(t, 1.2, 2.8), 0.15);
    }

    @Test
    void picksTheVoiceOutOfAnAccompaniment() {
        // Twinkle in G over sustained chords an octave and more below, as loud as the voice.
        double[][] tune = {{67, 1}, {67, 1}, {74, 1}, {74, 1}, {76, 1}, {76, 1}, {74, 2},
                           {72, 1}, {72, 1}, {71, 1}, {71, 1}, {69, 1}, {69, 1}, {67, 2}};
        int bpm = 100;
        float[] voice = Synth.sing(44_100, 10.6, Synth.Voice.SINGER, Synth.melody(0.5, bpm, 0.06, tune));
        double bar = 4 * 60.0 / bpm;
        int[][] chords = {{43, 47, 50}, {48, 52, 55}, {48, 52, 55}, {43, 47, 50}};   // G C C G (bars 1–4 under the tune)
        var pad = new ArrayList<Synth.Note>();
        for (int b = 0; b < chords.length; b++)
            for (int m : chords[b]) pad.add(new Synth.Note(0.5 + b * bar, bar - 0.02, m, 0.22));
        float[] accompaniment = Synth.sing(44_100, 10.6, Synth.Voice.PLAIN, pad);
        float[] mix = new float[voice.length];
        for (int i = 0; i < mix.length; i++) mix[i] = 0.6f * (voice[i] + accompaniment[i]);

        var timing = new TimingSpec(bpm, 0.5, null, null, null);
        var clip = new AudioClip(44_100, mix, 0);
        var t = Transcriber.transcribe(new TranscriptionRequest("mix.wav", null, SPEC, null, timing, "mix"), clip);
        List<Integer> sung = t.quantized().stream().map(QuantizedNote::midi).toList();
        List<Integer> written = Arrays.stream(tune).map(n -> (int) n[0]).toList();

        // Every pitch right, in order, and the key.
        assertEquals(collapseRepeats(written), collapseRepeats(sung), sung.toString());
        assertEquals("G major", Keys.name(t.key()));
        // Repeated notes are told apart by the dip in voice level between them —
        // except C5 C5 in bar 3, where the C chord's 4th harmonic sits on C5
        // through the gap and the dip is too shallow. Known limit; tighten
        // this when segmentation can separate it.
        assertTrue(sung.size() >= written.size() - 1, "repeats merged: " + sung);

        // YIN, on the same mix, finds no melody at all.
        var yin = Transcriber.transcribe(new TranscriptionRequest("mix.wav", null, null, null, timing, "mix"), clip);
        assertTrue(yin.quantized().size() < written.size() / 2, "YIN unexpectedly coped: " + yin.quantized().size());
    }

    private static List<Integer> collapseRepeats(List<Integer> xs) {
        var out = new ArrayList<Integer>();
        for (int x : xs) if (out.isEmpty() || out.getLast() != x) out.add(x);
        return out;
    }
}
