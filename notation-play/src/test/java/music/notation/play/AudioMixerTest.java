package music.notation.play;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Streaming + correctness checks for {@link AudioMixer}.
 *
 * <p>Each test writes one or more synthetic WAVs to a temp directory, runs
 * the mixer, then re-reads the output to assert frame length, format
 * fidelity, and (where verifiable) sample-level mixing behaviour.</p>
 */
class AudioMixerTest {

    private static final int SAMPLE_RATE = 44100;
    private static final int BITS        = 16;
    private static final int CHANNELS    = 2;
    private static final AudioFormat STD = new AudioFormat(
            SAMPLE_RATE, BITS, CHANNELS, /*signed=*/ true, /*bigEndian=*/ false);

    // ── Format & length ───────────────────────────────────────────────

    @Test
    void mixesTwoMatchedStereoWavsAtUnityGain(@TempDir Path tmp) throws Exception {
        File a = tmp.resolve("a.wav").toFile();
        File b = tmp.resolve("b.wav").toFile();
        File out = tmp.resolve("mix.wav").toFile();

        // 1000 frames of 1000 / -1000, 1000 frames of -500 / 500.
        writeWav(a, STD, samples(1000, 1000, 1000));
        writeWav(b, STD, samples(1000, -500, 500));

        AudioMixer.mix(a, b, out);

        AudioFileFormat info = AudioSystem.getAudioFileFormat(out);
        assertEquals(AudioFileFormat.Type.WAVE, info.getType());
        assertEquals((float) SAMPLE_RATE, info.getFormat().getSampleRate(), 0.1f);
        assertEquals(BITS, info.getFormat().getSampleSizeInBits());
        assertEquals(CHANNELS, info.getFormat().getChannels());
        assertEquals(1000, info.getFrameLength());
    }

    @Test
    void outputLengthEqualsLongestInput(@TempDir Path tmp) throws Exception {
        File shortFile = tmp.resolve("short.wav").toFile();
        File longFile  = tmp.resolve("long.wav").toFile();
        File out       = tmp.resolve("out.wav").toFile();

        writeWav(shortFile, STD, samples(500,  100, 200));
        writeWav(longFile,  STD, samples(2000, 300, 400));

        AudioMixer.mix(shortFile, longFile, out);

        AudioFileFormat info = AudioSystem.getAudioFileFormat(out);
        assertEquals(2000, info.getFrameLength(),
                "output length must equal the longest input");
    }

    // ── Sample-level correctness ──────────────────────────────────────

    @Test
    void unityGainSum_isExactSamplewise(@TempDir Path tmp) throws Exception {
        // Two streams: A is constant +1000 left / -1000 right; B is +2000 / +3000.
        // Expected mix: +3000 / +2000.
        File a = tmp.resolve("a.wav").toFile();
        File b = tmp.resolve("b.wav").toFile();
        File out = tmp.resolve("out.wav").toFile();

        writeWav(a, STD, samples(100, 1000, -1000));
        writeWav(b, STD, samples(100, 2000,  3000));

        AudioMixer.mix(a, b, out);

        short[] left = readChannel(out, /*channel=*/ 0);
        short[] right = readChannel(out, /*channel=*/ 1);
        assertEquals(100, left.length);
        for (int i = 0; i < 100; i++) {
            assertEquals(3000, left[i],  "left frame " + i);
            assertEquals(2000, right[i], "right frame " + i);
        }
    }

    @Test
    void gainAttenuates(@TempDir Path tmp) throws Exception {
        // A at gain 1.0 + B at gain 0.5 → A + B*0.5.
        File a = tmp.resolve("a.wav").toFile();
        File b = tmp.resolve("b.wav").toFile();
        File out = tmp.resolve("out.wav").toFile();

        writeWav(a, STD, samples(100, 1000, 1000));
        writeWav(b, STD, samples(100, 2000, 2000));

        AudioMixer.mix(List.of(
                AudioMixer.MixSource.of(a, 1.0f),
                AudioMixer.MixSource.of(b, 0.5f)), out);

        short[] left = readChannel(out, 0);
        assertEquals(2000, left[0]);
        assertEquals(2000, left[50]);
        assertEquals(2000, left[99]);
    }

