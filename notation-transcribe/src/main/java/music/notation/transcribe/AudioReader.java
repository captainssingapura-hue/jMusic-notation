package music.notation.transcribe;

import music.notation.input.AudioFileLoader;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

/**
 * Reads an audio file into a mono {@link AudioClip} at the file's own
 * sample rate. WAV/AIFF are read directly; anything else goes through
 * {@link AudioFileLoader}'s ffmpeg conversion. Channels are averaged —
 * a centre-panned lead vocal keeps its full level while side-panned
 * instruments drop a little.
 */
public final class AudioReader {

    private AudioReader() {}

    public static AudioClip read(Path file) {
        try (AudioFileLoader.LoadedAudio loaded = AudioFileLoader.load(file.toFile())) {
            return decode(loaded.stream());
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read audio " + file + ": " + e.getMessage(), e);
        }
    }

    static AudioClip decode(AudioInputStream in) throws IOException {
        AudioFormat f = in.getFormat();
        AudioFormat.Encoding enc = f.getEncoding();
        boolean supported = (enc.equals(AudioFormat.Encoding.PCM_SIGNED) && f.getSampleSizeInBits() % 8 == 0
                                && f.getSampleSizeInBits() >= 8 && f.getSampleSizeInBits() <= 32)
                || (enc.equals(AudioFormat.Encoding.PCM_UNSIGNED) && f.getSampleSizeInBits() == 8)
                || (enc.equals(AudioFormat.Encoding.PCM_FLOAT) && f.getSampleSizeInBits() == 32);
        if (!supported) {
            AudioFormat target = new AudioFormat(f.getSampleRate(), 16, f.getChannels(), true, false);
            in = AudioSystem.getAudioInputStream(target, in);
            f = in.getFormat();
            enc = f.getEncoding();
        }
        int channels = f.getChannels();
        int bytes = f.getSampleSizeInBits() / 8;
        int frameSize = bytes * channels;
        boolean big = f.isBigEndian();
        byte[] data = in.readAllBytes();
        int frames = data.length / frameSize;
        float[] out = new float[frames];
        for (int i = 0; i < frames; i++) {
            double sum = 0;
            for (int c = 0; c < channels; c++) {
                sum += sample(data, i * frameSize + c * bytes, bytes, big, enc);
            }
            out[i] = (float) (sum / channels);
        }
        return new AudioClip(Math.round(f.getSampleRate()), out);
    }

    private static double sample(byte[] b, int off, int bytes, boolean big, AudioFormat.Encoding enc) {
        if (enc.equals(AudioFormat.Encoding.PCM_UNSIGNED)) return ((b[off] & 0xFF) - 128) / 128.0;
        long v = 0;
        for (int k = 0; k < bytes; k++) {
            int idx = big ? off + k : off + bytes - 1 - k;
            v = (v << 8) | (b[idx] & 0xFF);
        }
        if (enc.equals(AudioFormat.Encoding.PCM_FLOAT)) return Float.intBitsToFloat((int) v);
        int bits = bytes * 8;
        v = (v << (64 - bits)) >> (64 - bits);            // sign-extend
        return v / (double) (1L << (bits - 1));
    }
}
