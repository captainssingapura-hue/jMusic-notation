package music.notation.input;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Opens an audio file as a WAV-formatted {@link AudioInputStream},
 * regardless of source codec.
 *
 * <h2>Native vs. converted</h2>
 *
 * <p>The JDK's {@link AudioSystem} natively supports a small handful of
 * formats — <b>WAV</b>, <b>AIFF</b>, <b>AU</b>, and (depending on the
 * SPI providers on the classpath) sometimes FLAC. For everything else
 * — notably <b>.m4a</b>, <b>.mp3</b>, <b>.ogg</b> — this loader shells
 * out to the system {@code ffmpeg} binary, converts to a 16-bit PCM
 * temp WAV, and returns an {@link AudioInputStream} reading from that.</p>
 *
 * <p>The {@link LoadedAudio} wrapper owns both the stream and the temp
 * file; closing it cleans both up.</p>
 *
 * <h2>ffmpeg dependency</h2>
 *
 * <p>For non-native formats, the user must have {@code ffmpeg} on their
 * PATH. When it's missing, {@link #load} throws an {@link IOException}
 * with a clear message naming the missing tool and the affected file —
 * UIs can surface this directly to the user.</p>
 *
 * <h2>Why ffmpeg over a Java decoder</h2>
 *
 * <ul>
 *   <li>One tool covers .m4a / .mp3 / .ogg / .flac / .aac / .opus all
 *       at once — no per-codec dependency hunt.</li>
 *   <li>ffmpeg is more correct and battle-tested than any Java codec
 *       we'd otherwise pull in.</li>
 *   <li>Most users on macOS / Linux already have it; on Windows the
 *       install is a one-time download.</li>
 *   <li>Keeps {@code notation-input} dep-free — only an optional
 *       runtime tool, not a Maven coordinate.</li>
 * </ul>
 */
public final class AudioFileLoader {

    /** Cached ffmpeg-presence check, computed lazily once per JVM. */
    private static volatile Boolean ffmpegAvailable;

    private AudioFileLoader() {}

    /**
     * Open {@code file} as a {@link LoadedAudio}. The returned stream
     * delivers WAV-format PCM regardless of input codec; close it when
     * done to release both the stream and any temp file created.
     */
    public static LoadedAudio load(File file) throws IOException {
        if (file == null) throw new IllegalArgumentException("file required");
        if (!file.isFile()) throw new IOException("not a file: " + file);

        // Try native first — covers WAV/AIFF/AU and any installed SPIs.
        try {
            AudioInputStream in = AudioSystem.getAudioInputStream(file);
            return new LoadedAudio(in, file, /*tempFile=*/ null);
        } catch (UnsupportedAudioFileException notNative) {
            // Fall through to ffmpeg path.
        }
        return loadViaFfmpeg(file);
    }

    /** True if {@code ffmpeg -version} succeeds. Cached after first call. */
    public static boolean isFfmpegAvailable() {
        Boolean cached = ffmpegAvailable;
        if (cached != null) return cached;
        synchronized (AudioFileLoader.class) {
            if (ffmpegAvailable != null) return ffmpegAvailable;
            ffmpegAvailable = probeFfmpeg();
            return ffmpegAvailable;
        }
    }

    /**
     * Visible for tests — clear the cached probe result so a subsequent
     * call re-probes the environment.
     */
    static void resetFfmpegProbe() {
        synchronized (AudioFileLoader.class) {
            ffmpegAvailable = null;
        }
    }

    // ── ffmpeg path ───────────────────────────────────────────────────

