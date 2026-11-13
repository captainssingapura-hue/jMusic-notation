package music.notation.transcribe;

import music.notation.duration.Duration;
import music.notation.mxl.MxlImport;
import music.notation.mxl.MxlSplitJsonReader;
import music.notation.performance.PitchedNote;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End to end on a synthesised sung melody: 44.1 kHz stereo WAV, vibrato,
 * scoops into notes, breath noise, 60 ms breaks between notes — through
 * decoding, resampling, YIN, segmentation, key finding and the grid.
 */
class TranscriberTest {

    /** Twinkle in G at ♩ = 100, starting on the downbeat at 0.5 s. */
    private static final double[][] TWINKLE = {
            {67, 1}, {67, 1}, {74, 1}, {74, 1}, {76, 1}, {76, 1}, {74, 2},
            {72, 1}, {72, 1}, {71, 1}, {71, 1}, {69, 1}, {69, 1}, {67, 2}};
    private static final int BPM = 100;

    @TempDir static Path dir;
    static Path wav;

    @BeforeAll
    static void render() throws Exception {
        var notes = Synth.melody(0.5, BPM, 0.06, TWINKLE);
        float[] voice = Synth.sing(44_100, 10.6, Synth.Voice.SINGER, notes);
        float[] right = voice.clone();
        for (int i = 0; i < right.length; i++) right[i] *= 0.9f;
        wav = dir.resolve("twinkle.wav");
        Synth.writeWav(wav, 44_100, voice, right);
    }

    /** Expected written notes: onsets and lengths in quarters. */
    private static List<PitchedNote> expected(int fromIndex, int toIndex) {
        var out = new ArrayList<PitchedNote>();
        Duration at = Duration.zero();
        for (int i = fromIndex; i < toIndex; i++) {
            Duration len = Duration.of((long) TWINKLE[i][1], 4);
            out.add(new PitchedNote(at, len, (int) TWINKLE[i][0]));
            at = at.plus(len);
        }
        return out;
    }

    private static void assertNotes(List<PitchedNote> expected, List<QuantizedNote> actual) {
        assertEquals(expected.size(), actual.size(), "note count: " + actual.stream().map(QuantizedNote::midi).toList());
        for (int i = 0; i < expected.size(); i++) {
            PitchedNote e = expected.get(i);
            QuantizedNote a = actual.get(i);
            assertEquals(e.midi(), a.midi(), "pitch of note " + (i + 1));
            assertTrue(e.at().equalsDuration(a.at()), "onset of note " + (i + 1) + ": " + a.at().numerator() + "/" + a.at().denominator());
            assertTrue(e.duration().equalsDuration(a.duration()),
                    "length of note " + (i + 1) + ": " + a.duration().numerator() + "/" + a.duration().denominator());
            assertTrue(Math.abs(a.cents()) < 15, "in tune: " + a.cents());
        }
    }

    @Test
    void wholeTakeComesOutAsWritten() {
        var t = Transcriber.transcribe(new TranscriptionRequest(wav.toString(), null, null, null,
                new TimingSpec(BPM, 0.5, null, null, null), null));
        assertEquals("twinkle", t.title());
        assertEquals("G major", Keys.name(t.key()));
        assertTrue(t.keyEstimated());
        assertEquals(0, t.pickupBars());
        assertEquals(0, t.dropped());
        assertNotes(expected(0, TWINKLE.length), t.quantized());
        assertEquals(4, t.quantized().getLast().bar());
        assertEquals(3.0, t.quantized().getLast().beat(), 1e-9);
        assertTrue(t.voicedFraction() > 0.7, "voiced " + t.voicedFraction());
    }

