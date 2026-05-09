package music.notation.mxl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Coverage for {@link MxlImportCache} — cache hit / miss / invalidation
 * behaviour. Uses a real Bach Air MXL fixture (already shipped for
 * other tests) so the parse side is exercised end-to-end, not mocked.
 *
 * <p>Cache root is overridden via the {@code user.home} system
 * property for the duration of each test so tests don't pollute the
 * developer's actual cache.</p>
 */
class MxlImportCacheTest {

    private static final String FIXTURE = "/Chopin_Nocturne_Op9_No1.mxl";

    @Test
    void firstLoadParsesAndWritesCache(@TempDir Path tmp) throws Exception {
        runWithCacheRoot(tmp, () -> {
            Path mxl = copyFixtureTo(tmp, "test.mxl");
            List<MxlImportCache.Stage> stages = new ArrayList<>();

            MxlImport imp = MxlImportCache.lookupOrParse(mxl, stages::add);

            assertNotNull(imp);
            assertTrue(stages.contains(MxlImportCache.Stage.PARSING),
                    "first load should parse");
            assertTrue(stages.contains(MxlImportCache.Stage.CACHING),
                    "first load should write cache");

            Path cacheDir = MxlImportCache.cacheDirFor(mxl);
            assertTrue(Files.isDirectory(cacheDir), "cache dir should exist");
            assertTrue(Files.exists(cacheDir.resolve("source.sha256")));
            assertTrue(Files.exists(cacheDir.resolve("schema-version.txt")));
            assertTrue(Files.exists(cacheDir.resolve("parser-version.txt")));
            assertTrue(Files.exists(cacheDir.resolve("meta.json")));
        });
    }

    @Test
    void secondLoadHitsCacheNoParse(@TempDir Path tmp) throws Exception {
        runWithCacheRoot(tmp, () -> {
            Path mxl = copyFixtureTo(tmp, "test.mxl");
            MxlImportCache.lookupOrParse(mxl);   // populate cache

            List<MxlImportCache.Stage> stages = new ArrayList<>();
            MxlImport imp = MxlImportCache.lookupOrParse(mxl, stages::add);

            assertNotNull(imp);
            assertTrue(stages.contains(MxlImportCache.Stage.CACHE_HIT_LOADING),
                    "second load should report cache hit");
            assertFalse(stages.contains(MxlImportCache.Stage.PARSING),
                    "second load should NOT re-parse");
        });
    }

    @Test
    void editedSourceInvalidatesCache(@TempDir Path tmp) throws Exception {
        runWithCacheRoot(tmp, () -> {
            Path mxl = copyFixtureTo(tmp, "test.mxl");
            MxlImportCache.lookupOrParse(mxl);   // populate cache

            // Mutate the source file's bytes (simulate an edit). Even
            // a single trailing-byte change shifts the hash.
            byte[] orig = Files.readAllBytes(mxl);
            byte[] mutated = new byte[orig.length + 1];
            System.arraycopy(orig, 0, mutated, 0, orig.length);
            mutated[orig.length] = 0x20;
            Files.write(mxl, mutated);

            List<MxlImportCache.Stage> stages = new ArrayList<>();
            // Mutated MXL probably won't parse cleanly (zip corruption).
            // For this test we just verify the cache was bypassed; the
            // parse failure is a separate concern.
            try {
                MxlImportCache.lookupOrParse(mxl, stages::add);
            } catch (Exception ignored) {
                // Mutating an MXL zip can break parsing; that's fine.
            }

            assertTrue(stages.contains(MxlImportCache.Stage.PARSING),
                    "edit should invalidate cache and trigger re-parse");
        });
    }

    @Test
    void schemaVersionMismatchInvalidatesCache(@TempDir Path tmp) throws Exception {
        runWithCacheRoot(tmp, () -> {
            Path mxl = copyFixtureTo(tmp, "test.mxl");
            MxlImportCache.lookupOrParse(mxl);   // populate cache

            // Corrupt the schema-version file to simulate a future
            // model change rendering this cache entry obsolete.
            Path cacheDir = MxlImportCache.cacheDirFor(mxl);
            Files.writeString(cacheDir.resolve("schema-version.txt"),
                    "999", StandardCharsets.UTF_8);

            List<MxlImportCache.Stage> stages = new ArrayList<>();
            MxlImportCache.lookupOrParse(mxl, stages::add);

            assertTrue(stages.contains(MxlImportCache.Stage.PARSING),
                    "schema-version mismatch should trigger re-parse");
        });
    }

    @Test
    void evictRemovesCacheDir(@TempDir Path tmp) throws Exception {
        runWithCacheRoot(tmp, () -> {
            Path mxl = copyFixtureTo(tmp, "test.mxl");
            MxlImportCache.lookupOrParse(mxl);
            Path cacheDir = MxlImportCache.cacheDirFor(mxl);
            assertTrue(Files.isDirectory(cacheDir));

            MxlImportCache.evict(mxl);
            assertFalse(Files.exists(cacheDir),
                    "evict should remove the cache dir");
        });
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /** Run a block with `user.home` redirected to a temp dir so the cache lives there. */
    private static void runWithCacheRoot(Path tmp, ThrowingRunnable body) throws Exception {
        String origHome = System.getProperty("user.home");
        System.setProperty("user.home", tmp.toString());
        try {
            body.run();
        } finally {
            System.setProperty("user.home", origHome);
        }
    }

    private static Path copyFixtureTo(Path dir, String name) throws IOException {
        Path out = dir.resolve(name);
        try (InputStream in = MxlImportCacheTest.class.getResourceAsStream(FIXTURE)) {
            if (in == null) throw new IOException("fixture missing: " + FIXTURE);
            Files.copy(in, out);
        }
        return out;
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
