package music.notation.transcribe;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Test signals: sines and a crude sung-voice model (harmonics, vibrato, scoop, envelope, breath noise). */
final class Synth {

    private Synth() {}

    static float[] sine(int rate, double hz, double seconds, double amp) {
        float[] x = new float[(int) Math.round(rate * seconds)];
        for (int i = 0; i < x.length; i++) x[i] = (float) (amp * Math.sin(2 * Math.PI * hz * i / rate));
        return x;
    }

    static double hz(double midi) { return 440.0 * Math.pow(2, (midi - 69) / 12.0); }

    /** One sung note in a melody. */
    record Note(double startSec, double lengthSec, double midi, double amp) {
        Note(double startSec, double lengthSec, double midi) { this(startSec, lengthSec, midi, 0.3); }
    }

    /** Voice parameters. */
    record Voice(double vibratoHz, double vibratoCents, double scoopCents, double scoopSec, double detuneCents,
                 double noiseAmp) {
        static final Voice PLAIN = new Voice(0, 0, 0, 0, 0, 0);
        static final Voice SINGER = new Voice(5.5, 30, -80, 0.04, 0, 0.002);
    }

    static float[] sing(int rate, double totalSec, Voice v, List<Note> notes) {
        float[] x = new float[(int) Math.round(rate * totalSec)];
        Random rnd = new Random(42);
        for (Note n : notes) {
            int s0 = (int) Math.round(n.startSec() * rate);
            int len = (int) Math.round(n.lengthSec() * rate);
            double phase = 0;
            for (int i = 0; i < len && s0 + i < x.length; i++) {
                double t = (double) i / rate;
                double cents = v.detuneCents();
                if (t < v.scoopSec()) cents += v.scoopCents() * (1 - t / v.scoopSec());
                if (t > 0.15) cents += v.vibratoCents() * Math.sin(2 * Math.PI * v.vibratoHz() * (t - 0.15));
                double f = hz(n.midi() + cents / 100.0);
                phase += 2 * Math.PI * f / rate;
                double env = Math.min(1, Math.min(t / 0.02, (n.lengthSec() - t) / 0.03));
                double s = 0;
                for (int k = 1; k <= 8; k++) {
                    if (f * k > rate / 2.0) break;
                    s += Math.sin(k * phase) / k;
                }
                x[s0 + i] += (float) (n.amp() * env * s * 0.6);
            }
        }
        if (v.noiseAmp() > 0) for (int i = 0; i < x.length; i++) x[i] += (float) (v.noiseAmp() * rnd.nextGaussian());
        return x;
    }

    /** Notes laid end to end at a tempo: each (midi, quarter-note beats); midi < 0 = rest. Gap before each note. */
    static List<Note> melody(double startSec, int bpm, double gapSec, double[][] pitchBeats) {
        var out = new ArrayList<Note>();
        double t = startSec, q = 60.0 / bpm;
        for (double[] pb : pitchBeats) {
            double len = pb[1] * q;
            if (pb[0] >= 0) out.add(new Note(t, len - gapSec, pb[0]));
            t += len;
        }
        return out;
    }

    /** Write a 16-bit WAV with the given channels (each channel the same length). */
    static void writeWav(Path file, int rate, float[]... channels) throws IOException {
        int n = channels[0].length, c = channels.length;
        byte[] data = new byte[n * c * 2];
        for (int i = 0; i < n; i++) {
            for (int ch = 0; ch < c; ch++) {
                int v = (int) Math.round(Math.max(-1, Math.min(1, channels[ch][i])) * 32767);
                int off = (i * c + ch) * 2;
                data[off] = (byte) v;
                data[off + 1] = (byte) (v >> 8);
            }
        }
        AudioFormat fmt = new AudioFormat(rate, 16, c, true, false);
        try (var in = new AudioInputStream(new ByteArrayInputStream(data), fmt, n)) {
            AudioSystem.write(in, AudioFileFormat.Type.WAVE, file.toFile());
        }
    }

    static double rms(float[] x, int from, int to) {
        double s = 0;
        for (int i = from; i < to; i++) s += (double) x[i] * x[i];
        return Math.sqrt(s / (to - from));
    }

    /** Amplitude of the {@code hz} component (Goertzel), for a sine of amplitude A ≈ A. */
    static double amplitudeAt(float[] x, int rate, double hz, int from, int to) {
        double w = 2 * Math.PI * hz / rate, re = 0, im = 0;
        for (int i = from; i < to; i++) { re += x[i] * Math.cos(w * i); im -= x[i] * Math.sin(w * i); }
        return 2 * Math.hypot(re, im) / (to - from);
    }
}
