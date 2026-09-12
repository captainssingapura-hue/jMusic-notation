package music.notation.lyrics;

import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import music.notation.expressivity.LyricEvent;
import music.notation.expressivity.LyricLine;
import music.notation.expressivity.Lyrics;
import music.notation.expressivity.TrackId;
import music.notation.mxl.MxlImport;
import music.notation.performance.ConcreteNote;
import music.notation.performance.Performance;
import music.notation.performance.PitchedNote;
import music.notation.performance.Track;
import music.notation.phrase.Monophony;
import music.notation.phrase.Monophony.TopLine;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory state for one open lyrics-editing session. Lives entirely
 * on the FX Application Thread — all mutations and reads happen there.
 * Loading (off-thread) builds a fresh instance and {@code Platform.runLater}s
 * a swap into the app.
 *
 * <p>Shape:</p>
 * <ul>
 *   <li>{@link #source} — the {@link MxlImport} that the lyrics line
 *       belongs to (kept around so {@code Save} can write the full
 *       folder back).</li>
 *   <li>{@link #sourceDir} — the folder the import was loaded from
 *       (null for never-saved sessions).</li>
 *   <li>{@link #trackId} — which Track in the import we're editing
 *       lyrics for. Monophonic, validated at load time.</li>
 *   <li>{@link #cells} — one {@link Cell} per audible onset in the
 *       track, in tickMs order. The cell's {@code codePoint} is the
 *       authoritative content of the lyric line:
 *       <ul>
 *         <li>{@code -1} → no glyph (output: rest, no lyric event)</li>
 *         <li>{@code LyricEvent.CONTINUATION_CODE_POINT} ({@code '_'})
 *             → continuation marker</li>
 *         <li>anything else → that codepoint as the glyph</li>
 *       </ul></li>
 *   <li>{@link #currentNoteIndex} — shared playhead / selection cursor
 *       between the sentence view, the per-note grid, and the big
 *       "current syllable" header.</li>
 *   <li>{@link #version} — bumps on every cell edit; views observe it
 *       to know they need to redraw.</li>
 * </ul>
 */
public final class LyricsModel {

    /** One row of the editing grid. Mutable {@code codePoint} only — the rest is read-only metadata. */
    public static final class Cell {
        public final int noteIndex;
        public final long tickMs;
        public final long durationMs;
        public final int midi;          // -1 if drum / unknown
        private int codePoint;          // -1 = empty (rest)

        Cell(int noteIndex, long tickMs, long durationMs, int midi, int codePoint) {
            this.noteIndex = noteIndex;
            this.tickMs = tickMs;
            this.durationMs = durationMs;
            this.midi = midi;
            this.codePoint = codePoint;
        }

        public int codePoint() { return codePoint; }

        /** True when this cell is the {@code '_'} continuation marker. */
        public boolean isContinuation() {
            return codePoint == LyricEvent.CONTINUATION_CODE_POINT;
        }

        /** True when this cell has no glyph attribution (renders as a rest in the lyric line). */
        public boolean isEmpty() { return codePoint < 0; }

        /** The glyph as a {@link String} (handles non-BMP); empty string if {@link #isEmpty()}. */
        public String character() {
            if (codePoint < 0) return "";
            return new String(Character.toChars(codePoint));
        }
    }

    private final MxlImport source;
    private final Path sourceDir;
    private final TrackId trackId;
    private final ObservableList<Cell> cells;
    private final int droppedNoteCount;

    private final SimpleIntegerProperty currentNoteIndex = new SimpleIntegerProperty(0);
    private final ReadOnlyIntegerWrapper version = new ReadOnlyIntegerWrapper(0);

    /**
     * Build a model from an already-loaded import + a chosen monophonic
     * track. Off-thread builders (see {@link LyricsIO#openFolder}) do
     * the work and hand the resulting model back to the FX thread via
     * {@code Platform.runLater}.
     *
     * @throws IllegalArgumentException if the track is not monophonic
     *         (use {@link Monophony} upstream to gate the picker)
     */
    public LyricsModel(MxlImport source, Path sourceDir, TrackId trackId) {
        this.source = source;
        this.sourceDir = sourceDir;
        this.trackId = trackId;

        Performance perf = source.performance();
        Track track = perf.score().tracks().stream()
                .filter(t -> t.id().equals(trackId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "no track named " + trackId.name() + " in the import"));

        // Audible onsets only — rests / silences don't get a cell.
        List<PitchedNote> audible = new ArrayList<>();
        for (ConcreteNote n : track.notes()) {
            if (n instanceof PitchedNote pn) audible.add(pn);
        }
        // Top-line extraction — relaxes "strictly monophonic" to "the
        // melody is the highest sounding note at any instant." Catches
        // vocal-to-MIDI converters that emit overlapping double-trigger
        // notes (test1 from the user's MIDI_Import corpus is a real
        // example: one onset 1 ms after another, both clearly the same
        // sung pitch fluctuating across two MIDI bins). Also handles
        // notated chords on a single track — the lyrics attach to the
        // top-line, the inner-voice notes are dropped from the editor's
        // view but preserved in the underlying Performance.
        Monophony.TopLine<PitchedNote> top = Monophony.extractTopLine(
                audible,
                PitchedNote::midi,
                PitchedNote::tickMs,
                pn -> pn.tickMs() + pn.durationMs());
        List<PitchedNote> melody = top.melody();
        this.droppedNoteCount = top.dropped();

        // Seed cells from any existing lyric line for this track. The
        // existing line may be shorter or longer than the note list
        // (older edits, or melody changed since); we match by tickMs.
        LyricLine existing = perf.lyrics().byTrack().getOrDefault(trackId, LyricLine.empty());
        Map<Long, Integer> codePointByTick = new LinkedHashMap<>();
        for (LyricEvent ev : existing.events()) {
            codePointByTick.put(ev.tickMs(), ev.codePoint());
        }

        List<Cell> built = new ArrayList<>(melody.size());
        for (int i = 0; i < melody.size(); i++) {
            PitchedNote pn = melody.get(i);
            int cp = codePointByTick.getOrDefault(pn.tickMs(), -1);
            built.add(new Cell(i, pn.tickMs(), pn.durationMs(), pn.midi(), cp));
        }
        this.cells = FXCollections.observableArrayList(built);
    }

    /**
     * Number of source notes that were dropped during top-line extraction
     * (chord-group bottoms + inner-voice onsets that landed under a
     * still-sounding higher note). Used by the editor to surface a
     * status hint like "12 inner-voice notes hidden".
     */
    public int droppedNoteCount() { return droppedNoteCount; }

    // ── Read accessors ───────────────────────────────────────────────────

    public MxlImport source()       { return source; }
    public Path sourceDir()         { return sourceDir; }
    public TrackId trackId()        { return trackId; }
    public ObservableList<Cell> cells() { return FXCollections.unmodifiableObservableList(cells); }
    public int cellCount()          { return cells.size(); }
    public Cell cellAt(int i)       { return cells.get(i); }

    public SimpleIntegerProperty currentNoteIndexProperty() { return currentNoteIndex; }
    public int currentNoteIndex()   { return currentNoteIndex.get(); }
    public void setCurrentNoteIndex(int i) {
        currentNoteIndex.set(Math.max(0, Math.min(cells.size() - 1, i)));
    }

    public ReadOnlyIntegerProperty versionProperty() { return version.getReadOnlyProperty(); }

    // ── Mutators — FX thread only ────────────────────────────────────────

    /** Set cell {@code i}'s glyph to the given code point, or to "empty" if {@code codePoint < 0}. */
    public void setCellCodePoint(int i, int codePoint) {
        if (i < 0 || i >= cells.size()) return;
        Cell cur = cells.get(i);
        int normalized = codePoint < 0 ? -1 : codePoint;
        if (cur.codePoint == normalized) return;     // no-op → skip the version bump
        cur.codePoint = normalized;
        // Force ObservableList listeners to fire by replacing the element
        // with itself — JavaFX has no built-in "element mutated" signal,
        // and we want grid + chip views to redraw together.
        cells.set(i, cur);
        version.set(version.get() + 1);
    }

    /** Convenience: clear a cell to empty (rest). */
    public void clearCell(int i) {
        setCellCodePoint(i, -1);
    }

    /** Convenience: mark a cell as the {@code '_'} continuation marker. */
    public void markContinuation(int i) {
        setCellCodePoint(i, LyricEvent.CONTINUATION_CODE_POINT);
    }

    // ── Export ───────────────────────────────────────────────────────────

    /**
     * Render the current cell array as a {@link LyricLine}. Cells with
     * {@code codePoint < 0} (empty / rest) emit no event; everything
     * else emits a {@link LyricEvent} at the cell's tickMs.
     */
    public LyricLine toLyricLine() {
        List<LyricEvent> events = new ArrayList<>();
        for (Cell c : cells) {
            if (c.codePoint < 0) continue;
            events.add(new LyricEvent(c.tickMs, c.codePoint));
        }
        return new LyricLine(events);
    }

    /**
     * Build the {@link MxlImport} we'd hand to
     * {@link music.notation.mxl.MxlSplitJsonWriter}: original source +
     * {@link Performance#withLyrics(Lyrics)} carrying this track's edits
     * folded into the existing lyrics side-channel.
     */
    public MxlImport toSavableImport() {
        LyricLine newLine = toLyricLine();
        Map<TrackId, LyricLine> byTrack = new LinkedHashMap<>(source.performance().lyrics().byTrack());
        if (newLine.events().isEmpty()) {
            byTrack.remove(trackId);                 // dropping the track if no glyphs
        } else {
            byTrack.put(trackId, newLine);
        }
        Lyrics updated = new Lyrics(byTrack);
        Performance newPerf = source.performance().withLyrics(updated);

        return new MxlImport(
                source.displayName(),
                newPerf,
                source.timeSig(),
                source.key(),
                source.sourceXml(),
                source.repeatStructure(),
                source.transpositions());
    }
}
