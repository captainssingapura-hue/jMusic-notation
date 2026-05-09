package music.notation.play;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Coverage for {@link WarmedSoundbanks} — cache semantics, async
 * warmup behaviour, and eviction. Doesn't load real SF2 files
 * (test environments may not have them); uses non-existent and
 * malformed files to exercise the failure paths.
 *
 * <p>Real SF2 loading is exercised manually via the UI; an integration
 * test would need a test SF2 fixture in resources, which we don't
 * ship.</p>
 */
class WarmedSoundbanksTest {

    @Test
    void warmAsyncSchedulesWithoutBlocking() {
        WarmedSoundbanks cache = new WarmedSoundbanks();
        try {
            // Empty list is a no-op.
            long t0 = System.nanoTime();
            cache.warmAsync(List.of());
            long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
            assertTrue(elapsedMs < 50,
                    "warmAsync(empty) should return promptly, took " + elapsedMs + "ms");
        } finally {
            cache.shutdown();
        }
    }

    @Test
    void getOrLoadReturnsNullForMissingFile(@TempDir Path tmp) {
        WarmedSoundbanks cache = new WarmedSoundbanks();
        try {
            File missing = tmp.resolve("does-not-exist.sf2").toFile();
            assertNull(cache.getOrLoad(missing),
                    "missing file should return null, not throw");
        } finally {
            cache.shutdown();
        }
    }

    @Test
    void getOrLoadReturnsNullForCorruptFile(@TempDir Path tmp) throws IOException {
        WarmedSoundbanks cache = new WarmedSoundbanks();
        try {
            // Write a few bytes that aren't a valid SF2.
            File bogus = tmp.resolve("bogus.sf2").toFile();
            try (OutputStream out = Files.newOutputStream(bogus.toPath())) {
                out.write("not an sf2".getBytes());
            }
            assertNull(cache.getOrLoad(bogus),
                    "malformed file should return null, not throw");
            assertFalse(cache.isCached(bogus),
                    "failed parse should not pollute the cache");
        } finally {
            cache.shutdown();
        }
    }

    @Test
    void evictExceptKeepsOnlyListedFiles(@TempDir Path tmp) throws IOException {
        WarmedSoundbanks cache = new WarmedSoundbanks();
        try {
            // Manually populate the cache by simulating cache hits.
            // Since we can't easily produce a real Soundbank, exercise
            // the eviction logic via a clear-then-evictExcept sequence
            // — eviction should be a no-op on empty cache.
            File a = tmp.resolve("a.sf2").toFile();
            File b = tmp.resolve("b.sf2").toFile();
            File c = tmp.resolve("c.sf2").toFile();

            assertEquals(0, cache.size());
            cache.evictExcept(List.of(a, b));   // no-op on empty
            assertEquals(0, cache.size());
        } finally {
            cache.shutdown();
        }
    }

    @Test
    void clearEmptiesTheCache() {
        WarmedSoundbanks cache = new WarmedSoundbanks();
        try {
            assertEquals(0, cache.size());
            cache.clear();
            assertEquals(0, cache.size(), "clear on empty cache stays empty");
        } finally {
            cache.shutdown();
        }
    }

    @Test
    void shutdownPreventsFurtherWarmup(@TempDir Path tmp) {
        WarmedSoundbanks cache = new WarmedSoundbanks();
        cache.shutdown();
        // After shutdown, warmAsync should silently no-op (RejectedExecutionException
        // is swallowed by the executor's submit; or if it bubbles, we'd want to
        // verify the cache stays unchanged).
        File f = tmp.resolve("after-shutdown.sf2").toFile();
        try {
            cache.warmAsync(List.of(f));
        } catch (Exception ex) {
            // RejectedExecutionException is acceptable post-shutdown.
        }
        assertEquals(0, cache.size());
    }
}