    @Test
    void aWindowWithAFixedKeyAndTheFirstOnsetAsDownbeat() {
        // Bar 2 only: 2.9 s – 5.3 s holds E E D(half).
        var t = Transcriber.transcribe(new TranscriptionRequest(wav.toString(), new TimeWindow(2.85, 5.3), null, null,
                new TimingSpec(BPM, null, null, "D major", null), "Bar two"));
        assertEquals("D major", Keys.name(t.key()));
        assertFalse(t.keyEstimated());
        assertEquals(2.9, t.downbeatSec(), 0.04);
        assertEquals(2.85, t.fromSec(), 1e-3);
        assertNotes(expected(4, 7), t.quantized());
        assertEquals(2.9, t.notes().getFirst().startMs() / 1000, 0.04, "times stay file times");
    }

    @Test
    void repeatedNotesWithShortBreaksStaySeparate() {
        // Same pitch three times with only 40 ms between — shorter than YIN's 64 ms window.
        var notes = Synth.melody(0.5, 120, 0.04, new double[][]{{67, 1}, {74, 1}, {74, 1}, {74, 1}});
        float[] x = Synth.sing(Transcriber.ANALYSIS_RATE, 2.8, Synth.Voice.SINGER, notes);
        var t = Transcriber.transcribe(TranscriptionRequest.of("memory.wav", new TimingSpec(120, 0.5, null, null, null)),
                new AudioClip(Transcriber.ANALYSIS_RATE, x));
        assertEquals(List.of(67, 74, 74, 74), t.quantized().stream().map(QuantizedNote::midi).toList());
        for (QuantizedNote q : t.quantized()) assertTrue(q.duration().equalsDuration(Duration.of(1, 4)));
    }

    @Test
    void cliWritesReportCsvJsonAndALoadablePiece() throws Exception {
        Path out = dir.resolve("cli-out");
        var buf = new ByteArrayOutputStream();
        int code = TranscribeCli.run(new String[]{wav.toString(), "--bpm", "100", "--downbeat", "0.5", "--out", out.toString()},
                new PrintStream(buf, true, StandardCharsets.UTF_8));
        String console = buf.toString(StandardCharsets.UTF_8);
        assertEquals(0, code, console);
        assertTrue(console.contains("Key:       G major (estimated)"), console);

        String report = Files.readString(out.resolve("notes.txt"));
        assertTrue(report.contains("14 notes"), report);
        assertTrue(report.lines().anyMatch(l -> l.matches("\\s+7\\s+2\\s+3\\s+D5\\s+1/2 .*")), report);
        assertTrue(Files.readString(out.resolve("pitch-track.csv")).startsWith("time_ms,midi,hz,confidence,rms"));
        assertTrue(Files.readString(out.resolve("transcription.json")).contains("\"quantized\""));

        MxlImport piece = MxlSplitJsonReader.read(out.resolve("twinkle"));
        assertEquals("twinkle", piece.displayName());
        assertEquals(BPM, piece.initialBpm());
        var notes = piece.performance().score().tracks().getFirst().notes();
        assertEquals(14, notes.size());
        var first = (PitchedNote) notes.getFirst();
        assertEquals(67, first.midi());
    }

    @Test
    void cliRequestFileResolvesAudioNextToIt() throws Exception {
        Path req = dir.resolve("req.json");
        Files.writeString(req, """
                { "audio": "twinkle.wav", "timing": { "bpm": 100, "downbeatSec": 0.5 }, "title": "From request" }
                """);
        var buf = new ByteArrayOutputStream();
        int code = TranscribeCli.run(new String[]{req.toString()}, new PrintStream(buf, true, StandardCharsets.UTF_8));
        assertEquals(0, code, buf.toString(StandardCharsets.UTF_8));
        assertTrue(Files.isDirectory(dir.resolve("from-request-transcription").resolve("from-request")));
    }

    @Test
    void cliExampleIsAValidRequest() throws Exception {
        Path ex = dir.resolve("example.json");
        assertEquals(0, TranscribeCli.run(new String[]{"--example", ex.toString()}, new PrintStream(new ByteArrayOutputStream())));
        assertEquals(TranscribeCli.exampleRequest(), TranscriptionJson.readRequest(ex));
    }
}
