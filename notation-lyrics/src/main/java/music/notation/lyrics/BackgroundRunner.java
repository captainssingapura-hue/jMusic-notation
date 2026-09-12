package music.notation.lyrics;

import javafx.application.Platform;
import javafx.concurrent.Task;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Tiny wrapper around a single-threaded daemon {@link ExecutorService} for
 * running off-the-FX-thread work in the lyrics editor. All UI mutations
 * happen on the FX Application Thread; everything that touches disk,
 * parses JSON, concretizes a {@link music.notation.structure.Piece Piece}
 * into a {@link music.notation.performance.Performance Performance}, or
 * writes the sidecar back out goes through this class.
 *
 * <p>Why a dedicated executor (rather than {@code new Thread(task).start()}
 * per call):</p>
 * <ul>
 *   <li>Daemon threads — closing the editor doesn't leave a worker
 *       blocking JVM shutdown.</li>
 *   <li>Serialised execution — a Save can't interleave with a Load and
 *       leave the model in a half-applied state. The single thread is
 *       the queue.</li>
 *   <li>Named threads — easy to spot in a thread dump
 *       ({@code lyrics-bg-1}, {@code lyrics-bg-2} …).</li>
 * </ul>
 *
 * <p>Cancellation is per-task via {@link Task#cancel()}; the executor
 * itself is shut down on app exit by {@link #shutdown()}.</p>
 */
public final class BackgroundRunner {

    private final ExecutorService executor;

    public BackgroundRunner() {
        AtomicInteger counter = new AtomicInteger(1);
        ThreadFactory daemon = r -> {
            Thread t = new Thread(r, "lyrics-bg-" + counter.getAndIncrement());
            t.setDaemon(true);
            return t;
        };
        // Single-threaded so concurrent saves/loads can't interleave on
        // the same source folder. If we ever want true parallelism for
        // multi-file batch ops, swap for a small pool — but the editor's
        // foreground edits are cheap, so it isn't needed.
        this.executor = Executors.newSingleThreadExecutor(daemon);
    }

    /**
     * Run {@code work} on the background thread. {@code onSuccess} runs
     * on the FX thread with the result; {@code onFailure} runs on the FX
     * thread with the exception (may be null to swallow). Returns the
     * task so callers can cancel it if needed.
     */
    public <T> Task<T> submit(Supplier<T> work,
                              Consumer<T> onSuccess,
                              Consumer<Throwable> onFailure) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() {
                return work.get();
            }
        };
        task.setOnSucceeded(e -> onSuccess.accept(task.getValue()));
        if (onFailure != null) {
            task.setOnFailed(e -> onFailure.accept(task.getException()));
        }
        executor.submit(task);
        return task;
    }

    /**
     * Convenience for fire-and-report-status work where the caller just
     * wants success / error logged to a status label. The label updater
     * runs on the FX thread.
     */
    public void submitWithStatus(Supplier<String> work,
                                  Consumer<String> statusUpdater) {
        submit(work,
                msg -> statusUpdater.accept(msg),
                err -> Platform.runLater(() -> statusUpdater.accept(
                        "Error: " + err.getMessage())));
    }

    /** Stop accepting new work; signal the worker thread to exit. */
    public void shutdown() {
        executor.shutdownNow();
    }
}
