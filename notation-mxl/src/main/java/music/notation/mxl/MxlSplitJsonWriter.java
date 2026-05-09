package music.notation.mxl;

import music.notation.performance.PerformanceJson;
import music.notation.performance.Track;
import music.notation.pitch.Accidental;
import music.notation.pitch.NoteName;
import music.notation.structure.KeySignature;
import music.notation.structure.Mode;
import music.notation.structure.TimeSignature;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes an {@link MxlImport} as a folder of small JSON files instead of one
 * large document. Layout under {@code <pieceDir>}:
 *
 * <pre>
 *   meta.json                   {displayName, timeSig, key}
 *   tempo.json                  TempoTrack
 *   track-01-&lt;slug&gt;.json   one file per Track, ordered by Score
 * </pre>
 *
 * <p>Side-channels ({@code instruments}, {@code volume}, {@code articulations})
 * are omitted while empty; future landings will write them as their own files
 * once populated.</p>
 */
final class MxlSplitJsonWriter {

    private MxlSplitJsonWriter() {}

    /** Write the import as split files under {@code pieceDir}. Creates the directory if needed. */
    static List<Path> write(MxlImport imp, Path pieceDir) {
        try {
            Files.createDirectories(pieceDir);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to create json piece dir: " + pieceDir, e);
        }

        // Sweep stale track-*.json files: numeric-prefix names depend on
        // the (possibly re-ordered) Score, so leftovers from a prior import
        // would mix with the new ones. Other sidecars have stable names
        // (meta / tempo / volume / articulations / repeats) and are
        // overwritten in place, except when their data became empty —
        // also remove them to keep the dir consistent.
        sweepStale(pieceDir, imp);

        List<Path> written = new ArrayList<>();
        written.add(writeJson(pieceDir.resolve("meta.json"),
                new Meta(imp.displayName(), TimeSig.of(imp.timeSig()), Key.of(imp.key()))));
        written.add(writeJson(pieceDir.resolve("tempo.json"),
                imp.performance().tempo()));

        var tracks = imp.performance().score().tracks();
        for (int i = 0; i < tracks.size(); i++) {
            Track t = tracks.get(i);
            String name = String.format("track-%02d-%s.json", i + 1, slug(t.id().name()));
            written.add(writeJson(pieceDir.resolve(name), t));
        }

        // Side-channels: emit only when populated. An empty file would carry no
        // information beyond an empty map and just clutter the listing.
        if (!imp.performance().instruments().byTrack().isEmpty()) {
            written.add(writeJson(pieceDir.resolve("instruments.json"),
                    imp.performance().instruments()));
        }
        if (!imp.performance().volume().byTrack().isEmpty()) {
            written.add(writeJson(pieceDir.resolve("volume.json"),
                    imp.performance().volume()));
        }
        if (!imp.performance().articulations().byTrack().isEmpty()) {
            written.add(writeJson(pieceDir.resolve("articulations.json"),
                    imp.performance().articulations()));
        }
        if (!imp.performance().pedaling().byTrack().isEmpty()) {
            written.add(writeJson(pieceDir.resolve("pedaling.json"),
                    imp.performance().pedaling()));
        }
        if (!imp.performance().velocities().byTrack().isEmpty()) {
            written.add(writeJson(pieceDir.resolve("velocity.json"),
                    imp.performance().velocities()));
        }
        if (!imp.repeatStructure().isEmpty()) {
            written.add(writeJson(pieceDir.resolve("repeats.json"),
                    imp.repeatStructure()));
        }
        if (!imp.transpositions().isEmpty()) {
            written.add(writeJson(pieceDir.resolve("transpositions.json"),
                    imp.transpositions()));
        }
        return written;
    }

    /**
     * Remove stale track-NN-*.json files (the numeric prefix shifts with
     * track ordering) and side-channel files whose payload is now empty
     * (so the dir doesn't accumulate orphaned sidecars from earlier imports).
     */
    private static void sweepStale(Path pieceDir, MxlImport imp) {
        try (var stream = Files.list(pieceDir)) {
            for (Path p : stream.toList()) {
                String name = p.getFileName().toString();
                if (name.startsWith("track-") && name.endsWith(".json")) {
                    Files.deleteIfExists(p);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to sweep stale files in " + pieceDir, e);
        }

        deleteIfEmpty(pieceDir.resolve("instruments.json"),
                imp.performance().instruments().byTrack().isEmpty());
        deleteIfEmpty(pieceDir.resolve("volume.json"),
                imp.performance().volume().byTrack().isEmpty());
        deleteIfEmpty(pieceDir.resolve("articulations.json"),
                imp.performance().articulations().byTrack().isEmpty());
        deleteIfEmpty(pieceDir.resolve("pedaling.json"),
                imp.performance().pedaling().byTrack().isEmpty());
        deleteIfEmpty(pieceDir.resolve("velocity.json"),
                imp.performance().velocities().byTrack().isEmpty());
        deleteIfEmpty(pieceDir.resolve("repeats.json"),
                imp.repeatStructure().isEmpty());
        deleteIfEmpty(pieceDir.resolve("transpositions.json"),
                imp.transpositions().isEmpty());
    }

    private static void deleteIfEmpty(Path file, boolean shouldDelete) {
        if (!shouldDelete) return;
        try { Files.deleteIfExists(file); }
        catch (IOException e) { throw new UncheckedIOException("failed to delete " + file, e); }
    }

    private static Path writeJson(Path file, Object payload) {
        try {
            Files.writeString(file, PerformanceJson.toJsonAny(payload), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write " + file, e);
        }
        return file;
    }

    /**
     * Filesystem-safe slug for track ids like {@code "P1 · staff 1 · v1"}. Folds
     * the unicode middle-dot, spaces, and any non-[A-Za-z0-9_-] runs to single
     * dashes; trims leading/trailing dashes.
     */
    private static String slug(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        boolean lastDash = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '_' || c == '-';
            if (ok) {
                sb.append(c);
                lastDash = false;
            } else if (!lastDash) {
                sb.append('-');
                lastDash = true;
            }
        }
        int start = 0, end = sb.length();
        while (start < end && sb.charAt(start) == '-') start++;
        while (end > start && sb.charAt(end - 1) == '-') end--;
        String s = sb.substring(start, end);
        return s.isEmpty() ? "track" : s;
    }

    // ── Serialisable shapes for meta.json ──────────────────────────────

    record Meta(String displayName, TimeSig timeSig, Key key) {}

    record TimeSig(int beats, int beatValue) {
        static TimeSig of(TimeSignature ts) { return new TimeSig(ts.beats(), ts.beatValue()); }
    }

    record Key(NoteName tonic, Accidental accidental, Mode mode) {
        static Key of(KeySignature k) { return new Key(k.tonic(), k.accidental(), k.mode()); }
    }
}
