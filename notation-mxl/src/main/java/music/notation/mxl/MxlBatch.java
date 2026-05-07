package music.notation.mxl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Batch converter: scans a directory of {@code .mxl} files and runs each
 * through {@link MxlProject#importMxl(Path)}, producing the per-piece JSON
 * package layout under a single project folder.
 *
 * <h2>Usage</h2>
 * <pre>
 * mvn -pl notation-mxl exec:java -Dexec.mainClass=music.notation.mxl.MxlBatch \
 *     -Dexec.args="&lt;input-dir-or-mxl&gt; [&lt;project-folder&gt;]"
 * </pre>
 *
 * <p>If the first argument is a single {@code .mxl} file, that file is
 * processed alone. If it is a directory, the top-level {@code *.mxl}
 * files are processed (no recursion — by design; nested folders typically
 * indicate distinct collections that deserve their own project).</p>
 *
 * <p>The optional second argument names the project folder where the
 * {@code mxl/}, {@code xml/}, and {@code json/MXL_<base>/} subfolders
 * land. When omitted, the input directory itself becomes the project
 * folder, or — for a single-file input — the file's parent directory.</p>
 *
 * <p>Per-piece progress and a final summary are emitted via SLF4J at INFO.
 * Failures don't abort the batch — each file is logged at ERROR and the
 * tool continues; the exit code is non-zero when any file failed.</p>
 */
public final class MxlBatch {

    private static final Logger log = LoggerFactory.getLogger(MxlBatch.class);

    private MxlBatch() {}

    public static void main(String[] args) throws IOException {
        if (args.length < 1 || args.length > 2) {
            System.err.println("usage: MxlBatch <input-dir-or-mxl> [<project-folder>]");
            System.exit(2);
        }
        Path input = Path.of(args[0]);
        if (!Files.exists(input)) {
            log.error("Input not found: {}", input);
            System.exit(2);
        }

        List<Path> mxlFiles = collectMxlFiles(input);
        if (mxlFiles.isEmpty()) {
            log.warn("No .mxl files found at {}", input);
            return;
        }

        Path projectDir = (args.length == 2)
                ? Path.of(args[1])
                : (Files.isDirectory(input) ? input : input.toAbsolutePath().getParent());
        MxlProject project = MxlProject.at(projectDir);

        log.info("Project folder: {}", project.projectFolder());
        log.info("Found {} .mxl file(s) to process", mxlFiles.size());

        int ok = 0, failed = 0;
        long startedAt = System.currentTimeMillis();
        for (int i = 0; i < mxlFiles.size(); i++) {
            Path mxl = mxlFiles.get(i);
            String label = mxl.getFileName().toString();
            log.info("[{}/{}] {}", i + 1, mxlFiles.size(), label);
            try {
                MxlImport imp = project.importMxl(mxl);
                int notes = imp.performance().score().tracks().stream()
                        .mapToInt(t -> t.notes().size()).sum();
                log.info("        OK · {} tracks · {} notes · {} · {}/{} · {} bpm{}",
                        imp.performance().score().tracks().size(),
                        notes,
                        KeyDisplay.format(imp.key()),
                        imp.timeSig().beats(),
                        imp.timeSig().beatValue(),
                        imp.initialBpm(),
                        imp.repeatStructure().isEmpty() ? "" : " · with repeats");
                ok++;
            } catch (Exception ex) {
                log.error("        FAILED: {}", ex.getMessage(), ex);
                failed++;
            }
        }

        long elapsedMs = System.currentTimeMillis() - startedAt;
        log.info("Done in {} ms — {} OK, {} failed", elapsedMs, ok, failed);
        if (failed > 0) System.exit(1);
    }

    private static List<Path> collectMxlFiles(Path input) throws IOException {
        if (Files.isRegularFile(input)) {
            return isMxl(input) ? List.of(input) : List.of();
        }
        if (!Files.isDirectory(input)) return List.of();
        try (Stream<Path> stream = Files.list(input)) {
            List<Path> out = new ArrayList<>();
            stream.filter(MxlBatch::isMxl).forEach(out::add);
            Collections.sort(out);
            return out;
        }
    }

    private static boolean isMxl(Path p) {
        return Files.isRegularFile(p)
                && p.getFileName().toString().toLowerCase().endsWith(".mxl");
    }
}
