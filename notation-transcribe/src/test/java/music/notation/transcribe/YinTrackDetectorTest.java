package music.notation.transcribe;

import music.notation.input.PitchEstimate;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class YinTrackDetectorTest {

    private static final int R = Transcriber.ANALYSIS_RATE;

    private static PitchTrack track(float[] x, double offsetSec) {
        return new YinTrackDetector(PitchDetectorSpec.Yin.DEFAULT).detect(new AudioClip(R, x, offsetSec));
    }

    private static double medianMidi(PitchTrack t, long fromMs, long toMs) {
        double[] v = t.frames().stream().filter(f -> f.tickMs() >= fromMs && f.tickMs() < toMs)
                .filter(PitchEstimate::hasPitch).mapToDouble(PitchEstimate::midiFloat).sorted().toArray();
        assertTrue(v.length > 0, "no voiced frames in " + fromMs + "–" + toMs);
        return v[v.length / 2];
    }

    @Test
    void framesAreCentredEveryTenMillisecondsInFileTime() {
        PitchTrack t = track(new float[R], 2.5);
        assertEquals(10.0, t.hopMs(), 1e-12);
        assertEquals(1 + R / 160, t.size());
        assertEquals(2500, t.get(0).tickMs());
        assertEquals(2510, t.get(1).tickMs());
        assertEquals(0.0, t.voicedFraction());
    }

    @Test
    void harmonicNotesAcrossTheVocalRangeAreFoundWithinAFewCents() {
        for (int midi : new int[]{43, 50, 57, 64, 72, 79}) {         // G2 … G5
            float[] x = Synth.sing(R, 0.6, Synth.Voice.PLAIN, List.of(new Synth.Note(0.05, 0.5, midi)));
            // YIN's parabolic interpolation reads ~4 cents sharp at G5 (a 20-sample period at 16 kHz).
            assertEquals(midi, medianMidi(track(x, 0), 150, 450), 0.05, "MIDI " + midi);
        }
    }

    @Test
    void vibratoAveragesOutAndSilenceIsUnvoiced() {
        float[] x = Synth.sing(R, 1.5, Synth.Voice.SINGER, List.of(new Synth.Note(0.5, 0.8, 62)));
        PitchTrack t = track(x, 0);
        assertEquals(62, medianMidi(t, 700, 1250), 0.1);
        long voicedBefore = t.frames().stream().filter(f -> f.tickMs() < 440 && f.hasPitch()).count();
        long voicedAfter = t.frames().stream().filter(f -> f.tickMs() > 1360 && f.hasPitch()).count();
        assertEquals(0, voicedBefore);
        assertEquals(0, voicedAfter);
    }

    @Test
    void csvHasOneRowPerFrame() {
        float[] x = Synth.sing(R, 0.3, Synth.Voice.PLAIN, List.of(new Synth.Note(0.1, 0.15, 69)));
        PitchTrack t = track(x, 0);
        String[] lines = t.toCsv().split("\n");
        assertEquals("time_ms,midi,hz,confidence,rms", lines[0]);
        assertEquals(t.size() + 1, lines.length);
        assertTrue(Arrays.stream(lines).anyMatch(l -> l.contains(",69.0") || l.contains(",68.9")), "an A4 frame");
        assertTrue(lines[1].startsWith("0,,,0,"), lines[1]);
    }
}
