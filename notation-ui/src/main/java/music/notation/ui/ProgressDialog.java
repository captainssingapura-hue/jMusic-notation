package music.notation.ui;

import javafx.beans.binding.Bindings;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Tiny modal dialog showing a {@link Task}'s message + progress bar.
 *
 * <p>Bound to the task's {@code messageProperty} and
 * {@code progressProperty} via JavaFX bindings — the framework
 * marshals updates onto the FX thread automatically when the task
 * calls {@code updateMessage(...)} / {@code updateProgress(...)}.</p>
 *
 * <p>Stage is undecorated and modal to its owner; closes when the
 * task succeeds or fails (caller wires the close via
 * {@code task.setOnSucceeded} / {@code setOnFailed}).</p>
 */
public final class ProgressDialog {

    private final Stage stage;

    public ProgressDialog(Stage owner, Task<?> task) {
        Label title = new Label();
        title.textProperty().bind(task.titleProperty());
        title.setStyle("-fx-text-fill: #cdd6f4; -fx-font-size: 13; -fx-font-weight: bold;");

        Label message = new Label();
        message.textProperty().bind(
                Bindings.when(task.messageProperty().isEmpty())
                        .then("Working…")
                        .otherwise(task.messageProperty()));
        message.setStyle("-fx-text-fill: #cdd6f4; -fx-font-size: 12;");

        ProgressBar bar = new ProgressBar();
        bar.progressProperty().bind(task.progressProperty());
        bar.setPrefWidth(280);

        VBox root = new VBox(8, title, message, bar);
        root.setPadding(new Insets(16));
        root.setAlignment(Pos.CENTER_LEFT);
        root.setStyle("-fx-background-color: #1e1e2e;");

        stage = new Stage(StageStyle.UTILITY);
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setResizable(false);
        stage.setScene(new Scene(root));
        stage.setTitle("Loading…");
    }

    public void show()       { stage.show(); }
    public void close()      { stage.close(); }
    public Stage stage()     { return stage; }
}
