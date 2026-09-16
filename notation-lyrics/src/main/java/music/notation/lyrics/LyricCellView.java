package music.notation.lyrics;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import music.notation.expressivity.LyricEvent;

/**
 * One cell in the per-note editing grid. Four rows top-to-bottom:
 * pitch label, duration glyph, single-codepoint text input, continuation
 * toggle. Read-only metadata is plain {@link Label}s; the input is a
 * {@link TextField} capped at one Unicode code point; the continuation
 * is a {@link ToggleButton}.
 *
 * <p>Every interactive callback marshals back to the parent through the
 * three lambdas in the constructor — no direct reference to the model.
 * That keeps the cell reusable and easy to test.</p>
 *
 * <p>FX-thread only: all interactions originate from JavaFX event
 * handlers, which run on the FX Application Thread by definition.</p>
 */
public final class LyricCellView extends VBox {

    /** Cell width in pixels. Matches the chip widths in the sentence view. */
    public static final double WIDTH = 44;
    /** Cell height — gives the four stacked rows comfortable breathing room. */
    public static final double HEIGHT = 88;

    private static final String CSS_BASE =
            "-fx-background-color: #fffef9;"
            + "-fx-border-color: #d4cfc0;"
            + "-fx-border-width: 1;"
            + "-fx-border-radius: 3;"
            + "-fx-background-radius: 3;";

    private static final String CSS_FOCUSED =
            "-fx-background-color: #fff8e1;"
            + "-fx-border-color: #d49b00;"
            + "-fx-border-width: 1.5;"
            + "-fx-border-radius: 3;"
            + "-fx-background-radius: 3;";

    private static final String CSS_ACTIVE_PLAYHEAD =
            "-fx-background-color: #e3f2fd;"
            + "-fx-border-color: #1976d2;"
            + "-fx-border-width: 1.5;"
            + "-fx-border-radius: 3;"
            + "-fx-background-radius: 3;";

    private final int noteIndex;
    private final Label pitchLabel;
    private final Label durationLabel;
    private final TextField input;
    private final ToggleButton continuationToggle;

    private boolean updating;     // suppress event handlers during model→view sync

    public interface Callbacks {
        /** User typed or pasted a single code point into the input. {@code -1} = cleared. */
        void onGlyphChanged(int noteIndex, int codePoint);

        /** Continuation toggle flipped. */
        void onContinuationToggled(int noteIndex, boolean nowContinuation);

        /** Cell gained focus (used to drive the shared currentNoteIndex). */
        void onFocused(int noteIndex);

        /** User pressed Tab / arrow key wanting to move to {@code targetIndex} (clamped externally). */
        void onNavigate(int targetIndex);
    }

    public LyricCellView(int noteIndex, String pitchText, String durationGlyph,
                          Callbacks cb) {
        this.noteIndex = noteIndex;

        pitchLabel = new Label(pitchText);
        pitchLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #555;");

        durationLabel = new Label(durationGlyph);
        durationLabel.setStyle("-fx-font-size: 14; -fx-text-fill: #333;");

        input = new TextField();
        input.setPrefWidth(WIDTH - 8);
        input.setMaxWidth(WIDTH - 8);
        input.setAlignment(Pos.CENTER);
        input.setStyle("-fx-font-size: 16; -fx-padding: 2 4 2 4;");
        input.focusedProperty().addListener((obs, was, now) -> {
            setStyle(now ? CSS_FOCUSED : CSS_BASE);
            if (now) cb.onFocused(noteIndex);
        });
        input.textProperty().addListener((obs, was, now) -> {
            if (updating) return;
            int codePoint = parseSingleCodePoint(now);
            // Force the displayed text to be the canonical single-glyph form
            // (truncates pasted multi-char input).
            String canonical = codePoint < 0 ? "" : new String(Character.toChars(codePoint));
            if (!canonical.equals(now)) {
                updating = true;
                input.setText(canonical);
                input.positionCaret(canonical.length());
                updating = false;
            }
            cb.onGlyphChanged(noteIndex, codePoint);
        });
        input.setOnKeyPressed(ev -> {
            switch (ev.getCode()) {
                case TAB -> {
                    cb.onNavigate(ev.isShiftDown() ? noteIndex - 1 : noteIndex + 1);
                    ev.consume();
                }
                case RIGHT -> { if (atCaretEnd()) { cb.onNavigate(noteIndex + 1); ev.consume(); } }
                case LEFT  -> { if (input.getCaretPosition() == 0) { cb.onNavigate(noteIndex - 1); ev.consume(); } }
                case ENTER -> { cb.onNavigate(noteIndex + 1); ev.consume(); }
                case ESCAPE -> input.getParent().requestFocus();
                default -> { /* let it through */ }
            }
        });

        continuationToggle = new ToggleButton("_");
        continuationToggle.setPrefSize(WIDTH - 8, 18);
        continuationToggle.setStyle("-fx-font-size: 10; -fx-padding: 0;");
        continuationToggle.setFocusTraversable(false);
        continuationToggle.setOnAction(ev -> {
            if (updating) return;
            cb.onContinuationToggled(noteIndex, continuationToggle.isSelected());
        });

        VBox.setMargin(pitchLabel, new Insets(2, 0, 0, 0));
        getChildren().addAll(pitchLabel, durationLabel, input, continuationToggle);
        setAlignment(Pos.TOP_CENTER);
        setPrefSize(WIDTH, HEIGHT);
        setSpacing(2);
        setPadding(new Insets(2));
        setStyle(CSS_BASE);
    }

    /** Push model state into the widget without firing change events. */
    public void renderFromModel(int codePoint, boolean isPlayhead) {
        updating = true;
        String text = codePoint < 0
                ? ""
                : new String(Character.toChars(codePoint));
        input.setText(text);
        boolean isContinuation = codePoint == LyricEvent.CONTINUATION_CODE_POINT;
        continuationToggle.setSelected(isContinuation);
        if (isContinuation) {
            input.setStyle("-fx-font-size: 16; -fx-padding: 2 4 2 4; -fx-text-fill: #999;");
        } else {
            input.setStyle("-fx-font-size: 16; -fx-padding: 2 4 2 4; -fx-text-fill: #222;");
        }
        if (isPlayhead && !input.isFocused()) {
            setStyle(CSS_ACTIVE_PLAYHEAD);
        } else if (!input.isFocused()) {
            setStyle(CSS_BASE);
        }
        updating = false;
    }

    /** Move focus into this cell's input field — used by keyboard navigation. */
    public void focusInput() {
        input.requestFocus();
        input.selectAll();
    }

    public int noteIndex() { return noteIndex; }

    private boolean atCaretEnd() {
        return input.getCaretPosition() == input.getText().length();
    }

    /**
     * Parse user input into a single Unicode code point, or {@code -1}
     * for empty / whitespace. Multi-character paste is truncated to the
     * first code point so the cell-as-one-glyph invariant holds.
     */
    private static int parseSingleCodePoint(String text) {
        if (text == null || text.isEmpty()) return -1;
        // Strip leading whitespace — defensive against IME oddities.
        int idx = 0;
        while (idx < text.length() && Character.isWhitespace(text.charAt(idx))) idx++;
        if (idx >= text.length()) return -1;
        int cp = text.codePointAt(idx);
        // Validate.
        return Character.isValidCodePoint(cp) ? cp : -1;
    }
}
