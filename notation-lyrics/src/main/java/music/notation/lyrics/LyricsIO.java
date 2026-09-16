package music.notation.lyrics;

import javafx.concurrent.Task;
import music.notation.expressivity.TrackId;
import music.notation.mxl.MxlImport;
import music.notation.mxl.MxlSplitJsonReader;
import music.notation.mxl.MxlSplitJsonWriter;
import music.notation.performance.Track;
import music.notation.performance.TrackKind;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Off-thread file I/O for the lyrics editor.
 *
 * <p>Every public method on this class submits a {@link Task} to the
 * supplied {@link BackgroundRunner} and returns void; results / errors
 * land back on the FX thread via the provided callbacks. This is the
 * only place the editor touches disk — keeping it isolated makes the
 * threading discipline easy to enforce.</p>
 */
public final class LyricsIO {

    private final BackgroundRunner runner;

    public LyricsIO(BackgroundRunner runner) {
        this.runner = runner;
    }

    /**
     * Load a piece folder (the kind {@link MxlSplitJsonWriter} produces)
     * off-thread. On success delivers the loaded import + the picked
     * track id; the caller then builds a {@link LyricsModel}. On
     * failure invokes {@code onError} with the exception's message.
     *
     * <p>This method itself does not pick the track — the caller shows
     * a track-picker dialog after the load completes (since the track
     * list is part of the loaded import).</p>
     */
    public void loadFolder(Path pieceDir,
                            Consumer<MxlImport> onLoaded,
                            Consumer<String> onError) {
        runner.submit(
                () -> MxlSplitJsonReader.read(pieceDir),
                onLoaded,
                err -> onError.accept(messageOf(err)));
    }

    /**
     * Save the model's current state back to its source folder. Writes
     * (or removes) {@code lyrics.json} as part of the standard split
     * JSON sidecar set. Off-thread; reports a one-line success status
     * to the FX thread on completion.
     */
    public void saveToSourceFolder(LyricsModel model,
                                    Consumer<String> onStatus,
                                    Consumer<String> onError) {
        Path dir = model.sourceDir();
        if (dir == null) {
            onError.accept("No source folder — use Save As… for a never-saved session.");
            return;
        }
        MxlImport savable = model.toSavableImport();
        runner.submit(
                () -> MxlSplitJsonWriter.write(savable, dir),
                paths -> onStatus.accept("Saved · " + paths.size() + " files written to " + dir.getFileName()),
                err -> onError.accept(messageOf(err)));
    }

    /**
     * Save the model's current state to a new parent folder (Save As…).
     * Creates a sub-folder named after {@code model.source().displayName()}
     * inside {@code parentDir}.
     */
    public void saveToFolder(LyricsModel model,
                              Path parentDir,
                              Consumer<String> onStatus,
                              Consumer<String> onError) {
        MxlImport savable = model.toSavableImport();
        Path target = parentDir.resolve(safeName(savable.displayName()));
        runner.submit(
                () -> MxlSplitJsonWriter.write(savable, target),
                paths -> onStatus.accept("Saved · " + paths.size() + " files in " + target.getFileName()),
                err -> onError.accept(messageOf(err)));
    }

    /**
     * List the candidate track IDs from a loaded {@link MxlImport} — all
     * {@link TrackKind#PITCHED PITCHED} tracks, in score order.
     * Monophonicity check is performed at the audible-onset layer by
     * {@link LyricsModel}'s constructor (which is cheap enough to run
     * on the FX thread).
     */
    public static List<TrackId> pitchedTrackIds(MxlImport imp) {
        List<TrackId> out = new ArrayList<>();
        for (Track t : imp.performance().score().tracks()) {
            if (t.kind() == TrackKind.PITCHED) out.add(t.id());
        }
        return out;
    }

    /** Filesystem-safe slug for the saved folder name. */
    private static String safeName(String raw) {
        if (raw == null || raw.isBlank()) return "piece";
        return raw.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fff _-]", "").replace(' ', '_');
    }

    private static String messageOf(Throwable t) {
        String msg = t.getMessage();
        if (msg != null && !msg.isBlank()) return msg;
        return t.getClass().getSimpleName();
    }
}