    @Test
    void clippingSaturatesAtInt16Bounds(@TempDir Path tmp) throws Exception {
        // Both sources at +25000 → sum 50000, must saturate to 32767.
        // Both at -25000 → sum -50000, must saturate to -32768.
        File a   = tmp.resolve("a.wav").toFile();
        File b   = tmp.resolve("b.wav").toFile();
        File pos = tmp.resolve("pos.wav").toFile();

        writeWav(a, STD, samples(100,  25000, -25000));
        writeWav(b, STD, samples(100,  25000, -25000));

        AudioMixer.mix(a, b, pos);

        short[] left  = readChannel(pos, 0);
        short[] right = readChannel(pos, 1);
        for (int i = 0; i < 100; i++) {
            assertEquals( 32767, left[i],  "positive clip · frame " + i);
            assertEquals(-32768, right[i], "negative clip · frame " + i);
        }
    }

    @Test
    void clipHelperBehaviour() {
        // Direct unit test of the saturation primitive.
        assertEquals( 32767, AudioMixer.clipInt16( 40000));
        assertEquals(-32768, AudioMixer.clipInt16(-40000));
        assertEquals( 32767, AudioMixer.clipInt16( 32767));
        assertEquals(-32768, AudioMixer.clipInt16(-32768));
        assertEquals(     0, AudioMixer.clipInt16(     0));
        assertEquals( 12345, AudioMixer.clipInt16( 12345));
    }

    @Test
    void shorterInputZeroPadsAtEnd(@TempDir Path tmp) throws Exception {
        // A: 100 frames @ 5000 / 0
        // B: 200 frames @ 1000 / 0
        // First 100 frames: mix → 6000 / 0.  Next 100 frames: B alone → 1000 / 0.
        File a = tmp.resolve("a.wav").toFile();
        File b = tmp.resolve("b.wav").toFile();
        File out = tmp.resolve("out.wav").toFile();

        writeWav(a, STD, samples(100, 5000, 0));
        writeWav(b, STD, samples(200, 1000, 0));

        AudioMixer.mix(a, b, out);

        short[] left = readChannel(out, 0);
        assertEquals(200, left.length);
        for (int i = 0; i < 100; i++) {
            assertEquals(6000, left[i], "mixed region · frame " + i);
        }
        for (int i = 100; i < 200; i++) {
            assertEquals(1000, left[i], "tail region (A silent) · frame " + i);
        }
    }

    @Test
    void singleSourceMixIsLossless(@TempDir Path tmp) throws Exception {
        // Mixing one source at unity should produce a byte-identical
        // result (up to WAV header) — no rounding, no clipping.
        File in = tmp.resolve("in.wav").toFile();
        File out = tmp.resolve("out.wav").toFile();

        writeWav(in, STD, samples(500, 12345, -23456));

        AudioMixer.mix(List.of(AudioMixer.MixSource.of(in)), out);

        short[] inLeft  = readChannel(in, 0);
        short[] outLeft = readChannel(out, 0);
        assertArrayEquals(inLeft, outLeft);
        short[] inRight  = readChannel(in, 1);
        short[] outRight = readChannel(out, 1);
        assertArrayEquals(inRight, outRight);
    }

    // ── Error handling ────────────────────────────────────────────────

    // ── Higher bit depths ─────────────────────────────────────────────

    @Test
    void mixes24BitStereoWavs(@TempDir Path tmp) throws Exception {
        AudioFormat fmt24 = new AudioFormat(SAMPLE_RATE, 24, 2, true, false);
        File a = tmp.resolve("a24.wav").toFile();
        File b = tmp.resolve("b24.wav").toFile();
        File out = tmp.resolve("mix24.wav").toFile();

        // 24-bit samples: range [-8388608, 8388607]
        writeWav(a, fmt24, samples24(100, 100_000, -100_000));
        writeWav(b, fmt24, samples24(100, 200_000,  300_000));

        AudioMixer.mix(a, b, out);

        AudioFileFormat info = AudioSystem.getAudioFileFormat(out);
        assertEquals(24, info.getFormat().getSampleSizeInBits());
        assertEquals(100, info.getFrameLength());

        int[] left = readChannel24(out, 0);
        int[] right = readChannel24(out, 1);
        for (int i = 0; i < 100; i++) {
            assertEquals(300_000, left[i],  "left @" + i);
            assertEquals(200_000, right[i], "right @" + i);
        }
    }

