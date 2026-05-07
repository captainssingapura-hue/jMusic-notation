package music.notation.mxl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MxlProjectTest {

    private static final String FIXTURE = "/Chopin_Nocturne_Op9_No1.mxl";

    private static Path stageFixture(Path dir) throws IOException {
        Path target = dir.resolve("Chopin_Nocturne_Op9_No1.mxl");
        try (InputStream in = MxlProjectTest.class.getResourceAsStream(FIXTURE)) {
            assertNotNull(in, "test fixture missing on classpath: " + FIXTURE);
            Files.write(target, in.readAllBytes());
        }
        return target;
    }

    @Test
    void inferFromUsesParentAsProjectFolder(@TempDir Path tmp) throws IOException {
        Path mxl = stageFixture(tmp);
        MxlProject project = MxlProject.inferFrom(mxl);

        assertEquals(tmp.toAbsolutePath(), project.projectFolder());
        assertEquals(tmp.resolve("xml").toAbsolutePath(), project.xmlDir());
    }

    @Test
    void extractXmlWritesSidecarWithMxlPrefix(@TempDir Path tmp) throws IOException {
        Path mxl = stageFixture(tmp);
        MxlProject project = MxlProject.inferFrom(mxl);

        Path written = project.extractXml(mxl);

        assertTrue(Files.exists(written), "extracted XML must exist: " + written);
        assertEquals("MXL_Chopin_Nocturne_Op9_No1.xml",
                written.getFileName().toString());
        String xml = Files.readString(written);
        assertTrue(xml.startsWith("<?xml"), "extracted file should be an XML document");
        assertTrue(xml.contains("score-partwise") || xml.contains("score-timewise"),
                "extracted file should be a MusicXML score");
    }

    @Test
    void extractXmlCopiesSourceWhenOutsideProject(@TempDir Path source, @TempDir Path project)
            throws IOException {
        Path mxl = stageFixture(source);
        MxlProject p = MxlProject.at(project);

        p.extractXml(mxl);

        Path mirrored = project.resolve("mxl").resolve("Chopin_Nocturne_Op9_No1.mxl");
        assertTrue(Files.exists(mirrored),
                "external source should be copied into <project>/mxl/: " + mirrored);
    }

    /**
     * One-shot extraction into the module's {@code target/inspection/} directory
     * so the full decompressed XML is on disk for human/tooling inspection. Not
     * a behavioural assertion — its job is to land the file alongside the build
     * output. Skipped silently if {@code target/} doesn't exist (e.g. running
     * from an IDE that uses a different output folder).
     */
    @Test
    void writeChopinFullImportToTargetForInspection() throws IOException {
        Path target = Paths.get("target");
        if (!Files.isDirectory(target)) return;
        Path inspection = Files.createDirectories(target.resolve("inspection"));

        Path mxl = inspection.resolve("Chopin_Nocturne_Op9_No1.mxl");
        try (InputStream in = MxlProjectTest.class.getResourceAsStream(FIXTURE)) {
            assertNotNull(in);
            Files.write(mxl, in.readAllBytes());
        }

        MxlProject project = MxlProject.at(inspection);
        project.importMxl(mxl);

        assertTrue(Files.exists(project.xmlDir().resolve("MXL_Chopin_Nocturne_Op9_No1.xml")));
        Path pieceDir = project.jsonDir().resolve("MXL_Chopin_Nocturne_Op9_No1");
        assertTrue(Files.isDirectory(pieceDir));
        assertTrue(Files.exists(pieceDir.resolve("meta.json")));
        assertTrue(Files.exists(pieceDir.resolve("tempo.json")));
    }

    @Test
    void importMxlWritesSplitJsonLayout(@TempDir Path tmp) throws IOException {
        Path mxl = stageFixture(tmp);
        MxlProject project = MxlProject.inferFrom(mxl);

        MxlImport result = project.importMxl(mxl);

        Path xml = project.xmlDir().resolve("MXL_Chopin_Nocturne_Op9_No1.xml");
        Path pieceDir = project.jsonDir().resolve("MXL_Chopin_Nocturne_Op9_No1");
        assertTrue(Files.exists(xml), "xml sidecar should exist: " + xml);
        assertTrue(Files.isDirectory(pieceDir), "json piece dir should exist: " + pieceDir);
        assertTrue(Files.exists(pieceDir.resolve("meta.json")),  "meta.json missing");
        assertTrue(Files.exists(pieceDir.resolve("tempo.json")), "tempo.json missing");

        try (var stream = Files.list(pieceDir)) {
            long trackFiles = stream
                    .filter(p -> p.getFileName().toString().startsWith("track-"))
                    .count();
            assertEquals(result.performance().score().tracks().size(), trackFiles,
                    "one track-*.json per Track");
        }

        int totalNotes = result.performance().score().tracks().stream()
                .mapToInt(t -> t.notes().size())
                .sum();
        assertTrue(totalNotes > 1000,
                "Chopin Nocturne should yield > 1000 concrete notes, got " + totalNotes);
    }
}
