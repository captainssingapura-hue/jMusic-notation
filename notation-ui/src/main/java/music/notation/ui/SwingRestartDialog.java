package music.notation.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.Optional;

/**
 * Tiny confirmation modal used when swing changes during playback.
 * Three choices: restart from beginning, restart from current bar, or
 * cancel (revert the swing setting).
 */
final class SwingRestartDialog {

    enum Choice { FROM_START, FROM_BAR }

    private SwingRestartDialog() {}

    /** Returns empty on cancel/close. */
    static Optional<Choice> show(Window owner, String title) {
        Stage stage = new Stage();
        stage.setTitle(title);
        stage.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) stage.initOwner(owner);

        Label header = new Label("Apply swing — restart playback from where?");
        header.setStyle("-fx-text-fill: #cdd6f4; -fx-font-size: 13; -fx-font-weight: bold;");

        Label hint = new Label("Bar restart snaps the playhead to the current bar's start.");
        hint.setStyle("-fx-text-fill: #a6adc8; -fx-font-size: 11;");
        hint.setWrapText(true);

        Choice[] result = { null };

        Button fromBar   = primary("From current bar");
        Button fromStart = secondary("From beginning");
        Button cancel    = secondary("Cancel");
        fromBar.setOnAction(e   -> { result[0] = Choice.FROM_BAR;   stage.close(); });
        fromStart.setOnAction(e -> { result[0] = Choice.FROM_START; stage.close(); });
        cancel.setOnAction(e    -> stage.close());

        HBox buttons = new HBox(8, cancel, fromStart, fromBar);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPadding(new Insets(8, 0, 0, 0));

        VBox root = new VBox(8, header, hint, buttons);
        root.setPadding(new Insets(14));
        root.setStyle("-fx-background-color: #1e1e2e;");

        Scene scene = new Scene(root, 380, 140);
        stage.setScene(scene);
        stage.showAndWait();
        return Optional.ofNullable(result[0]);
    }

    private static Button primary(String text) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color: #89b4fa; -fx-text-fill: #1e1e2e; "
                + "-fx-font-weight: bold; -fx-padding: 6 14; -fx-background-radius: 4;");
        return b;
    }

    private static Button secondary(String text) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color: #45475a; -fx-text-fill: #cdd6f4; "
                + "-fx-padding: 6 14; -fx-background-radius: 4;");
        return b;
    }
}
