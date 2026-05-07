package music.notation.mxl;

import music.notation.mxl.RepeatStructure.Direction;
import music.notation.mxl.RepeatStructure.JumpKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real-file regression for Landing 1e against the Bach Air sample on the
 * user's local drive. Skipped silently when the source is absent.
 *
 * <p>The score has two backward repeats and a 1st/2nd-ending pair — when
 * expansion is correct the playback note count roughly doubles (modulo
 * volta-1 vs volta-2 length difference).</p>
 */
class BachAirRepeatsTest {

    private static final Path SOURCE = Paths.get(
            "R:/Music_works/MusicXml/Bach/Air/J._S._Bach_-_Air_on_the_G_String_Piano_arrangement (1).mxl");

    private static boolean fixturePresent() { return Files.exists(SOURCE); }

    @Test
    void scheduleExpandsRepeatsAndCapturesStructure() throws IOException {
        if (!fixturePresent()) return;
        MxlImport imp = MxlReader.read(SOURCE);

        long noteCount = imp.performance().score().tracks().stream()
                .mapToLong(t -> t.notes().size()).sum();
        assertTrue(noteCount > 800,
                "expected expanded note count > 800 (Bach Air with repeats), got " + noteCount);

        var rs = imp.repeatStructure();
        assertFalse(rs.isEmpty());

        // Two backward repeats + one explicit forward (the inner section).
        long backward = rs.repeatBars().stream()
                .filter(b -> b.direction() == Direction.BACKWARD).count();
        long forward = rs.repeatBars().stream()
                .filter(b -> b.direction() == Direction.FORWARD).count();
        assertEquals(2, backward, "expected 2 backward repeats");
        assertEquals(1, forward,  "expected 1 forward repeat");

        // 1st + 2nd ending captured.
        assertEquals(2, rs.voltas().size(), "expected 2 voltas (1st + 2nd ending)");
        assertTrue(rs.voltas().stream().anyMatch(v -> v.numbers().contains(1)));
        assertTrue(rs.voltas().stream().anyMatch(v -> v.numbers().contains(2)));

        // No D.C./D.S./fine in this score.
        assertTrue(rs.jumps().stream().noneMatch(j ->
                j.kind() == JumpKind.DACAPO || j.kind() == JumpKind.DALSEGNO ||
                j.kind() == JumpKind.FINE));
    }

    @Test
    void splitJsonRoundTripsRepeatStructure(@TempDir Path tmp) throws IOException {
        if (!fixturePresent()) return;

        // Stage the .mxl into the temp project so the relative-path semantics work.
        Path stagedMxl = tmp.resolve("bach.mxl");
        Files.copy(SOURCE, stagedMxl);

        MxlProject project = MxlProject.inferFrom(stagedMxl);
        MxlImport written = project.importMxl(stagedMxl);

        Path pieceDir = project.jsonDir().resolve("MXL_bach");
        assertTrue(Files.exists(pieceDir.resolve("repeats.json")),
                "Bach Air should produce a repeats.json sidecar");

        MxlImport reloaded = MxlSplitJsonReader.read(pieceDir);
        assertEquals(written.repeatStructure(), reloaded.repeatStructure(),
                "RepeatStructure must round-trip through split JSON");
    }
}
