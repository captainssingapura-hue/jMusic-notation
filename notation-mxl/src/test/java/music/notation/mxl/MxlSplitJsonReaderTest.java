package music.notation.mxl;

import music.notation.performance.MidiCodec;
import music.notation.performance.PerformanceJson;
import music.notation.structure.Piece;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MxlSplitJsonReaderTest {

    private static final String FIXTURE = "/Chopin_Nocturne_Op9_No1.mxl";

    private static Path stageFixture(Path dir) throws IOException {
        Path target = dir.resolve("Chopin_Nocturne_Op9_No1.mxl");
        try (InputStream in = MxlSplitJsonReaderTest.class.getResourceAsStream(FIXTURE)) {
            assertNotNull(in);
            Files.write(target, in.readAllBytes());
        }
        return target;
    }

    @Test
    void writeThenReadRoundTripsPerformance(@TempDir Path tmp) throws IOException {
        Path mxl = stageFixture(tmp);
        MxlProject project = MxlProject.inferFrom(mxl);
        MxlImport written = project.importMxl(mxl);

        Path pieceDir = project.jsonDir().resolve("MXL_Chopin_Nocturne_Op9_No1");
        MxlImport reloaded = MxlSplitJsonReader.read(pieceDir);

        assertEquals(written.displayName(),  reloaded.displayName());
        assertEquals(written.timeSig(),      reloaded.timeSig());
        assertEquals(written.key(),          reloaded.key());
        // sourceXml is intentionally not preserved in the split layout.
        assertEquals("", reloaded.sourceXml());

        // Round-trip contract: structural equality of Performance.
        // Map iteration order is intentionally unspecified (Map.copyOf inside
        // the side-channels), so we rely on record equals() — which compares
        // maps by entries rather than by iteration order.
        assertEquals(written.performance(), reloaded.performance(),
                "Performance round-trip should be lossless via split JSON");
    }

    @Test
    void reloadedJsonProducesPlayableMidiSequence(@TempDir Path tmp) throws Exception {
        Path mxl = stageFixture(tmp);
        MxlProject project = MxlProject.inferFrom(mxl);
        project.importMxl(mxl);

        MxlImport reloaded = MxlSplitJsonReader.read(
                project.jsonDir().resolve("MXL_Chopin_Nocturne_Op9_No1"));

        byte[] midi = MidiCodec.toMidi(reloaded.performance());
        Sequence sequence = MidiSystem.getSequence(new ByteArrayInputStream(midi));
        assertTrue(sequence.getTickLength() > 0,
                "JSON-reloaded Performance must produce a non-empty MIDI sequence");
        assertTrue(sequence.getMicrosecondLength() > 0);
    }

    @Test
    void reloadedImportConvertsToPieceForGuiPlayback(@TempDir Path tmp) throws IOException {
        Path mxl = stageFixture(tmp);
        MxlProject project = MxlProject.inferFrom(mxl);
        project.importMxl(mxl);

        MxlImport reloaded = MxlSplitJsonReader.read(
                project.jsonDir().resolve("MXL_Chopin_Nocturne_Op9_No1"));

        // GUI flow: NotationApp / PiecePickerDialog consume Piece, not Performance.
        // MxlImport.toPiece() should produce a usable Piece for the abstract layer.
        Piece piece = reloaded.toPiece();
        assertEquals(reloaded.displayName(), piece.title());
        assertEquals(reloaded.timeSig(),     piece.timeSig());
        assertEquals(reloaded.key(),         piece.key());
        assertTrue(piece.tracks().size() >= 1, "Piece should carry at least one track");
    }
}
