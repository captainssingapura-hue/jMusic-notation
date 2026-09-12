package music.notation.lyrics;

import javafx.geometry.Insets;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Horizontal strip of {@link LyricCellView}s — the precision editing tool.
 * Lives inside a horizontally-scrolling {@link ScrollPane}; cells are
 * created once at model-load time and then re-rendered on every
 * model-version change. The cell count is bounded by the song's note
 * count (typically a few hundred), so a flat HBox is fine; no virtualisation
 * needed for v1.
 *
 * <p>FX-thread only. Model edits originate here through
 * {@link LyricCellView.Callbacks}; the pane forwards them to the model,
 * then listens to {@link LyricsModel#versionProperty()} to redraw.</p>
 */
public final class LyricGridPane extends ScrollPane {

    private final LyricsModel model;
    private final HBox row = new HBox(4);
    private final List<LyricCellView> cellViews = new ArrayList<>();

    public LyricGridPane(LyricsModel model) {
        this.model = model;
        row.setPadding(new Insets(8));
        setContent(row);
        setFitToHeight(true);
        setHbarPolicy(ScrollBarPolicy.AS_NEEDED);
        setVbarPolicy(ScrollBarPolicy.NEVER);
        setPannable(true);
        setStyle("-fx-background-color: #fcfaf2;");

        rebuildCellViews();

        // Redraw on model version change — covers all in-place edits.
        model.versionProperty().addListener((obs, was, now) -> renderAll());
        // Re-style on playhead change.
        model.currentNoteIndexProperty().addListener((obs, was, now) -> {
            renderAll();
            scrollToIndex(now.intValue());
        });
    }

    private void rebuildCellViews() {
        cellViews.clear();
        row.getChildren().clear();
        LyricCellView.Callbacks cb = new LyricCellView.Callbacks() {
            @Override public void onGlyphChanged(int noteIndex, int codePoint) {
                model.setCellCodePoint(noteIndex, codePoint);
            }
            @Override public void onContinuationToggled(int noteIndex, boolean nowContinuation) {
                if (nowContinuation) {
                    model.markContinuation(noteIndex);
                } else {
                    model.clearCell(noteIndex);
                }
            }
            @Override public void onFocused(int noteIndex) {
                model.setCurrentNoteIndex(noteIndex);
            }
            @Override public void onNavigate(int targetIndex) {
                if (targetIndex < 0 || targetIndex >= cellViews.size()) return;
                model.setCurrentNoteIndex(targetIndex);
                cellViews.get(targetIndex).focusInput();
            }
        };
        for (int i = 0; i < model.cellCount(); i++) {
            LyricsModel.Cell c = model.cellAt(i);
            LyricCellView view = new LyricCellView(
                    i, midiToPitchName(c.midi), durationGlyph(c.durationMs), cb);
            cellViews.add(view);
            row.getChildren().add(view);
        }
        renderAll();
    }

    private void renderAll() {
        int playhead = model.currentNoteIndex();
        for (int i = 0; i < cellViews.size(); i++) {
            cellViews.get(i).renderFromModel(model.cellAt(i).codePoint(), i == playhead);
        }
    }

    private void scrollToIndex(int i) {
        if (i < 0 || cellViews.size() <= 1) return;
        double rowWidth = row.getBoundsInLocal().getWidth();
        double viewport = getViewportBounds().getWidth();
        if (rowWidth <= viewport) return;
        double x = i * (LyricCellView.WIDTH + 4);
        double frac = Math.max(0, Math.min(1, (x - viewport / 2) / (rowWidth - viewport)));
        setHvalue(frac);
    }

    /** Move keyboard focus into a cell by index — used from the toolbar / chip view. */
    public void focusCell(int index) {
        if (index < 0 || index >= cellViews.size()) return;
        cellViews.get(index).focusInput();
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    /** Concise pitch label, e.g. 60 → "C4". Returns "—" for invalid. */
    private static String midiToPitchName(int midi) {
        if (midi < 0 || midi > 127) return "—";
        String[] names = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
        int oct = (midi / 12) - 1;
        return names[midi % 12] + oct;
    }

    /** Map a duration in ms (at 120 bpm reference) to a note-value glyph. */
    private static String durationGlyph(long durationMs) {
        // 120 bpm → quarter = 500 ms. Pick the closest standard.
        // Sub-quarter gets the eighth/sixteenth glyph; longer gets half/whole.
        if (durationMs >= 1800) return "𝅝";       // whole
        if (durationMs >=  900) return "𝅗𝅥";       // half
        if (durationMs >=  450) return "♩";       // quarter
        if (durationMs >=  225) return "♪";       // eighth
        return "♬";                              // sixteenth-ish
    }
}
