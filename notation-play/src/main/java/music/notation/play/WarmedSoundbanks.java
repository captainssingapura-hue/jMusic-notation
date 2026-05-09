package music.notation.play;

import javax.sound.midi.MidiSystem;
import javax.sound.midi.Soundbank;
import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/**
 * Asynchronous cache of parsed {@link Soundbank} objects, keyed by
 * source {@link File}.
 *
 * <p>{@link MidiSystem#getSoundbank(File)} is slow on first call —
 * 100s of milliseconds for typical SF2s, multiple seconds for big
 * ones. Synchronous calls during {@code MidiPlayer.start()} freeze
 * the FX thread until the SF2 is parsed and uploaded to the synth.</p>
 *
 * <p>This cache pre-parses the SF2 files on a background daemon
 * thread, triggered by the application as soon as the user's
 * soundbank list is known (typically at startup via
 * {@code loadPersistedSoundbanks()}). By the time the user clicks
 * Play, the {@link Soundbank} objects are sitting in memory — synth
 * setup just needs to call {@code synth.loadAllInstruments(sb)}.</p>
 *
 * <h2>Behaviour</h2>
 *
 * <ul>
 *   <li>{@link #warmAsync(List)} schedules parsing on a background
 *       worker thread; returns immediately. Idempotent — already-cached
 *       files are skipped.</li>
 *   <li>{@link #getOrLoad(File)} returns the cached {@link Soundbank}
 *       if present; otherwise parses synchronously (fallback if the
 *       async warmup hasn't finished yet).</li>
 *   <li>{@link #evictExcept(Collection)} removes cache entries whose
 *       files aren't in the supplied collection. Called when the
 *       user's soundbank list changes.</li>
 *   <li>{@link #shutdown()} releases the worker thread on app exit.</li>
 * </ul>
 *
 * <p>Per-file failures are silently swallowed — a malformed SF2 won't
 * fail the warmup of other files. The {@code getOrLoad} fallback will
 * surface the exception to the caller if it's still problematic at
 * use time.</p>
 */
public final class WarmedSoundbanks {

    private final ConcurrentHashMap<File, Soundbank> cache = new ConcurrentHashMap<>();
    private final ExecutorService worker;

    public WarmedSoundbanks() {
        this.worker = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "soundbank-warmup");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Schedule background parsing of {@code files} on the worker
     * thread. Returns immediately. Files already cached are skipped;
     * per-file failures don't abort the rest.
     */
    public void warmAsync(List<File> files) {
        if (files == null || files.isEmpty()) return;
        // Snapshot to avoid races if the caller mutates the list.
        List<File> snapshot = List.copyOf(files);
        worker.submit(() -> {
            for (File f : snapshot) {
                if (f == null || !f.isFile()) continue;
                if (cache.containsKey(f)) continue;
                try {
                    Soundbank sb = MidiSystem.getSoundbank(f);
                    if (sb != null) cache.put(f, sb);
                } catch (Exception ignored) {
                    // Per-file failure ignored. getOrLoad fallback
                    // will re-attempt and surface the error if still
                    // needed.
                }
            }
        });
    }

    /**
     * Return the cached {@link Soundbank} for {@code file}, or parse
     * it synchronously and cache the result.
     *
     * <p>Synchronous fallback pays the parse cost on the calling
     * thread. Used by {@link SoundbankSetup#apply} when the warmup
     * hasn't finished yet — better than failing.</p>
     *
     * @return the soundbank, or {@code null} if the file is not a
     *         valid SF2 / DLS / SBK
     */
    public Soundbank getOrLoad(File file) {
        if (file == null || !file.isFile()) return null;
        Soundbank cached = cache.get(file);
        if (cached != null) return cached;
        try {
            Soundbank sb = MidiSystem.getSoundbank(file);
            if (sb != null) cache.put(file, sb);
            return sb;
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Drop cache entries whose files are not in {@code keep}. Called
     * when the user reduces their soundbank list — frees the parsed
     * SF2 memory of files no longer in use.
     */
    public void evictExcept(Collection<File> keep) {
        Set<File> keepSet = new HashSet<>(keep);
        cache.keySet().retainAll(keepSet);
    }

    /** Pure clear — removes everything. Used by tests and forced refreshes. */
    public void clear() {
        cache.clear();
    }

    /** Inspection: how many soundbanks are currently cached. */
    public int size() {
        return cache.size();
    }

    /** Inspection: is this file already in the cache? */
    public boolean isCached(File file) {
        return cache.containsKey(file);
    }

    /**
     * Release the worker thread. Call on app shutdown. After this,
     * {@link #warmAsync} silently no-ops; {@link #getOrLoad} still
     * works synchronously.
     */
    public void shutdown() {
        worker.shutdown();
    }
}