    @Test
    void clippingSaturates24Bit(@TempDir Path tmp) throws Exception {
        // 24-bit ceiling 8_388_607; floor -8_388_608.
        AudioFormat fmt24 = new AudioFormat(SAMPLE_RATE, 24, 2, true, false);
        File a = tmp.resolve("a.wav").toFile();
        File b = tmp.resolve("b.wav").toFile();
        File out = tmp.resolve("clip24.wav").toFile();

        writeWav(a, fmt24, samples24(50,  5_000_000, -5_000_000));
        writeWav(b, fmt24, samples24(50,  5_000_000, -5_000_000));

        AudioMixer.mix(a, b, out);

        int[] left  = readChannel24(out, 0);
        int[] right = readChannel24(out, 1);
        for (int i = 0; i < 50; i++) {
            assertEquals( 8_388_607, left[i],  "positive 24-bit clip @" + i);
            assertEquals(-8_388_608, right[i], "negative 24-bit clip @" + i);
        }
    }

    @Test
    void mixes32BitStereoWavs(@TempDir Path tmp) throws Exception {
        AudioFormat fmt32 = new AudioFormat(SAMPLE_RATE, 32, 2, true, false);
        File a = tmp.resolve("a32.wav").toFile();
        File b = tmp.resolve("b32.wav").toFile();
        File out = tmp.resolve("mix32.wav").toFile();

        writeWav(a, fmt32, samples32(50, 1_000_000_000, -1_000_000_000));
        writeWav(b, fmt32, samples32(50,   500_000_000,    500_000_000));

        AudioMixer.mix(a, b, out);

        AudioFileFormat info = AudioSystem.getAudioFileFormat(out);
        assertEquals(32, info.getFormat().getSampleSizeInBits());

        int[] left = readChannel32(out, 0);
        int[] right = readChannel32(out, 1);
        for (int i = 0; i < 50; i++) {
            assertEquals(1_500_000_000, left[i],  "left @" + i);
            assertEquals(-500_000_000,  right[i], "right @" + i);
        }
    }

    @Test
    void clippingSaturates32Bit(@TempDir Path tmp) throws Exception {
        // 32-bit ceiling Integer.MAX_VALUE; floor Integer.MIN_VALUE.
        // Two sources each at 2_000_000_000 → sum 4_000_000_000 > INT_MAX.
        AudioFormat fmt32 = new AudioFormat(SAMPLE_RATE, 32, 2, true, false);
        File a = tmp.resolve("a.wav").toFile();
        File b = tmp.resolve("b.wav").toFile();
        File out = tmp.resolve("clip32.wav").toFile();

        writeWav(a, fmt32, samples32(20,  2_000_000_000, -2_000_000_000));
        writeWav(b, fmt32, samples32(20,  2_000_000_000, -2_000_000_000));

        AudioMixer.mix(a, b, out);

        int[] left  = readChannel32(out, 0);
        int[] right = readChannel32(out, 1);
        for (int i = 0; i < 20; i++) {
            assertEquals(Integer.MAX_VALUE, left[i],  "positive 32-bit clip @" + i);
            assertEquals(Integer.MIN_VALUE, right[i], "negative 32-bit clip @" + i);
        }
    }

    @Test
    void rejects8BitPcm(@TempDir Path tmp) throws Exception {
        // 8-bit WAV is written as PCM_UNSIGNED per the WAV spec (Java Sound
        // converts our PCM_SIGNED on the way in). The mixer rejects it on
        // the encoding check before getting to the bit-depth check; either
        // failure mode is acceptable as long as it doesn't quietly process.
        AudioFormat fmt8 = new AudioFormat(SAMPLE_RATE, 8, 1, true, false);
        File a = tmp.resolve("a8.wav").toFile();
        byte[] pcm = new byte[100];
        for (int i = 0; i < 100; i++) pcm[i] = (byte) 50;
        writeWav(a, fmt8, pcm);

        File out = tmp.resolve("out.wav").toFile();
        IOException ex = assertThrows(IOException.class, () -> AudioMixer.mix(a, a, out));
        String msg = ex.getMessage().toLowerCase();
        assertTrue(msg.contains("pcm_signed") || msg.contains("16/24/32"),
                "expected unsupported-format rejection, got: " + ex.getMessage());
    }