    private static LoadedAudio loadViaFfmpeg(File source) throws IOException {
        if (!isFfmpegAvailable()) {
            throw new IOException(
                    "Cannot read " + source.getName() + ": format not supported by Java's built-in "
                            + "audio system. Install ffmpeg (https://ffmpeg.org/download.html) and "
                            + "ensure it's on your PATH to enable processing of compressed audio "
                            + "formats (.m4a, .mp3, .ogg, .flac, .aac, ...).");
        }

        File tempWav = Files.createTempFile("notation-input-", ".wav").toFile();
        tempWav.deleteOnExit();

        // -y: overwrite output
        // -i <source>
        // -vn: no video stream
        // -acodec pcm_s16le: 16-bit signed little-endian PCM (broadly compatible)
        // -ar 44100: resample to 44.1 kHz (consistent with our analysis defaults)
        // -ac 1: mono — pitch detection is monophonic anyway
        // -f wav: explicit WAV container
        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg", "-y",
                "-i", source.getAbsolutePath(),
                "-vn",
                "-acodec", "pcm_s16le",
                "-ar", "44100",
                "-ac", "1",
                "-f", "wav",
                tempWav.getAbsolutePath());
        pb.redirectErrorStream(true);

        Process p = pb.start();
        // Drain stdout/stderr so ffmpeg doesn't block on a full pipe buffer
        // when it logs more than a few KB (it always does — encoder banners,
        // stream info, progress lines, etc.).
        StringBuilder log = new StringBuilder();
        try (var reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.append(line).append('\n');
            }
        }

        boolean finished;
        try {
            finished = p.waitFor(60, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            p.destroyForcibly();
            tempWav.delete();
            throw new IOException("ffmpeg conversion interrupted for " + source.getName());
        }

        if (!finished) {
            p.destroyForcibly();
            tempWav.delete();
            throw new IOException("ffmpeg conversion timed out (>60s) for " + source.getName());
        }
        if (p.exitValue() != 0) {
            tempWav.delete();
            throw new IOException("ffmpeg failed (exit " + p.exitValue()
                    + ") for " + source.getName() + " — last log:\n" + tail(log, 1000));
        }

        try {
            AudioInputStream in = AudioSystem.getAudioInputStream(tempWav);
            return new LoadedAudio(in, source, tempWav);
        } catch (UnsupportedAudioFileException e) {
            tempWav.delete();
            throw new IOException("ffmpeg produced an unreadable WAV for " + source.getName(), e);
        }
    }

    private static boolean probeFfmpeg() {
        try {
            ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            // Drain output so the process can exit cleanly.
            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream()))) {
                while (reader.readLine() != null) { /* discard */ }
            }
            boolean finished = p.waitFor(3, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                return false;
            }
            return p.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    private static String tail(StringBuilder sb, int maxChars) {
        int start = Math.max(0, sb.length() - maxChars);
        return sb.substring(start);
    }

    // ── LoadedAudio ───────────────────────────────────────────────────

    /**
     * Owned handle bundling the decoded {@link AudioInputStream}, the
     * original source file, and any temp file produced during
     * conversion. {@link #close} releases the stream and deletes the
     * temp file (if any).
     */
    public static final class LoadedAudio implements AutoCloseable {
        private final AudioInputStream stream;
        private final File source;
        private final File tempFile;

        LoadedAudio(AudioInputStream stream, File source, File tempFile) {
            this.stream = stream;
            this.source = source;
            this.tempFile = tempFile;
        }

        public AudioInputStream stream() { return stream; }
        public File source()             { return source; }
        /** Non-null when the source was converted via ffmpeg; null for native loads. */
        public File tempFile()           { return tempFile; }
        public boolean wasConverted()    { return tempFile != null; }

        @Override
        public void close() throws IOException {
            try { stream.close(); } finally {
                if (tempFile != null) {
                    try { Files.deleteIfExists(tempFile.toPath()); } catch (IOException ignored) {}
                }
            }
        }
    }

    // ── Utility: file-extension hint ──────────────────────────────────

    /**
     * True when the file extension suggests a format Java's
     * {@link AudioSystem} reads natively (wav/aif/aiff/au). Used by UIs
     * to decide whether to warn about needing ffmpeg upfront.
     */
    public static boolean isNativelySupported(File file) {
        if (file == null) return false;
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".wav") || name.endsWith(".aif")
                || name.endsWith(".aiff") || name.endsWith(".au");
    }
}
