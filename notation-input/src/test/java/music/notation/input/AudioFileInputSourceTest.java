package music.notation.input;

import music.notation.performance.PitchedNote;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link AudioFileInputSource}. Each test builds a
 * synthetic WAV (sine wave at a known pitch, optionally with silence
 * gaps) and verifies that the file-driven pipeline produces the same
 * notes the live-mic pipeline would.
 */
class AudioFileInputSourceTest {

    private static final int SAMPLE_RATE = 44100;

    // ── Single-tone detection ────────────────────────────────────────

    @Test
    void detectsSingleA4Note(@TempDir Path tmp) throws Exception {
        File wav = tmp.resolve("a4.wav").toFile();
        // 1 second of A4 (440 Hz), 0.5 amplitude.
        writeSineWav(wav, /*freq=*/ 440.0, /*durationSec=*/ 1.0, /*amp=*/ 0.5);

        List<PitchedNote> notes = analyseAndCollect(wav);
        assertEquals(1, notes.size(),
                "1 second of pure A4 should produce exactly one note");
        assertEquals(69, notes.get(0).midi(), "A4 = MIDI 69");
        assertTrue(notes.get(0).durationMs() > 800,
                "note should span almost the whole second; got " + notes.get(0).durationMs());
    }

    @Test
    void detectsTwoSeparatedNotes(@TempDir Path tmp) throws Exception {
        // 500 ms of C4, 200 ms silence, 500 ms of E4.
        File wav = tmp.resolve("two-notes.wav").toFile();
        byte[] pcm = concat(
                sineSamples(261.6256, 0.5, /*sec=*/ 0.5),   // C4
                silentSamples(0.2),
                sineSamples(329.6276, 0.5, /*sec=*/ 0.5));   // E4
        writeWav(wav, pcm);

        List<PitchedNote> notes = analyseAndCollect(wav);
        assertEquals(2, notes.size(),
                "C4 + silence + E4 should produce two notes; got " + notes.size());
        assertEquals(60, notes.get(0).midi(), "first note = C4 (MIDI 60)");
        assertEquals(64, notes.get(1).midi(), "second note = E4 (MIDI 64)");
        assertTrue(notes.get(1).tickMs() > notes.get(0).tickMs() + notes.get(0).durationMs(),
                "second note must start after the first ends");
    }

    @Test
    void detectsPitchJumpAsTwoNotes(@TempDir Path tmp) throws Exception {
        // 500 ms of C4 immediately followed by 500 ms of G4 (no silence).
        File wav = tmp.resolve("jump.wav").toFile();
        byte[] pcm = concat(
                sineSamples(261.6256, 0.5, 0.5),    // C4
                sineSamples(392.0,    0.5, 0.5));   // G4
        writeWav(wav, pcm);

        List<PitchedNote> notes = analyseAndCollect(wav);
        assertEquals(2, notes.size(),
                "pitch jump without silence still segments into two notes");
        assertEquals(60, notes.get(0).midi());
        assertEquals(67, notes.get(1).midi());
    }

    // ── Format support ───────────────────────────────────────────────

    @Test
    void detects24BitWav(@TempDir Path tmp) throws Exception {
        File wav = tmp.resolve("a4-24bit.wav").toFile();
        // Build 24-bit PCM directly.
        AudioFormat fmt24 = new AudioFormat(SAMPLE_RATE, 24, 1, true, false);
        int frames = (int) Math.round(0.6 * SAMPLE_RATE);
        byte[] pcm = new byte[frames * 3];
        double twoPiF = 2.0 * Math.PI * 440.0 / SAMPLE_RATE;
        for (int i = 0; i < frames; i++) {
            int sample = (int) Math.round(Math.sin(twoPiF * i) * 0.5 * 8_388_607);
            writeLE24(pcm, i * 3, sample);
        }
        writeWav(wav, fmt24, pcm);

        List<PitchedNote> notes = analyseAndCollect(wav);
        assertFalse(notes.isEmpty(), "24-bit WAV must be analysed");
        assertEquals(69, notes.get(0).midi(), "A4 detected from 24-bit input");
    }

    @Test
    void detectsStereoWavViaDownmix(@TempDir Path tmp) throws Exception {
        // Stereo: left = A4, right = silence. Average → half-amplitude A4.
        File wav = tmp.resolve("a4-stereo.wav").toFile();
        int frames = (int) Math.round(0.6 * SAMPLE_RATE);
        AudioFormat stereo = new AudioFormat(SAMPLE_RATE, 16, 2, true, false);
        byte[] pcm = new byte[frames * 4];
        double twoPiF = 2.0 * Math.PI * 440.0 / SAMPLE_RATE;
        for (int i = 0; i < frames; i++) {
            int leftSample = (int) Math.round(Math.sin(twoPiF * i) * 0.6 * 32767);
            writeLE16(pcm, i * 4,     leftSample);   // left
            writeLE16(pcm, i * 4 + 2, 0);            // right
        }
        writeWav(wav, stereo, pcm);

        List<PitchedNote> notes = analyseAndCollect(wav);
        assertFalse(notes.isEmpty(), "stereo WAV must be downmixed and analysed");
        assertEquals(69, notes.get(0).midi(), "A4 still detectable post-downmix");
    }

