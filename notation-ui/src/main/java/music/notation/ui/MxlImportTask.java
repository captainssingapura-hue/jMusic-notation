package music.notation.ui;

import javafx.concurrent.Task;
import music.notation.mxl.MxlImport;
import music.notation.mxl.MxlImportCache;

import java.nio.file.Path;

/**
 * JavaFX {@link Task} wrapper around {@link MxlImportCache#lookupOrParse}.
 * Runs the parse / cache-load on a background thread, reporting stage
 * messages to the FX-thread via {@link Task#updateMessage(String)}.
 *
 * <p>Keeps the UI responsive on large MXL files (parse can take
 * multi-second on 150 kb+ inputs).</p>
 *
 * <p>Usage:</p>
 *
 * <pre>{@code
 * var task = new MxlImportTask(file.toPath());
 * var dialog = new ProgressDialog(stage, task);
 * task.setOnSucceeded(e -> {
 *     dialog.close();
 *     useImport(task.getValue());
 * });
 * task.setOnFailed(e -> {
 *     dialog.close();
 *     showError(task.getException());
 * });
 * new Thread(task, "mxl-import").start();
 * dialog.showAndWait();
 * }</pre>
 */
public final class MxlImportTask extends Task<MxlImport> {

    private final Path source;

    public MxlImportTask(Path source) {
        this.source = source;
        updateTitle("Loading " + source.getFileName());
        updateMessage("Preparing…");
    }

    @Override
    protected MxlImport call() {
        // Indeterminate progress bar — file-scale parsing is fast
        // enough that step-based progress isn't worth plumbing yet.
        updateProgress(-1, 1);
        return MxlImportCache.lookupOrParse(source, stage -> updateMessage(stage.label));
    }
}