    @Test
    void rejectsBitDepthMismatch(@TempDir Path tmp) throws Exception {
        AudioFormat fmt16 = STD;
        AudioFormat fmt24 = new AudioFormat(SAMPLE_RATE, 24, 2, true, false);
        File a = tmp.resolve("a16.wav").toFile();
        File b = tmp.resolve("b24.wav").toFile();
        File out = tmp.resolve("out.wav").toFile();
        writeWav(a, fmt16, samples(50, 1000, 1000));
        writeWav(b, fmt24, samples24(50, 1000, 1000));

        IOException ex = assertThrows(IOException.class, () -> AudioMixer.mix(a, b, out));
        assertTrue(ex.getMessage().toLowerCase().contains("format mismatch"),
                "16-bit / 24-bit mix should fail format-mismatch: " + ex.getMessage());
    }

    // ── (Existing 16-bit tests continue below) ────────────────────────

    @Test
    void rejectsSampleRateMismatch(@TempDir Path tmp) throws Exception {
        File a = tmp.resolve("a.wav").toFile();
        File b = tmp.resolve("b.wav").toFile();
        File out = tmp.resolve("out.wav").toFile();

        writeWav(a, STD, samples(100, 1000, 1000));
        AudioFormat alt = new AudioFormat(48000, 16, 2, true, false);
        writeWav(b, alt, samples(100, 1000, 1000));

        IOException ex = assertThrows(IOException.class, () -> AudioMixer.mix(a, b, out));
        assertTrue(ex.getMessage().toLowerCase().contains("format mismatch"),
                "exception should clearly indicate format mismatch: " + ex.getMessage());
    }

    @Test
    void rejectsChannelCountMismatch(@TempDir Path tmp) throws Exception {
        File a = tmp.resolve("a.wav").toFile();
        File b = tmp.resolve("b.wav").toFile();
        File out = tmp.resolve("out.wav").toFile();

        writeWav(a, STD, samples(100, 1000, 1000));
        AudioFormat mono = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
        writeWav(b, mono, monoSamples(100, 1000));

        assertThrows(IOException.class, () -> AudioMixer.mix(a, b, out));
    }

    @Test
    void emptyListRejected(@TempDir Path tmp) {
        File out = tmp.resolve("out.wav").toFile();
        assertThrows(IllegalArgumentException.class,
                () -> AudioMixer.mix(List.of(), out));
    }

    @Test
    void negativeGainRejected() {
        File f = new File("dummy.wav");
        assertThrows(IllegalArgumentException.class,
                () -> new AudioMixer.MixSource(f, -0.5f, 0L));
    }

    // ── Test helpers ──────────────────────────────────────────────────

    /** Build a 16-bit signed little-endian byte array of stereo frames. */
    private static byte[] samples(int frames, int left, int right) {
        byte[] buf = new byte[frames * 4];
        for (int i = 0; i < frames; i++) {
            writeLE16(buf, i * 4,     left);
            writeLE16(buf, i * 4 + 2, right);
        }
        return buf;
    }

    private static byte[] monoSamples(int frames, int value) {
        byte[] buf = new byte[frames * 2];
        for (int i = 0; i < frames; i++) writeLE16(buf, i * 2, value);
        return buf;
    }

    private static void writeLE16(byte[] buf, int off, int value) {
        buf[off]     = (byte) ( value       & 0xFF);
        buf[off + 1] = (byte) ((value >> 8) & 0xFF);
    }

