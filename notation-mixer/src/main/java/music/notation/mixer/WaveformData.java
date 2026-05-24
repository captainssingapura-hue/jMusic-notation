package music.notation.mixer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;

/**
 * Compact min/max overview of a WAV's PCM samples, computed in a single
 * streaming pass — no full-file load required.
 *
 * <h2>What this is</h2>
 *
 * <p>For waveform rendering, the canonical approach is one vertical line
 * per pixel column, drawn from the minimum sample value in that column's
 * range to the maximum. This preserves transient peaks (the visually
 * informative part) while compressing the data by orders of magnitude.</p>
 *
 * <p>{@code WaveformData} performs that compression at load time. It reads
 * the source WAV in fixed-size byte chunks, decodes the 16-bit signed PCM
 * samples, and accumulates per-bucket min/max pairs into a small array.
 * The bucket size is chosen so the resulting array is well-sized for
 * typical display widths (~10 000–100 000 buckets for a 3-minute song).</p>
 *
 * <h2>Memory profile</h2>
 *
 * <p>A 3-minute stereo 44.1 kHz WAV is ~32 MB on disk. Its overview at
 * 100 samples/bucket is ~130 KB — three orders of magnitude smaller.
 * Constant memory during load (one chunk buffer); the overview array is
 * the only persistent allocation.</p>
 *
 * <h2>Channels</h2>
 *
 * <p>Stereo files are reduced to a single mono envelope (left and right
 * samples are averaged before being folded into the overview). The
 * alignment-verification use case doesn't need per-channel detail; a
 * single waveform shape per file is what's compared across tracks.</p>
 */
public final class WaveformData {

    /** Default samples-per-bucket when none specified. */
    public static final int DEFAULT_SAMPLES_PER_BUCKET = 100;

    private final File source;
    private final AudioFormat format;
    private final long totalFrames;
    private final int samplesPerBucket;
    /** Interleaved min/max pairs: [min0, max0, min1, max1, ...]. */
    private final short[] overview;
    private final long durationMicros;

    private WaveformData(File source, AudioFormat format, long totalFrames,
                         int samplesPerBucket, short[] overview) {
        this.source = source;
        this.format = format;
        this.totalFrames = totalFrames;
        this.samplesPerBucket = samplesPerBucket;
        this.overview = overview;
        this.durationMicros = Math.round(totalFrames * 1_000_000.0 / format.getSampleRate());
    }

    // ── Public accessors ──────────────────────────────────────────────

    public File source()           { return source; }
    public AudioFormat format()    { return format; }
    public long totalFrames()      { return totalFrames; }
    public long durationMicros()   { return durationMicros; }
    public int samplesPerBucket()  { return samplesPerBucket; }
    public int bucketCount()       { return overview.length / 2; }

    /** Min sample in bucket {@code i}. */
    public short min(int i) { return overview[i * 2]; }
    /** Max sample in bucket {@code i}. */
    public short max(int i) { return overview[i * 2 + 1]; }

    // ── Factory ───────────────────────────────────────────────────────

    /**
     * Stream the WAV at {@code file} and build an overview at the default
     * bucket size. Reads in fixed-size byte chunks; memory used is
     * constant (one read buffer) plus the resulting overview array.
     */
    public static WaveformData load(File file) throws IOException {
        return load(file, DEFAULT_SAMPLES_PER_BUCKET);
    }

    /**
     * Stream the WAV and build an overview with the requested bucket size.
     * Smaller buckets → larger overview, finer visual resolution. The
     * tradeoff is purely memory + paint time; correctness is identical.
     */
    public static WaveformData load(File file, int samplesPerBucket) throws IOException {
        if (samplesPerBucket < 1) {
            throw new IllegalArgumentException("samplesPerBucket must be >= 1: " + samplesPerBucket);
        }
        try (AudioInputStream in = AudioSystem.getAudioInputStream(file)) {
            AudioFormat fmt = in.getFormat();
            validate(fmt);
            long totalFrames = in.getFrameLength();
            int bucketCount = (int) Math.max(1L, (totalFrames + samplesPerBucket - 1) / samplesPerBucket);
            short[] overview = new short[bucketCount * 2];
            for (int i = 0; i < bucketCount; i++) {
                overview[i * 2]     = Short.MAX_VALUE;   // min placeholder
                overview[i * 2 + 1] = Short.MIN_VALUE;   // max placeholder
            }
            streamIntoBuckets(in, fmt, overview, samplesPerBucket);
            // Any buckets we never touched (totalFrames == 0 edge) collapse to 0..0.
            for (int i = 0; i < bucketCount; i++) {
                if (overview[i * 2] > overview[i * 2 + 1]) {
                    overview[i * 2]     = 0;
                    overview[i * 2 + 1] = 0;
                }
            }
            return new WaveformData(file, fmt, totalFrames, samplesPerBucket, overview);
        } catch (UnsupportedAudioFileException e) {
            throw new IOException("unsupported WAV: " + file, e);
        }
    }

