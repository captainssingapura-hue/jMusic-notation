package music.notation.lyrics;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import music.notation.expressivity.LyricEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Sentence-view chip strip. Each chip = one syllable (a non-continuation
 * glyph) plus an underline whose width spans the following continuation
 * cells. Rests render as a dim "░" chip.
 *
 * <p>MVP scope: <b>read-only display</b>. Selection, drag-melisma,
 * Replace…, Edit-text mode all land in a follow-up — the chip
 * rendering proves the two-view layout and gives users the readable
 * sentence summary the per-note grid lacks.</p>
 *
 * <p>Re-renders on every model version change. Clicking a chip drives
 * {@link LyricsModel#setCurrentNoteIndex(int)} so the grid + header
 * follow.</p>
 */
public final class SentenceStripPane extends ScrollPane {

    private final LyricsModel model;
    private final HBox row = new HBox(4);

    /** Per-chip note-index range so click → playhead update knows where to land. */
    private final List<int[]> chipRanges = new ArrayList<>();

    public SentenceStripPane(LyricsModel model) {
        this.model = model;
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10, 12, 10, 12));
        setContent(row);
        setFitToHeight(true);
        setHbarPolicy(ScrollBarPolicy.AS_NEEDED);
        setVbarPolicy(ScrollBarPolicy.NEVER);
        setPannable(true);
        setStyle("-fx-background-color: #f7f4ea;");
        setPrefHeight(70);
        setMinHeight(70);

        rebuild();
        model.versionProperty().addListener((obs, was, now) -> rebuild());
        model.currentNoteIndexProperty().addListener((obs, was, now) -> {
            highlightActive(now.intValue());
            scrollToIndex(now.intValue());
        });
    }

    /** Re-render every chip from the current model state. */
    private void rebuild() {
        row.getChildren().clear();
        chipRanges.clear();

        // Walk cells, grouping continuation runs onto their preceding glyph.
        // Rests become standalone "░" chips. Lone continuations at the start
        // (no preceding glyph) also render as "░" — visually they're notes
        // with no lyric attribution.
        int n = model.cellCount();
        int i = 0;
        while (i < n) {
            LyricsModel.Cell c = model.cellAt(i);
            if (c.isEmpty()) {
                row.getChildren().add(rest(i));
                chipRanges.add(new int[] { i, i });
                i++;
                continue;
            }
            if (c.codePoint() == LyricEvent.CONTINUATION_CODE_POINT) {
                // Orphan continuation — treat as a rest visually.
                row.getChildren().add(rest(i));
                chipRanges.add(new int[] { i, i });
                i++;
                continue;
            }
            // Glyph chip with a melisma run of trailing '_' cells.
            int runStart = i;
            int melismaCount = 0;
            int j = i + 1;
            while (j < n
                    && !model.cellAt(j).isEmpty()
                    && model.cellAt(j).codePoint() == LyricEvent.CONTINUATION_CODE_POINT) {
                melismaCount++;
                j++;
            }
            row.getChildren().add(syllableChip(c.character(), runStart, j - 1, melismaCount));
            chipRanges.add(new int[] { runStart, j - 1 });
            i = j;
        }

        highlightActive(model.currentNoteIndex());
    }

    /** Re-style chips so the one containing {@code activeIndex} stands out. */
    private void highlightActive(int activeIndex) {
        for (int k = 0; k < chipRanges.size() && k < row.getChildren().size(); k++) {
            int[] range = chipRanges.get(k);
            boolean active = activeIndex >= range[0] && activeIndex <= range[1];
            var node = row.getChildren().get(k);
            if (node instanceof HBox h) {
                h.setStyle(active ? CSS_CHIP_ACTIVE : CSS_CHIP);
            }
        }
    }

    private void scrollToIndex(int activeIndex) {
        double rowWidth = row.getBoundsInLocal().getWidth();
        double viewport = getViewportBounds().getWidth();
        if (rowWidth <= viewport) return;
        // Find the chip containing the active index.
        for (int k = 0; k < chipRanges.size(); k++) {
            int[] range = chipRanges.get(k);
            if (activeIndex >= range[0] && activeIndex <= range[1]) {
                var node = row.getChildren().get(k);
                double x = node.getBoundsInParent().getMinX();
                double frac = Math.max(0, Math.min(1, (x - viewport / 2) / (rowWidth - viewport)));
                setHvalue(frac);
                return;
            }
        }
    }

    // ── Chip builders ───────────────────────────────────────────────────

    private static final String CSS_CHIP =
            "-fx-background-color: transparent;"
            + "-fx-padding: 4 6 4 6;"
            + "-fx-border-color: transparent;"
            + "-fx-background-radius: 4;";

    private static final String CSS_CHIP_ACTIVE =
            "-fx-background-color: #fff8e1;"
            + "-fx-padding: 4 6 4 6;"
            + "-fx-border-color: #d49b00;"
            + "-fx-border-width: 1;"
            + "-fx-border-radius: 4;"
            + "-fx-background-radius: 4;";

    private HBox syllableChip(String glyph, int firstIdx, int lastIdx, int melismaCount) {
        Label g = new Label(glyph);
        g.setFont(Font.font("System", FontWeight.NORMAL, 22));
        g.setTextFill(Color.web("#222"));

        // Underline whose width visually echoes the melisma length.
        // Width per continuation cell ≈ 0.4em; minimum is the glyph width.
        double extenderWidth = Math.max(0, melismaCount) * 10;
        Line extender = new Line(0, 0, extenderWidth, 0);
        extender.setStroke(Color.web("#888"));
        extender.setStrokeWidth(1.2);

        // Render extender to the right of the glyph as a single block so the
        // chip's clickable area covers both.
        HBox box = new HBox(2);
        box.setAlignment(Pos.CENTER);
        box.getChildren().add(g);
        if (melismaCount > 0) box.getChildren().add(extender);
        box.setStyle(CSS_CHIP);
        box.setOnMouseClicked(ev -> model.setCurrentNoteIndex(firstIdx));
        return box;
    }

    private HBox rest(int idx) {
        Label r = new Label("░");
        r.setFont(Font.font("System", FontPosture.ITALIC, 18));
        r.setTextFill(Color.web("#aaa"));
        HBox box = new HBox(r);
        box.setAlignment(Pos.CENTER);
        box.setStyle(CSS_CHIP);
        box.setOnMouseClicked(ev -> model.setCurrentNoteIndex(idx));
        return box;
    }
}
