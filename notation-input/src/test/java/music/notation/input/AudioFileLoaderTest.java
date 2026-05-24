package music.notation.input;

import org.junit.jupiter.api.AfterEach;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AudioFileLoader}'s deterministic behaviour. The
 * native-format path (WAV/AIFF/AU) is fully tested; the ffmpeg fallback
 * is exercised opportunistically — when ffmpeg isn't installed we
 * assert the error message instead of skipping silently.
 */
class AudioFileLoaderTest {

    @AfterEach
    void resetProbe() {
        // Tests touch the cached ffmpeg-presence flag indirectly; reset
        // so subsequent test classes see a clean state.
        AudioFileLoader.resetFfmpegProbe();
    }

    // ── Native path ───────────────────────────────────────────────────

    @Test
    void loadsWavNativelyWithoutTempFile(@TempDir Path tmp) throws Exception {
        File wav = tmp.resolve("a.wav").toFile();
        writeSimpleWav(wav);

        try (AudioFileLoader.LoadedAudio loaded = AudioFileLoader.load(wav)) {
            assertNotNull(loaded.stream());
            assertEquals(wav, loaded.source());
            assertNull(loaded.tempFile(), "native WAV load must not create a temp file");
            assertFalse(loaded.wasConverted());
        }
    }

    @Test
    void closeReleasesResources(@TempDir Path tmp) throws Exception {
        File wav = tmp.resolve("close-test.wav").toFile();
        writeSimpleWav(wav);

        AudioFileLoader.LoadedAudio loaded = AudioFileLoader.load(wav);
        loaded.close();   // should not throw
        // Re-close is safe (close on an already-closed AudioInputStream is a no-op in practice).
        loaded.close();
    }

    @Test
    void rejectsNullFile() {
        assertThrows(IllegalArgumentException.class, () -> AudioFileLoader.load(null));
    }

    @Test
    void rejectsNonExistentFile(@TempDir Path tmp) {
        File ghost = tmp.resolve("does-not-exist.wav").toFile();
        IOException ex = assertThrows(IOException.class, () -> AudioFileLoader.load(ghost));
        assertTrue(ex.getMessage().contains("not a file"));
    }

    // ── Native-format detection ───────────────────────────────────────

    @Test
    void isNativelySupportedFlagsCorrectExtensions(@TempDir Path tmp) {
        assertTrue(AudioFileLoader.isNativelySupported(tmp.resolve("x.wav").toFile()));
        assertTrue(AudioFileLoader.isNativelySupported(tmp.resolve("x.WAV").toFile()));
        assertTrue(AudioFileLoader.isNativelySupported(tmp.resolve("x.aif").toFile()));
        assertTrue(AudioFileLoader.isNativelySupported(tmp.resolve("x.aiff").toFile()));
        assertTrue(AudioFileLoader.isNativelySupported(tmp.resolve("x.au").toFile()));

        assertFalse(AudioFileLoader.isNativelySupported(tmp.resolve("x.m4a").toFile()));
        assertFalse(AudioFileLoader.isNativelySupported(tmp.resolve("x.mp3").toFile()));
        assertFalse(AudioFileLoader.isNativelySupported(tmp.resolve("x.ogg").toFile()));
        assertFalse(AudioFileLoader.isNativelySupported(tmp.resolve("x.flac").toFile()));
        assertFalse(AudioFileLoader.isNativelySupported(null));
    }

    // ── ffmpeg fallback path ─────────────────────────────────────────

    @Test
    void nonNativeFormatGivesClearErrorWhenFfmpegMissing(@TempDir Path tmp) throws Exception {
        // We can't synthesise a valid .m4a without ffmpeg, but we can
        // verify the error message: create a .m4a-named file with
        // arbitrary bytes — AudioSystem will reject it, the loader
        // falls back to ffmpeg, ffmpeg either (a) succeeds and reports
        // a parse error, or (b) is absent and we get the "Install
        // ffmpeg…" message.
        File fakeM4a = tmp.resolve("fake.m4a").toFile();
        java.nio.file.Files.writeString(fakeM4a.toPath(), "not really audio");

        IOException ex = assertThrows(IOException.class, () -> AudioFileLoader.load(fakeM4a));
        String msg = ex.getMessage();
        if (AudioFileLoader.isFfmpegAvailable()) {
            // ffmpeg present → it should fail to parse the bogus bytes.
            assertTrue(msg.contains("ffmpeg") || msg.toLowerCase().contains("unreadable"),
                    "with ffmpeg installed, bogus input must fail with an ffmpeg-related error; got: " + msg);
        } else {
            // ffmpeg absent → install hint shown.
            assertTrue(msg.contains("Install ffmpeg") || msg.contains("not supported"),
                    "without ffmpeg, must show install hint; got: " + msg);
        }
    }

    @Test
    void ffmpegProbeIsCachedAcrossCalls() {
        AudioFileLoader.resetFfmpegProbe();
        boolean first  = AudioFileLoader.isFfmpegAvailable();
        boolean second = AudioFileLoader.isFfmpegAvailable();
        boolean third  = AudioFileLoader.isFfmpegAvailable();
        assertEquals(first, second);
        assertEquals(first, third);
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private static void writeSimpleWav(File file) throws IOException {
        // 100 frames of silence at 16-bit mono 44.1 kHz.
        AudioFormat fmt = new AudioFormat(44100f, 16, 1, true, false);
        byte[] pcm = new byte[200];   // 100 frames * 2 bytes
        try (AudioInputStream ais = new AudioInputStream(
                new ByteArrayInputStream(pcm), fmt, 100)) {
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, file);
        }
    }
}
