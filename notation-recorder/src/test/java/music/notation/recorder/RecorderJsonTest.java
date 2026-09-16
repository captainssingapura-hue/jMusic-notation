package music.notation.recorder;

import music.notation.duration.Duration;
import music.notation.expressivity.TrackId;
import music.notation.mxl.MxlImport;
import music.notation.mxl.MxlSplitJsonReader;
import music.notation.mxl.MxlSplitJsonWriter;
import music.notation.performance.Performance;
import music.notation.performance.PitchedNote;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the captured-notes → JSON-folder → NotationApp round-trip:
 *
 * <ul>
 *   <li>{@link RecorderApp#buildPerformance} packages captured notes into
 *       a structurally-correct {@link Performance} with a single VOCAL-style
 *       PITCHED track and a populated {@code Velocities} side-channel.</li>
 *   <li>{@link RecorderApp#buildMxlImport} wraps that Performance in an
 *       {@link MxlImport} with neutral 4/4 + C major defaults.</li>
 *   <li>{@link MxlSplitJsonWriter#write} produces the standard JSON
 *       folder layout (meta.json + tempo.json + track-*.json + velocity.json).</li>
 *   <li>{@link MxlSplitJsonReader#read} reads it back without loss —
 *       exactly the path NotationApp uses for "Load JSON folder…".</li>
 * </ul>
 */
class RecorderJsonTest {

    /**
     * Real-time capture is projected onto the Duration timeline at the
     * default 120 bpm (whole note = 2000 ms), matching the input sources.
     */
    private static Duration ms(long millis) {
        return Duration.of(millis, 2000);
    }

    // ── Performance construction (in-memory) ─────────────────────────

    @Test
    void buildsPerformanceWithSingleVocalTrack() {
        var captured = List.of(
                new RecorderApp.CapturedNote(new PitchedNote(ms(0), ms(500), 60), 80),
                new RecorderApp.CapturedNote(new PitchedNote(ms(500), ms(500), 64), 95));

        Performance perf = RecorderApp.buildPerformance(captured);
        assertEquals(1, perf.score().tracks().size());
        var track = perf.score().tracks().get(0);
        assertEquals("vocal", track.id().name());
        assertEquals(music.notation.performance.TrackKind.PITCHED, track.kind());
        assertEquals(2, track.notes().size());
        assertEquals(60, ((PitchedNote) track.notes().get(0)).midi());
        assertEquals(64, ((PitchedNote) track.notes().get(1)).midi());
    }

    @Test
    void velocitiesSidecarMatchesCapturedValues() {
        var captured = List.of(
                new RecorderApp.CapturedNote(new PitchedNote(ms(0), ms(500), 60),  80),
                new RecorderApp.CapturedNote(new PitchedNote(ms(500), ms(500), 64), 110),
                new RecorderApp.CapturedNote(new PitchedNote(ms(1000), ms(500), 67),  60));

        Performance perf = RecorderApp.buildPerformance(captured);
        var vc = perf.velocities().byTrack().get(new TrackId("vocal"));
        assertNotNull(vc, "velocity control should be present for vocal track");
        assertEquals(80 / 127.0, vc.levelAt(ms(0)), 1e-9);
        assertEquals(110 / 127.0, vc.levelAt(ms(500)), 1e-9);
        assertEquals(60 / 127.0, vc.levelAt(ms(1000)), 1e-9);
    }

    @Test
    void emptyCaptureProducesEmptyTrack() {
        Performance perf = RecorderApp.buildPerformance(List.of());
        assertEquals(1, perf.score().tracks().size());
        assertTrue(perf.score().tracks().get(0).notes().isEmpty());
        assertTrue(perf.velocities().byTrack().isEmpty(),
                "no captured notes → no velocity entry written");
    }

    // ── MxlImport wrapping ───────────────────────────────────────────

    @Test
    void buildsMxlImportWithNeutralDefaults() {
        var captured = List.of(
                new RecorderApp.CapturedNote(new PitchedNote(ms(0), ms(500), 60), 80));

        MxlImport imp = RecorderApp.buildMxlImport("my-recording", captured);
        assertEquals("my-recording", imp.displayName());
        assertEquals(4, imp.timeSig().beats());
        assertEquals(4, imp.timeSig().beatValue());
        assertEquals(music.notation.pitch.NoteName.C, imp.key().tonic());
        assertEquals(music.notation.structure.Mode.MAJOR, imp.key().mode());
        assertEquals("", imp.sourceXml(),
                "no XML source for mic capture — sourceXml must be empty");
        assertTrue(imp.repeatStructure().isEmpty());
        assertTrue(imp.transpositions().isEmpty());
    }

    // ── Folder round-trip via the same reader NotationApp uses ───────

    @Test
    void folderRoundTrips_singleNote(@TempDir Path tmp) throws Exception {
        var captured = List.of(
                new RecorderApp.CapturedNote(new PitchedNote(ms(0), ms(500), 60), 80));

        MxlImport written = RecorderApp.buildMxlImport("single", captured);
        File pieceDir = tmp.resolve("single").toFile();
        MxlSplitJsonWriter.write(written, pieceDir.toPath());

        // Standard folder layout — the same files NotationApp expects.
        assertTrue(new File(pieceDir, "meta.json").isFile(),         "meta.json must exist");
        assertTrue(new File(pieceDir, "tempo.json").isFile(),        "tempo.json must exist");
        assertTrue(new File(pieceDir, "velocity.json").isFile(),     "velocity.json must exist (we have velocities)");
        File[] trackFiles = pieceDir.listFiles((d, n) -> n.startsWith("track-") && n.endsWith(".json"));
        assertNotNull(trackFiles);
        assertEquals(1, trackFiles.length, "exactly one track file expected");

        MxlImport back = MxlSplitJsonReader.read(pieceDir.toPath());
        assertEquals("single", back.displayName());
        assertEquals(written.performance().score(), back.performance().score(),
                "score must round-trip via the standard JSON folder reader");
        assertEquals(written.performance().velocities(), back.performance().velocities(),
                "velocities sidecar must round-trip");
    }

    @Test
    void folderRoundTrips_multipleNotes(@TempDir Path tmp) throws Exception {
        var captured = List.of(
                new RecorderApp.CapturedNote(new PitchedNote(ms(0), ms(333), 60), 88),
                new RecorderApp.CapturedNote(new PitchedNote(ms(333), ms(333), 64), 88),
                new RecorderApp.CapturedNote(new PitchedNote(ms(666), ms(333), 67), 88));

        MxlImport written = RecorderApp.buildMxlImport("triad", captured);
        File pieceDir = tmp.resolve("triad").toFile();
        MxlSplitJsonWriter.write(written, pieceDir.toPath());

        MxlImport back = MxlSplitJsonReader.read(pieceDir.toPath());
        assertEquals(1, back.performance().score().tracks().size());
        var notes = back.performance().score().tracks().get(0).notes();
        assertEquals(3, notes.size());
        assertEquals(60, ((PitchedNote) notes.get(0)).midi());
        assertEquals(64, ((PitchedNote) notes.get(1)).midi());
        assertEquals(67, ((PitchedNote) notes.get(2)).midi());
    }

    @Test
    void folderRoundTrips_emptyCaptureWritesValidShell(@TempDir Path tmp) throws Exception {
        MxlImport written = RecorderApp.buildMxlImport("empty", List.of());
        File pieceDir = tmp.resolve("empty").toFile();
        MxlSplitJsonWriter.write(written, pieceDir.toPath());

        // No notes → no velocity sidecar.
        assertFalse(new File(pieceDir, "velocity.json").isFile(),
                "empty velocities must not be written");

        MxlImport back = MxlSplitJsonReader.read(pieceDir.toPath());
        assertEquals(1, back.performance().score().tracks().size());
        assertTrue(back.performance().score().tracks().get(0).notes().isEmpty());
    }
}
