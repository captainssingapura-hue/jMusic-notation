package music.notation.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * Composable top-of-app strip: transport buttons on the left, the
 * piece selector in the middle (grows), a settings (⚙) toggle on the
 * right.
 *
 * <p>Owns its widgets and exposes setters to install handlers; state
 * (play/pause glyph, disabled flags) is mutated by the host.</p>
 */
final class TransportBar {

    private final HBox root;
    private final Button playButton;
    private final Button stopButton;
    private final Button exportButton;
    private final Button pieceButton;
    private final Button settingsButton;

    TransportBar() {
        playButton    = iconButton("▶", "Play (Space)");
        stopButton    = iconButton("⏹", "Stop");
        exportButton  = iconButton("⬇", "Export MIDI…");
        settingsButton = iconButton("⚙", "Controls");
        playButton.setDisable(true);
        stopButton.setDisable(true);
        exportButton.setDisable(true);

        pieceButton = new Button("▾  Choose a piece…");
        pieceButton.setStyle("-fx-background-color: #313244; -fx-text-fill: #cdd6f4;"
                + " -fx-border-color: #45475a; -fx-border-radius: 4; -fx-background-radius: 4;"
                + " -fx-padding: 4 10 4 10;");
        pieceButton.setMaxWidth(Double.MAX_VALUE);
        pieceButton.setAlignment(Pos.CENTER_LEFT);

        Region leftDivider = verticalDivider();
        Region rightDivider = verticalDivider();

        root = new HBox(8,
                playButton, stopButton,
                leftDivider, pieceButton, rightDivider,
                exportButton, settingsButton);
        HBox.setHgrow(pieceButton, Priority.ALWAYS);
        root.setAlignment(Pos.CENTER_LEFT);
        root.setPadding(new Insets(4, 8, 4, 8));
        root.setStyle("-fx-background-color: #181825; "
                + "-fx-border-color: transparent transparent #313244 transparent; -fx-border-width: 1;");
    }

    Node getRoot() { return root; }

    Button playButton()     { return playButton; }
    Button stopButton()     { return stopButton; }
    Button exportButton()   { return exportButton; }
    Button pieceButton()    { return pieceButton; }
    Button settingsButton() { return settingsButton; }

    /** Update the piece-button label (called when a piece is loaded). */
    void setPieceLabel(String text) {
        pieceButton.setText(text);
    }

    /** Toggle whether transport-affecting widgets are interactive. */
    void setPieceEnabled(boolean enabled) {
        pieceButton.setDisable(!enabled);
    }

    void setPlayGlyph(String glyph) { playButton.setText(glyph); }

    // ── Widgets ─────────────────────────────────────────────────────

    private static Button iconButton(String icon, String tooltipText) {
        Button b = new Button(icon);
        final double SIZE = 32;
        b.setMinSize(SIZE, SIZE);
        b.setPrefSize(SIZE, SIZE);
        b.setMaxSize(SIZE, SIZE);
        final String base = "-fx-background-color: #45475a; -fx-text-fill: #cdd6f4; "
                + "-fx-font-size: 14; -fx-padding: 0; "
                + "-fx-background-radius: 16; -fx-min-width: 32; -fx-alignment: center;";
        final String hover = "-fx-background-color: #585b70; -fx-text-fill: #cdd6f4; "
                + "-fx-font-size: 14; -fx-padding: 0; "
                + "-fx-background-radius: 16; -fx-min-width: 32; -fx-alignment: center;";
        b.setStyle(base);
        b.setOnMouseEntered(e -> b.setStyle(hover));
        b.setOnMouseExited(e -> b.setStyle(base));
        Tooltip tip = new Tooltip(tooltipText);
        tip.setShowDelay(javafx.util.Duration.millis(300));
        tip.setStyle("-fx-font-size: 12; -fx-background-color: #313244; -fx-text-fill: #cdd6f4;");
        Tooltip.install(b, tip);
        return b;
    }

    private static Region verticalDivider() {
        Region r = new Region();
        r.setMinWidth(1);
        r.setPrefWidth(1);
        r.setMaxWidth(1);
        r.setStyle("-fx-background-color: #313244;");
        r.setPrefHeight(24);
        return r;
    }
}
