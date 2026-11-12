package music.notation.transcribe.bench;

import music.notation.transcribe.AudioClip;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** 16-bit mono PCM conversions between {@link AudioClip} floats, byte buffers and WAV files. */
final class Pcm {

    private Pcm() {}

    static AudioFormat format(int rate) { return new AudioFormat(rate, 16, 1, true, false); }

    static byte[] toBytes(float[] x, int from, int to) {
        byte[] b = new byte[(to - from) * 2];
        for (int i = from; i < to; i++) {
            int v = (int) Math.round(Math.max(-1f, Math.min(1f, x[i])) * 32767);
            b[(i - from) * 2] = (byte) v;
            b[(i - from) * 2 + 1] = (byte) (v >> 8);
        }
        return b;
    }

    static float[] fromBytes(byte[] b, int len) {
        float[] x = new float[len / 2];
        for (int i = 0; i < x.length; i++) x[i] = (short) ((b[2 * i] & 0xFF) | (b[2 * i + 1] << 8)) / 32768f;
        return x;
    }

    static void writeWav(Path file, AudioClip clip) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        byte[] data = toBytes(clip.samples(), 0, clip.length());
        try (var in = new AudioInputStream(new ByteArrayInputStream(data), format(clip.sampleRate()), clip.length())) {
            AudioSystem.write(in, AudioFileFormat.Type.WAVE, file.toFile());
        }
    }
}