    // ── Internals ─────────────────────────────────────────────────────

    private static void validate(AudioFormat fmt) throws IOException {
        if (fmt.getEncoding() != AudioFormat.Encoding.PCM_SIGNED) {
            throw new IOException("only PCM_SIGNED supported, got " + fmt.getEncoding());
        }
        int bits = fmt.getSampleSizeInBits();
        if (bits != 16 && bits != 24 && bits != 32) {
            throw new IOException("only 16/24/32-bit PCM supported, got " + bits + " bits");
        }
        int ch = fmt.getChannels();
        if (ch < 1 || ch > 2) {
            throw new IOException("only mono or stereo supported, got " + ch);
        }
    }

    private static void streamIntoBuckets(AudioInputStream in, AudioFormat fmt,
                                          short[] overview, int samplesPerBucket) throws IOException {
        int channels       = fmt.getChannels();
        int frameSize      = fmt.getFrameSize();
        int bytesPerSample = fmt.getSampleSizeInBits() / 8;
        boolean be         = fmt.isBigEndian();
        int bucketCount    = overview.length / 2;

        // Right-shift used to scale the raw sample down into a 16-bit overview
        // value. 16-bit samples already fit; 24-bit values are >> 8;
        // 32-bit values are >> 16. The overview's purpose is display, so we
        // preserve the relative shape, not the full numeric precision.
        int overviewShift = (bytesPerSample - 2) * 8;

        // Read in chunks of whole frames; 4 KB is generous and JIT-friendly.
        int bufFrames = 1024;
        byte[] buf = new byte[bufFrames * frameSize];

        long frameIdx = 0;
        while (true) {
            int want = buf.length;
            int got = 0;
            while (got < want) {
                int n = in.read(buf, got, want - got);
                if (n < 0) break;
                got += n;
            }
            if (got <= 0) break;
            int framesInBuf = got / frameSize;
            for (int f = 0; f < framesInBuf; f++) {
                int off = f * frameSize;
                int sample;
                if (channels == 1) {
                    sample = readSample(buf, off, bytesPerSample, be);
                } else {
                    int l = readSample(buf, off,                  bytesPerSample, be);
                    int r = readSample(buf, off + bytesPerSample, bytesPerSample, be);
                    sample = (l + r) / 2;
                }
                short overviewSample = (short) (sample >> overviewShift);
                int bucket = (int) Math.min(bucketCount - 1L, frameIdx / samplesPerBucket);
                int minIdx = bucket * 2;
                int maxIdx = minIdx + 1;
                if (overviewSample < overview[minIdx]) overview[minIdx] = overviewSample;
                if (overviewSample > overview[maxIdx]) overview[maxIdx] = overviewSample;
                frameIdx++;
            }
            if (got < want) break;   // EOF reached mid-buffer
        }
    }

    private static int readSample(byte[] buf, int off, int bytesPerSample, boolean bigEndian) {
        return switch (bytesPerSample) {
            case 2 -> readInt16(buf, off, bigEndian);
            case 3 -> readInt24(buf, off, bigEndian);
            case 4 -> readInt32(buf, off, bigEndian);
            default -> throw new IllegalStateException(
                    "unsupported sample width: " + bytesPerSample + " bytes");
        };
    }

    private static int readInt16(byte[] buf, int off, boolean bigEndian) {
        int lo, hi;
        if (bigEndian) {
            hi = buf[off]     & 0xFF;
            lo = buf[off + 1] & 0xFF;
        } else {
            lo = buf[off]     & 0xFF;
            hi = buf[off + 1] & 0xFF;
        }
        int v = (hi << 8) | lo;
        return (v >= 0x8000) ? (v - 0x10000) : v;
    }

    private static int readInt24(byte[] buf, int off, boolean bigEndian) {
        int b0, b1, b2;
        if (bigEndian) {
            b0 = buf[off]     & 0xFF;
            b1 = buf[off + 1] & 0xFF;
            b2 = buf[off + 2] & 0xFF;
        } else {
            b2 = buf[off]     & 0xFF;
            b1 = buf[off + 1] & 0xFF;
            b0 = buf[off + 2] & 0xFF;
        }
        int v = (b0 << 16) | (b1 << 8) | b2;
        return (v & 0x800000) != 0 ? (v | 0xFF000000) : v;
    }

    private static int readInt32(byte[] buf, int off, boolean bigEndian) {
        int b0, b1, b2, b3;
        if (bigEndian) {
            b0 = buf[off]     & 0xFF;
            b1 = buf[off + 1] & 0xFF;
            b2 = buf[off + 2] & 0xFF;
            b3 = buf[off + 3] & 0xFF;
        } else {
            b3 = buf[off]     & 0xFF;
            b2 = buf[off + 1] & 0xFF;
            b1 = buf[off + 2] & 0xFF;
            b0 = buf[off + 3] & 0xFF;
        }
        return (b0 << 24) | (b1 << 16) | (b2 << 8) | b3;
    }
}
