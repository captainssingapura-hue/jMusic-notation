package music.notation.mxl;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.function.Consumer;

/**
 * On-disk cache for parsed MusicXML imports. Keyed by source filename
 * (one canonical cache dir per file); invalidated by SHA-256 of the
 * source bytes.
 *
 * <h2>Layout</h2>
 *
 * <pre>
 *   ~/.notation-cache/mxl/&lt;sanitised-filename&gt;/
 *       source.sha256          last-parsed source hash
 *       schema-version.txt     bumps when JSON shape changes
 *       parser-version.txt     bumps when MusicXmlParser behaviour changes
 *       cached-at.txt          ISO timestamp of cache write
 *       meta.json
 *       tempo.json
 *       track-NN-….json
 *       (and any other split-JSON sidecars)
 * </pre>
 *
 * <h2>Lookup logic</h2>
 *
 * <ol>
 *   <li>Compute SHA-256 of the source file's bytes.</li>
 *   <li>If the cache dir for this filename exists and its
 *       {@code source.sha256} matches AND its schema/parser versions
 *       match the current — load via {@link MxlSplitJsonReader}.</li>
 *   <li>Otherwise parse via {@link MxlReader}, write the result
 *       atomically to the cache dir (temp + rename), and return.</li>
 * </ol>
 *
 * <h2>Why name-keyed (not hash-keyed)</h2>
 *
 * <p>Browsable: {@code ls ~/.notation-cache/mxl/} shows recognisable
 * filenames. Single canonical cache dir per file (edits overwrite,
 * not accumulate). Trade-off: file renames orphan the old cache dir;
 * basename collisions clobber each other (re-load triggers re-parse).
 * Acceptable for V1.</p>
 */
public final class MxlImportCache {

    /** Bumps when the on-disk JSON shape changes. Mismatch invalidates cache. */
    public static final String SCHEMA_VERSION = "1";

    /** Bumps when the parser's behaviour changes. Mismatch invalidates cache. */
    public static final String PARSER_VERSION = "2";

    /** Stages reported via the optional progress callback. */
    public enum Stage {
        HASHING("Hashing source…"),
        CACHE_HIT_LOADING("Loading from cache…"),
        PARSING("Parsing MusicXML…"),
        CACHING("Writing cache…"),
        DONE("Done");

        public final String label;
        Stage(String label) { this.label = label; }
    }

    private MxlImportCache() {}

    /**
     * Cache-aware load. Returns the MxlImport, using the cache when
     * available; otherwise parses fresh and writes to cache.
     *
     * @param source MXL file to load
     * @param onStage optional callback for progress reporting; may be null
     */
    public static MxlImport lookupOrParse(Path source, Consumer<Stage> onStage) {
        report(onStage, Stage.HASHING);
        String hash = sha256(source);
        Path cacheDir = cacheDirFor(source);

        if (isCacheValid(cacheDir, hash)) {
            report(onStage, Stage.CACHE_HIT_LOADING);
            try {
                MxlImport imp = MxlSplitJsonReader.read(cacheDir);
                report(onStage, Stage.DONE);
                return imp;
            } catch (Exception ex) {
                // Cache read failed (corrupted, partial write). Fall
                // through to re-parse rather than fail the load.
            }
        }

        report(onStage, Stage.PARSING);
        MxlImport imp = MxlReader.read(source);

        report(onStage, Stage.CACHING);
        writeCacheAtomic(cacheDir, imp, hash);

        report(onStage, Stage.DONE);
        return imp;
    }

    /** Convenience: synchronous cache-aware load with no progress callback. */
    public static MxlImport lookupOrParse(Path source) {
        return lookupOrParse(source, null);
    }

    /**
     * Resolve the cache dir for {@code source}. Public so callers can
     * inspect / clear specific entries.
     */
    public static Path cacheDirFor(Path source) {
        String name = source.getFileName().toString();
        return cacheRoot().resolve("mxl").resolve(slug(name));
    }

    /** Root of the cache. Read-only configuration. */
    public static Path cacheRoot() {
        return Paths.get(System.getProperty("user.home"), ".notation-cache");
    }

    /**
     * Remove a single piece from the cache (e.g. user-triggered "clear
     * cache for this file"). Silently no-op if the cache dir doesn't
     * exist.
     */
    public static void evict(Path source) {
        Path cacheDir = cacheDirFor(source);
        if (!Files.exists(cacheDir)) return;
        try (var stream = Files.walk(cacheDir)) {
            stream.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
        } catch (IOException e) {
            throw new UncheckedIOException("failed to evict " + cacheDir, e);
        }
    }

    // ── Internals ──────────────────────────────────────────────────────────

    private static boolean isCacheValid(Path cacheDir, String expectedHash) {
        if (!Files.isDirectory(cacheDir)) return false;
        return readTextOrNull(cacheDir.resolve("source.sha256")).equals(expectedHash)
                && readTextOrNull(cacheDir.resolve("schema-version.txt")).equals(SCHEMA_VERSION)
                && readTextOrNull(cacheDir.resolve("parser-version.txt")).equals(PARSER_VERSION);
    }

    private static String readTextOrNull(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * Write the cache atomically: build under a sibling temp dir,
     * then rename to the final cache dir. Failed writes leave nothing
     * behind.
     */
    private static void writeCacheAtomic(Path finalDir, MxlImport imp, String hash) {
        Path parent = finalDir.getParent();
        try {
            Files.createDirectories(parent);
            Path tempDir = Files.createTempDirectory(parent, finalDir.getFileName() + ".tmp.");

            try {
                MxlSplitJsonWriter.write(imp, tempDir);
                Files.writeString(tempDir.resolve("source.sha256"), hash, StandardCharsets.UTF_8);
                Files.writeString(tempDir.resolve("schema-version.txt"), SCHEMA_VERSION, StandardCharsets.UTF_8);
                Files.writeString(tempDir.resolve("parser-version.txt"), PARSER_VERSION, StandardCharsets.UTF_8);
                Files.writeString(tempDir.resolve("cached-at.txt"),
                        Instant.now().toString(), StandardCharsets.UTF_8);

                // Replace any prior cache dir atomically. ATOMIC_MOVE
                // isn't supported across filesystems on all platforms,
                // and Files.move with REPLACE_EXISTING for non-empty
                // directories isn't atomic either. Best-effort: delete
                // old, then move new. A crash between steps leaves no
                // cache for this piece (next load re-parses).
                if (Files.exists(finalDir)) {
                    try (var stream = Files.walk(finalDir)) {
                        stream.sorted(java.util.Comparator.reverseOrder())
                                .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
                    }
                }
                Files.move(tempDir, finalDir, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception writeFailed) {
                // Clean up temp on failure.
                try (var stream = Files.walk(tempDir)) {
                    stream.sorted(java.util.Comparator.reverseOrder())
                            .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
                } catch (IOException ignored) {}
                if (writeFailed instanceof RuntimeException re) throw re;
                throw new UncheckedIOException("failed to write cache " + finalDir,
                        writeFailed instanceof IOException io ? io : new IOException(writeFailed));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to prepare cache dir " + parent, e);
        }
    }

    private static String sha256(Path file) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = Files.readAllBytes(file);
            byte[] digest = md.digest(bytes);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read " + file, e);
        }
    }

    /**
     * Filesystem-safe slug: replaces path separators and reserved
     * characters with underscores. Preserves Unicode (Chinese
     * filenames work).
     */
    private static String slug(String raw) {
        return raw.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static void report(Consumer<Stage> onStage, Stage stage) {
        if (onStage != null) onStage.accept(stage);
    }
}