    private static void writeWav(File file, AudioFormat fmt, byte[] pcm) throws IOException {
        try (AudioInputStream ais = new AudioInputStream(
                new ByteArrayInputStream(pcm), fmt, pcm.length / fmt.getFrameSize())) {
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, file);
        }
    }

    /** 24-bit stereo sample buffer (3 bytes per sample, little-endian). */
    private static byte[] samples24(int frames, int left, int right) {
        byte[] buf = new byte[frames * 6];
        for (int i = 0; i < frames; i++) {
            writeLE24(buf, i * 6,     left);
            writeLE24(buf, i * 6 + 3, right);
        }
        return buf;
    }

    /** 32-bit stereo sample buffer (4 bytes per sample, little-endian). */
    private static byte[] samples32(int frames, int left, int right) {
        byte[] buf = new byte[frames * 8];
        for (int i = 0; i < frames; i++) {
            writeLE32(buf, i * 8,     left);
            writeLE32(buf, i * 8 + 4, right);
        }
        return buf;
    }

    private static void writeLE24(byte[] buf, int off, int value) {
        buf[off]     = (byte) ( value        & 0xFF);
        buf[off + 1] = (byte) ((value >>  8) & 0xFF);
        buf[off + 2] = (byte) ((value >> 16) & 0xFF);
    }

    private static void writeLE32(byte[] buf, int off, int value) {
        buf[off]     = (byte) ( value         & 0xFF);
        buf[off + 1] = (byte) ((value >>>  8) & 0xFF);
        buf[off + 2] = (byte) ((value >>> 16) & 0xFF);
        buf[off + 3] = (byte) ((value >>> 24) & 0xFF);
    }

    /** Read one channel of a 24-bit stereo WAV as int[]; signs preserved. */
    private static int[] readChannel24(File f, int channel) throws Exception {
        try (AudioInputStream in = AudioSystem.getAudioInputStream(f)) {
            int frames = (int) in.getFrameLength();
            byte[] buf = new byte[frames * 6];
            int got = 0;
            while (got < buf.length) {
                int n = in.read(buf, got, buf.length - got);
                if (n < 0) break;
                got += n;
            }
            int[] result = new int[frames];
            int sampleOff = channel * 3;
            for (int i = 0; i < frames; i++) {
                int off = i * 6 + sampleOff;
                int v = ((buf[off + 2] & 0xFF) << 16)
                      | ((buf[off + 1] & 0xFF) << 8)
                      |  (buf[off]     & 0xFF);
                if ((v & 0x800000) != 0) v |= 0xFF000000;   // sign-extend
                result[i] = v;
            }
            return result;
        }
    }

    /** Read one channel of a 32-bit stereo WAV as int[]. */
    private static int[] readChannel32(File f, int channel) throws Exception {
        try (AudioInputStream in = AudioSystem.getAudioInputStream(f)) {
            int frames = (int) in.getFrameLength();
            byte[] buf = new byte[frames * 8];
            int got = 0;
            while (got < buf.length) {
                int n = in.read(buf, got, buf.length - got);
                if (n < 0) break;
                got += n;
            }
            int[] result = new int[frames];
            int sampleOff = channel * 4;
            for (int i = 0; i < frames; i++) {
                int off = i * 8 + sampleOff;
                result[i] = ((buf[off + 3] & 0xFF) << 24)
                          | ((buf[off + 2] & 0xFF) << 16)
                          | ((buf[off + 1] & 0xFF) << 8)
                          |  (buf[off]     & 0xFF);
            }
            return result;
        }
    }

    /** Read one channel of a 16-bit stereo (or mono) WAV as shorts. */
    private static short[] readChannel(File f, int channel) throws Exception {
        try (AudioInputStream in = AudioSystem.getAudioInputStream(f)) {
            AudioFormat fmt = in.getFormat();
            int channels = fmt.getChannels();
            int frameSize = fmt.getFrameSize();
            boolean be = fmt.isBigEndian();
            int frames = (int) in.getFrameLength();
            byte[] buf = new byte[frames * frameSize];
            int got = 0;
            while (got < buf.length) {
                int n = in.read(buf, got, buf.length - got);
                if (n < 0) break;
                got += n;
            }
            short[] result = new short[frames];
            int sampleOff = channel * 2;
            for (int i = 0; i < frames; i++) {
                int off = i * frameSize + sampleOff;
                int lo, hi;
                if (be) { hi = buf[off] & 0xFF; lo = buf[off + 1] & 0xFF; }
                else    { lo = buf[off] & 0xFF; hi = buf[off + 1] & 0xFF; }
                int v = (hi << 8) | lo;
                if (v >= 0x8000) v -= 0x10000;
                result[i] = (short) v;
            }
            return result;
        }
    }
}
