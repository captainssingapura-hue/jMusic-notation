package music.notation.mxl;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One-shot helper: extract the Bach Air sample (outside the repo, on the
 * user's local drive) into {@code target/inspection/} so its decompressed
 * MusicXML can be read with the {@code Read} tool. Skipped silently when
 * the source file isn't present (CI / other machines).
 */
class BachAirInspectTest {

    private static final Path SOURCE = Paths.get(
            "R:/Music_works/MusicXml/Bach/Air/J._S._Bach_-_Air_on_the_G_String_Piano_arrangement (1).mxl");

    @Test
    void extractToTargetIfPresent() throws IOException {
        if (!Files.exists(SOURCE)) return;
        Path target = Paths.get("target");
        if (!Files.isDirectory(target)) return;
        Path inspection = Files.createDirectories(target.resolve("inspection-bach"));

        MxlProject project = MxlProject.at(inspection);
        Path xml = project.extractXml(SOURCE);
        assertTrue(Files.exists(xml));
    }
}
