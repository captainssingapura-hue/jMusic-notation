package music.notation.play;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Streaming sample-by-sample mixer for multiple WAV inputs into one WAV
 * output. Mixing is performed in constant memory: a fixed buffer per input
 * is read, summed, clipped, and emitted — no full-file load required.
 *
 * <h2>The workflow this serves</h2>
 *
 * <p>Vocal-synth tools (notably ACE Studio) ignore MIDI channel 10 and
 * import drum tracks as Piano. The robust workaround is to use ACE solely
 * for voice synthesis and render every other instrument ourselves via
 * {@link AudioRenderer}, then combine the resulting WAVs into a final
 * mix. This class is that combine step.</p>
 *
 * <h2>Streaming, not in-memory</h2>
 *
 * <p>The mixer subclasses {@link AudioInputStream}, presenting itself as a
 * single readable stream of mixed samples. {@link AudioSystem#write} pulls
 * from this stream in chunks as it writes the WAV. Memory footprint is
 * bounded by the per-source buffer (default 4 KB), independent of file
 * length — a 30-minute mix uses the same memory as a 30-second one.</p>
 *
 * <h2>Format compatibility</h2>
 *
 * <p>All inputs must share the same {@link AudioFormat}: sample rate,
 * bit depth, channel count, encoding, endianness. Mismatched formats throw
 * at construction with a clear message; callers may pre-convert via
 * {@link AudioSystem#getAudioInputStream(AudioFormat, AudioInputStream)}.
 * Supported encodings: 16-, 24-, and 32-bit signed PCM, either endianness.
 * 32-bit float (PCM_FLOAT) is not yet supported.</p>
 *
 * <h2>Mixing rule</h2>
 *
 * <p>Each output sample is the gain-weighted sum of the corresponding
 * input samples, clipped (saturated) to the int16 range. Shorter inputs
 * zero-pad at the end; the output length equals the longest input.
 * Per-source gain defaults to 1.0 (unity); the user can lower it to
 * balance against louder sources. No automatic normalisation — we never
 * change apparent volume across exports.</p>
 */
public final class AudioMixer {

    /** Per-source read buffer size in bytes; ~1024 frames at 16-bit stereo. */
    static final int BUFFER_BYTES = 4096;

    private AudioMixer() {}

    /**
     * Convenience: mix two WAVs at unity gain into {@code output}.
     */
    public static void mix(File a, File b, File output) throws IOException {
        mix(List.of(MixSource.of(a), MixSource.of(b)), output);
    }

    /**
     * Mix any number of WAV sources, each with its own gain, into one
     * output WAV file.
     *
     * @param sources non-empty list of mix sources; all must share a format
     * @param output  destination WAV file
     */
    public static void mix(List<MixSource> sources, File output) throws IOException {
        Objects.requireNonNull(sources, "sources");
        Objects.requireNonNull(output, "output");
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("at least one source required");
        }

        List<AudioInputStream> streams = new ArrayList<>(sources.size());
        try {
            // Open every source and verify formats match.
            AudioFormat format = null;
            long maxFrames = 0L;
            for (MixSource s : sources) {
                AudioInputStream in = openWav(s.file());
                streams.add(in);
                if (format == null) {
                    format = in.getFormat();
                    requireSupportedFormat(format);
                } else if (!formatsCompatible(format, in.getFormat())) {
                    throw new IOException(
                            "Format mismatch in source " + s.file()
                                    + ": expected " + describe(format)
                                    + ", got " + describe(in.getFormat()));
                }
                long fl = in.getFrameLength();
                if (fl > 0) maxFrames = Math.max(maxFrames, fl);
            }

            float[] gains = new float[sources.size()];
            for (int i = 0; i < sources.size(); i++) gains[i] = sources.get(i).gain();

            try (var mixer = new MixingAudioInputStream(streams, format, maxFrames, gains)) {
                AudioSystem.write(mixer, AudioFileFormat.Type.WAVE, output);
            }
        } catch (javax.sound.sampled.UnsupportedAudioFileException e) {
            throw new IOException("unsupported audio file", e);
        } finally {
            for (AudioInputStream s : streams) {
                try { s.close(); } catch (IOException ignored) {}
            }
        }
    }

    // ── Mix source ────────────────────────────────────────────────────

    /**
     * One input to the mixer: a WAV file, a linear gain multiplier, and a
     * start offset in frames (zero = aligned with mix t=0; positive =
     * prepend that many silent frames).
     */
    public record MixSource(File file, float gain, long offsetFrames) {
        public MixSource {
            Objects.requireNonNull(file, "file");
            if (gain < 0f) throw new IllegalArgumentException("gain must be >= 0: " + gain);
            if (offsetFrames < 0L) throw new IllegalArgumentException("offsetFrames must be >= 0: " + offsetFrames);
        }

        /** Unity gain, no offset. */
        public static MixSource of(File f) {
            return new MixSource(f, 1.0f, 0L);
        }

        /** Custom gain, no offset. */
        public static MixSource of(File f, float gain) {
            return new MixSource(f, gain, 0L);
        }
    }

    // ── Format helpers ────────────────────────────────────────────────

    private static AudioInputStream openWav(File f)
            throws IOException, javax.sound.sampled.UnsupportedAudioFileException {
        return AudioSystem.getAudioInputStream(f);
    }

    private static void requireSupportedFormat(AudioFormat fmt) throws IOException {
        if (fmt.getEncoding() != AudioFormat.Encoding.PCM_SIGNED) {
            throw new IOException("only PCM_SIGNED encoding is supported, got " + fmt.getEncoding());
        }
        int bits = fmt.getSampleSizeInBits();
        if (bits != 16 && bits != 24 && bits != 32) {
            throw new IOException("only 16/24/32-bit PCM is supported, got " + bits + " bits");
        }
        int ch = fmt.getChannels();
        if (ch < 1 || ch > 2) {
            throw new IOException("only mono or stereo is supported, got " + ch + " channels");
        }
    }

    private static boolean formatsCompatible(AudioFormat a, AudioFormat b) {
        return a.getEncoding().equals(b.getEncoding())
                && Float.compare(a.getSampleRate(), b.getSampleRate()) == 0
                && a.getSampleSizeInBits() == b.getSampleSizeInBits()
                && a.getChannels() == b.getChannels()
                && a.isBigEndian() == b.isBigEndian();
    }

    private static String describe(AudioFormat f) {
        return String.format("%.0fHz/%dbit/%dch %s",
                f.getSampleRate(), f.getSampleSizeInBits(), f.getChannels(),
                f.isBigEndian() ? "BE" : "LE");
    }

    // ── Streaming inner class ─────────────────────────────────────────

    /**
     * Custom {@link AudioInputStream} that pulls one buffer at a time from
     * each underlying source, performs sample-wise summation + clipping,
     * and exposes the mixed bytes as its own readable stream.
     *
     * <p>The output length is fixed at construction (max input length plus
     * any source offsets) so the WAV header can be sized correctly. Once
     * the configured frame count has been emitted, further reads return
     * EOF, even if any underlying stream has data left.</p>
     */
    static final class MixingAudioInputStream extends AudioInputStream {

        private final List<AudioInputStream> sources;
        private final float[] gains;
        private final int bytesPerFrame;
        private final int bytesPerSample;
        private final boolean bigEndian;
        private final long totalFrames;

        // Per-source intake buffers. Reused across read() calls.
        private final byte[][] intake;
        // Number of valid bytes currently in each intake buffer.
        private final int[] intakeFilled;
        // EOF flag per source (true once a source returned -1).
        private final boolean[] sourceEof;

        private long framesEmitted = 0L;

        MixingAudioInputStream(List<AudioInputStream> sources,
                               AudioFormat format,
                               long totalFrames,
                               float[] gains) {
            // The "base stream" we hand to super is unused — we override read().
            // The frame-length argument is what AudioSystem.write uses for the
            // WAV header's data-chunk size.
            super(new ByteArrayInputStream(new byte[0]), format, totalFrames);
            this.sources = sources;
            this.gains = gains;
            this.bytesPerFrame = format.getFrameSize();
            this.bytesPerSample = format.getSampleSizeInBits() / 8;
            this.bigEndian = format.isBigEndian();
            this.totalFrames = totalFrames;
            this.intake = new byte[sources.size()][BUFFER_BYTES];
            this.intakeFilled = new int[sources.size()];
            this.sourceEof = new boolean[sources.size()];
        }

        @Override
        public int read() throws IOException {
            // Single-byte read is required by AudioInputStream contract but
            // never used by AudioSystem.write — implement via a tiny buffer.
            byte[] one = new byte[1];
            int n = read(one, 0, 1);
            return n <= 0 ? -1 : (one[0] & 0xFF);
        }

        @Override
        public int read(byte[] dst, int off, int len) throws IOException {
            if (framesEmitted >= totalFrames) return -1;

            // Round len down to a whole number of frames.
            int framesRequested = Math.min(len / bytesPerFrame,
                    (int) Math.min(Integer.MAX_VALUE, totalFrames - framesEmitted));
            if (framesRequested <= 0) return -1;

            int bytesRequested = framesRequested * bytesPerFrame;
            // Cap by the smaller of: requested bytes, or our intake buffer size.
            int bytesThisRound = Math.min(bytesRequested, BUFFER_BYTES);
            int framesThisRound = bytesThisRound / bytesPerFrame;
            bytesThisRound = framesThisRound * bytesPerFrame;

            // Pull from each source.
            for (int s = 0; s < sources.size(); s++) {
                intakeFilled[s] = readFully(sources.get(s), intake[s], bytesThisRound, s);
            }

            // Mix sample-wise into dst.
            mixInto(dst, off, bytesThisRound);

            framesEmitted += framesThisRound;
            return bytesThisRound;
        }

        /**
         * Best-effort fill: read up to {@code want} bytes from {@code in}.
         * Returns the actual number of bytes read (0 ≤ result ≤ want).
         * On EOF, marks the source and returns whatever was read.
         */
        private int readFully(AudioInputStream in, byte[] buf, int want, int sourceIdx) throws IOException {
            if (sourceEof[sourceIdx]) return 0;
            int got = 0;
            while (got < want) {
                int n = in.read(buf, got, want - got);
                if (n < 0) {
                    sourceEof[sourceIdx] = true;
                    break;
                }
                got += n;
            }
            return got;
        }

        /**
         * Sum samples across sources into dst. Each source contributes
         * up to its own {@code intakeFilled} bytes; bytes beyond that
         * are treated as silence (zero).
         *
         * <p>Works for 16-, 24-, and 32-bit signed PCM. The accumulator is
         * a {@code long} so 32-bit sums don't overflow the gain × source
         * combination; clipping saturates to the destination width's
         * int min/max.</p>
         */
        private void mixInto(byte[] dst, int dstOff, int bytesThisRound) {
            for (int byteIdx = 0; byteIdx < bytesThisRound; byteIdx += bytesPerSample) {
                long sum = 0L;
                for (int s = 0; s < sources.size(); s++) {
                    int filled = intakeFilled[s];
                    if (byteIdx + bytesPerSample - 1 >= filled) continue;   // past EOF for this source
                    int sample = readSample(intake[s], byteIdx, bytesPerSample, bigEndian);
                    sum += Math.round((double) sample * gains[s]);
                }
                long clipped = clipForBitDepth(sum, bytesPerSample);
                writeSample(dst, dstOff + byteIdx, clipped, bytesPerSample, bigEndian);
            }
        }

        @Override
        public int available() {
            long remaining = (totalFrames - framesEmitted) * bytesPerFrame;
            return (int) Math.min(Integer.MAX_VALUE, Math.max(0, remaining));
        }

        @Override
        public void close() throws IOException {
            // Underlying sources are owned by the caller of AudioMixer.mix;
            // closing them here would double-close. Just propagate the
            // contract that this stream is no longer readable.
        }
    }

    // ── Sample packing helpers (16-/24-/32-bit signed PCM) ────────────

    /** Read one signed PCM sample of {@code bytesPerSample} width. Returns the
     *  sign-extended sample as int (fits 24-bit; 32-bit also fits in int by
     *  construction since {@code int} is exactly 32 bits). */
    static int readSample(byte[] buf, int off, int bytesPerSample, boolean bigEndian) {
        return switch (bytesPerSample) {
            case 2 -> readInt16(buf, off, bigEndian);
            case 3 -> readInt24(buf, off, bigEndian);
            case 4 -> readInt32(buf, off, bigEndian);
            default -> throw new IllegalStateException(
                    "unsupported sample width: " + bytesPerSample + " bytes");
        };
    }

    /** Write one signed PCM sample. {@code value} must already be clipped to
     *  the bit-depth range; this method only packs bytes, it doesn't saturate. */
    static void writeSample(byte[] buf, int off, long value, int bytesPerSample, boolean bigEndian) {
        switch (bytesPerSample) {
            case 2 -> writeInt16(buf, off, (int) value, bigEndian);
            case 3 -> writeInt24(buf, off, (int) value, bigEndian);
            case 4 -> writeInt32(buf, off, (int) value, bigEndian);
            default -> throw new IllegalStateException(
                    "unsupported sample width: " + bytesPerSample + " bytes");
        }
    }

    static int readInt16(byte[] buf, int off, boolean bigEndian) {
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

    static void writeInt16(byte[] buf, int off, int value, boolean bigEndian) {
        int v = value & 0xFFFF;
        if (bigEndian) {
            buf[off]     = (byte) ((v >> 8) & 0xFF);
            buf[off + 1] = (byte) ( v       & 0xFF);
        } else {
            buf[off]     = (byte) ( v       & 0xFF);
            buf[off + 1] = (byte) ((v >> 8) & 0xFF);
        }
    }

    static int readInt24(byte[] buf, int off, boolean bigEndian) {
        int b0, b1, b2;
        if (bigEndian) {
            b0 = buf[off]     & 0xFF;     // most-significant
            b1 = buf[off + 1] & 0xFF;
            b2 = buf[off + 2] & 0xFF;
        } else {
            b2 = buf[off]     & 0xFF;     // least-significant
            b1 = buf[off + 1] & 0xFF;
            b0 = buf[off + 2] & 0xFF;
        }
        int v = (b0 << 16) | (b1 << 8) | b2;
        // Sign-extend from 24 to 32 bits.
        return (v & 0x800000) != 0 ? (v | 0xFF000000) : v;
    }

    static void writeInt24(byte[] buf, int off, int value, boolean bigEndian) {
        int v = value & 0xFFFFFF;
        int b0 = (v >> 16) & 0xFF;
        int b1 = (v >>  8) & 0xFF;
        int b2 =  v        & 0xFF;
        if (bigEndian) {
            buf[off]     = (byte) b0;
            buf[off + 1] = (byte) b1;
            buf[off + 2] = (byte) b2;
        } else {
            buf[off]     = (byte) b2;
            buf[off + 1] = (byte) b1;
            buf[off + 2] = (byte) b0;
        }
    }

    static int readInt32(byte[] buf, int off, boolean bigEndian) {
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
        // Int already 32-bit signed — natural fit, no extra sign extension needed.
        return (b0 << 24) | (b1 << 16) | (b2 << 8) | b3;
    }

    static void writeInt32(byte[] buf, int off, int value, boolean bigEndian) {
        int b0 = (value >>> 24) & 0xFF;
        int b1 = (value >>> 16) & 0xFF;
        int b2 = (value >>>  8) & 0xFF;
        int b3 =  value         & 0xFF;
        if (bigEndian) {
            buf[off]     = (byte) b0;
            buf[off + 1] = (byte) b1;
            buf[off + 2] = (byte) b2;
            buf[off + 3] = (byte) b3;
        } else {
            buf[off]     = (byte) b3;
            buf[off + 1] = (byte) b2;
            buf[off + 2] = (byte) b1;
            buf[off + 3] = (byte) b0;
        }
    }

    /** Saturate {@code v} to the signed range of a sample of the given width.
     *  Returns {@code long} so 32-bit clipping doesn't itself overflow. */
    static long clipForBitDepth(long v, int bytesPerSample) {
        return switch (bytesPerSample) {
            case 2 -> clipInt16Long(v);
            case 3 -> clipInt24Long(v);
            case 4 -> clipInt32Long(v);
            default -> throw new IllegalStateException("bytesPerSample = " + bytesPerSample);
        };
    }

    static int clipInt16(int v) {
        if (v >  32767) return 32767;
        if (v < -32768) return -32768;
        return v;
    }

    private static long clipInt16Long(long v) {
        if (v >  32767L) return  32767L;
        if (v < -32768L) return -32768L;
        return v;
    }

    static int clipInt24(int v) {
        if (v >  8_388_607)  return  8_388_607;
        if (v < -8_388_608)  return -8_388_608;
        return v;
    }

    private static long clipInt24Long(long v) {
        if (v >  8_388_607L)  return  8_388_607L;
        if (v < -8_388_608L)  return -8_388_608L;
        return v;
    }

    static int clipInt32(long v) {
        if (v > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        if (v < Integer.MIN_VALUE) return Integer.MIN_VALUE;
        return (int) v;
    }

    private static long clipInt32Long(long v) {
        if (v > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        if (v < Integer.MIN_VALUE) return Integer.MIN_VALUE;
        return v;
    }
}
