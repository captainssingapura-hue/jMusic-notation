package music.notation.transcribe;

import music.notation.mxl.MxlSplitJsonWriter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Writes a run's outputs into a folder:
 * <ul>
 *   <li>{@code notes.txt} — the readable report</li>
 *   <li>{@code pitch-track.csv} — the detector's frames, for plotting</li>
 *   <li>{@code transcription.json} — request and notes</li>
 *   <li>{@code <title>/} — a split-JSON piece folder (open in the apps, play as MIDI)</li>
 * </ul>
 */
public final class TranscriptionWriter {

    private TranscriptionWriter() {}

    public static List<Path> write(Transcription t, Path outDir) {
        try {
            Files.createDirectories(outDir);
            var written = new ArrayList<Path>();
            written.add(Files.writeString(outDir.resolve("notes.txt"), TranscriptionReport.render(t), StandardCharsets.UTF_8));
            if (t.pitchTrack() != null)
                written.add(Files.writeString(outDir.resolve("pitch-track.csv"), t.pitchTrack().toCsv(), StandardCharsets.UTF_8));
            Path json = outDir.resolve("transcription.json");
            TranscriptionJson.write(json, t);
            written.add(json);
            Path piece = outDir.resolve(slug(t.title()));
            MxlSplitJsonWriter.write(TranscriptionPiece.toImport(t), piece);
            written.add(piece);
            return written;
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write to " + outDir + ": " + e.getMessage(), e);
        }
    }

    /** File-system-safe folder name; keeps letters of any script. */
    public static String slug(String title) {
        String s = title.strip().replaceAll("[\\\\/:*?\"<>|\\s]+", "-").replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "").toLowerCase(Locale.ROOT);
        return s.isEmpty() ? "transcription" : s;
    }
}