    // ── Format rejection ─────────────────────────────────────────────

    @Test
    void rejectsUnsupportedBitDepth_silently(@TempDir Path tmp) throws Exception {
        // 8-bit WAV: Java writes it as PCM_UNSIGNED. The worker logs and
        // exits with no notes — the source remains usable for retry.
        File wav = tmp.resolve("8bit.wav").toFile();
        AudioFormat fmt8 = new AudioFormat(SAMPLE_RATE, 8, 1, true, false);
        byte[] pcm = new byte[100];
        for (int i = 0; i < 100; i++) pcm[i] = (byte) 50;
        writeWav(wav, fmt8, pcm);

        List<PitchedNote> notes = analyseAndCollect(wav);
        assertTrue(notes.isEmpty(),
                "8-bit WAV rejected at validation; no notes produced");
    }

    @Test
    void emptyFileProducesNoNotes(@TempDir Path tmp) throws Exception {
        File wav = tmp.resolve("empty.wav").toFile();
        writeWav(wav, new byte[0]);   // 0 frames

        List<PitchedNote> notes = analyseAndCollect(wav);
        assertTrue(notes.isEmpty(), "empty WAV yields no notes");
    }

    // ── Helpers ──────────────────────────────────────────────────────

    /** Drive the source synchronously by waiting on a latch. */
    private static List<PitchedNote> analyseAndCollect(File wav) throws Exception {
        List<PitchedNote> collected = new ArrayList<>();
        CountDownLatch done = new CountDownLatch(1);

        AudioFileInputSource src = new AudioFileInputSource(wav);
        src.setListener(new NoteInputListener() {
            @Override public void onNoteCompleted(PitchedNote n, int v) {
                synchronized (collected) { collected.add(n); }
            }
        });
        src.setOnFinished(done::countDown);
        src.start();
        assertTrue(done.await(10, TimeUnit.SECONDS),
                "WAV analysis must finish within 10 seconds");
        src.close();
        synchronized (collected) {
            return new ArrayList<>(collected);
        }
    }

    /** Write a single sine-wave WAV at 16-bit mono. */
    private static void writeSineWav(File file, double freqHz, double durationSec, double amp) throws IOException {
        writeWav(file, sineSamples(freqHz, amp, durationSec));
    }

    /** Build 16-bit mono PCM bytes for a sine of given frequency / amplitude / duration. */
    private static byte[] sineSamples(double freqHz, double amp, double durationSec) {
        int frames = (int) Math.round(durationSec * SAMPLE_RATE);
        byte[] pcm = new byte[frames * 2];
        double twoPiF = 2.0 * Math.PI * freqHz / SAMPLE_RATE;
        for (int i = 0; i < frames; i++) {
            int sample = (int) Math.round(Math.sin(twoPiF * i) * amp * 32767);
            writeLE16(pcm, i * 2, sample);
        }
        return pcm;
    }

    /** 16-bit mono silence of the given duration. */
    private static byte[] silentSamples(double durationSec) {
        return new byte[(int) Math.round(durationSec * SAMPLE_RATE) * 2];
    }

    private static byte[] concat(byte[]... chunks) {
        int total = 0;
        for (byte[] c : chunks) total += c.length;
        byte[] out = new byte[total];
        int pos = 0;
        for (byte[] c : chunks) {
            System.arraycopy(c, 0, out, pos, c.length);
            pos += c.length;
        }
        return out;
    }

    private static void writeLE16(byte[] buf, int off, int value) {
        buf[off]     = (byte) ( value       & 0xFF);
        buf[off + 1] = (byte) ((value >> 8) & 0xFF);
    }

    private static void writeLE24(byte[] buf, int off, int value) {
        buf[off]     = (byte) ( value        & 0xFF);
        buf[off + 1] = (byte) ((value >>  8) & 0xFF);
        buf[off + 2] = (byte) ((value >> 16) & 0xFF);
    }

    /** Default 16-bit mono WAV writer. */
    private static void writeWav(File file, byte[] pcm) throws IOException {
        writeWav(file, new AudioFormat(SAMPLE_RATE, 16, 1, true, false), pcm);
    }

    private static void writeWav(File file, AudioFormat fmt, byte[] pcm) throws IOException {
        try (AudioInputStream ais = new AudioInputStream(
                new ByteArrayInputStream(pcm), fmt, pcm.length / fmt.getFrameSize())) {
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, file);
        }
    }
}
