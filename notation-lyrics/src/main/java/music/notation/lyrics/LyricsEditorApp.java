package music.notation.lyrics;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import music.notation.expressivity.TrackId;

import java.io.File;
import java.nio.file.Path;

/**
 * Lyrics editor — Phase 2 MVP.
 *
 * <p>Two synchronised views over a single {@link LyricsModel}:</p>
 * <ul>
 *   <li>{@link SentenceStripPane} — read-only chip strip with melisma
 *       underlines (the "sentence" view)</li>
 *   <li>{@link LyricGridPane} — per-note cell grid with full keyboard
 *       editing (the "precision" view)</li>
 * </ul>
 *
 * <p>All file I/O — open folder, save, save-as — runs off the FX thread
 * via {@link BackgroundRunner} / {@link LyricsIO}. Model mutations and
 * UI updates run on the FX thread.</p>
 */
public final class LyricsEditorApp extends Application {

    public static void main(String[] args) {
        launch(LyricsEditorApp.class, args);
    }

    private final BackgroundRunner runner = new BackgroundRunner();
    private final LyricsIO io = new LyricsIO(runner);

    private LyricsModel model;            // null until a piece is loaded
    private Stage stage;

    // UI nodes that need to be wired/rewired across loads.
    private BorderPane root;
    private Label statusLabel;
    private Label headerGlyph;
    private Label headerLocation;
    private Button saveButton;
    private Button saveAsButton;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        stage.setTitle("Lyrics Editor");
        stage.setOnCloseRequest(e -> runner.shutdown());

        root = new BorderPane();
        root.setTop(buildToolbar());
        root.setCenter(buildEmptyState());
        root.setBottom(buildStatusBar());

        Scene scene = new Scene(root, 980, 520);
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() {
        runner.shutdown();
    }

    // ── Toolbar ─────────────────────────────────────────────────────────

    private HBox buildToolbar() {
        Button open = new Button("Open folder…");
        open.setOnAction(e -> onOpen());

        saveButton = new Button("Save");
        saveButton.setOnAction(e -> onSave());
        saveButton.setDisable(true);

        saveAsButton = new Button("Save As…");
        saveAsButton.setOnAction(e -> onSaveAs());
        saveAsButton.setDisable(true);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label trackLabel = new Label();
        trackLabel.setStyle("-fx-text-fill: #555; -fx-font-size: 11;");
        trackLabel.textProperty().bind(
                javafx.beans.binding.Bindings.createStringBinding(
                        () -> (model == null) ? "(no piece loaded)"
                                : "Track: " + model.trackId().name(),
                        // No actual property to watch — this is a one-shot
                        // binding refreshed by setStage on each load via
                        // a manual call to update().
                        statusLabelProperty()));

        HBox bar = new HBox(8, open, new Separator(), saveButton, saveAsButton,
                            spacer, trackLabel);
        bar.setPadding(new Insets(8, 12, 8, 12));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setStyle("-fx-background-color: #f4f1e8; -fx-border-color: #d4cfc0; -fx-border-width: 0 0 1 0;");
        return bar;
    }

    /** Dummy property whose mere existence keeps the track-label binding refreshable. */
    private final javafx.beans.property.SimpleStringProperty statusProp =
            new javafx.beans.property.SimpleStringProperty("");
    private javafx.beans.property.SimpleStringProperty statusLabelProperty() { return statusProp; }

    // ── Empty / loaded states ───────────────────────────────────────────

    private VBox buildEmptyState() {
        Label icon = new Label("📝");
        icon.setFont(Font.font(48));
        Label msg = new Label("Open a piece folder to begin.");
        msg.setStyle("-fx-text-fill: #888; -fx-font-size: 14;");
        VBox box = new VBox(12, icon, msg);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(40));
        return box;
    }

    private BorderPane buildHeaderRow() {
        headerGlyph = new Label("—");
        headerGlyph.setFont(Font.font("System", FontWeight.BOLD, 32));
        headerGlyph.setStyle("-fx-padding: 0 12 0 0;");

        Label caption = new Label("Current syllable");
        caption.setStyle("-fx-text-fill: #666; -fx-font-size: 11;");

        VBox glyphBox = new VBox(2, caption, headerGlyph);
        glyphBox.setAlignment(Pos.CENTER_LEFT);

        headerLocation = new Label("—");
        headerLocation.setStyle("-fx-text-fill: #555; -fx-font-size: 12;");

        BorderPane h = new BorderPane();
        h.setLeft(glyphBox);
        h.setRight(headerLocation);
        BorderPane.setAlignment(headerLocation, Pos.CENTER_RIGHT);
        h.setPadding(new Insets(10, 16, 10, 16));
        h.setStyle("-fx-background-color: #fffef9; -fx-border-color: #d4cfc0; -fx-border-width: 0 0 1 0;");
        return h;
    }

    // ── Toolbar actions ─────────────────────────────────────────────────

    private void onOpen() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Open piece folder");
        File dir = chooser.showDialog(stage);
        if (dir == null) return;

        Path pieceDir = dir.toPath();
        setStatus("Loading " + pieceDir.getFileName() + "…");
        io.loadFolder(pieceDir,
                imp -> {
                    // Track picker is FX-thread cheap; no need to push it off-thread.
                    var pick = TrackPickerDialog.show(stage, imp);
                    if (pick.isEmpty()) {
                        setStatus("Open cancelled.");
                        return;
                    }
                    bindModel(new LyricsModel(imp, pieceDir, pick.get()));
                    String dropMsg = model.droppedNoteCount() == 0
                            ? ""
                            : " · " + model.droppedNoteCount() + " inner-voice notes hidden";
                    setStatus("Loaded · " + model.cellCount() + " notes on "
                            + model.trackId().name() + dropMsg);
                },
                err -> setStatus("Load failed: " + err));
    }

    private void onSave() {
        if (model == null) return;
        io.saveToSourceFolder(model,
                this::setStatus,
                err -> setStatus("Save failed: " + err));
    }

    private void onSaveAs() {
        if (model == null) return;
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Save in parent folder…");
        File dir = chooser.showDialog(stage);
        if (dir == null) return;
        io.saveToFolder(model, dir.toPath(),
                this::setStatus,
                err -> setStatus("Save As failed: " + err));
    }

    // ── Model swap (FX thread) ──────────────────────────────────────────

    private void bindModel(LyricsModel newModel) {
        this.model = newModel;
        saveButton.setDisable(false);
        saveAsButton.setDisable(false);

        VBox header = new VBox(buildHeaderRow(), new SentenceStripPane(newModel));
        LyricGridPane grid = new LyricGridPane(newModel);

        BorderPane center = new BorderPane();
        center.setTop(header);
        center.setCenter(grid);
        root.setCenter(center);

        // Listen for playhead changes to update the big header glyph.
        newModel.currentNoteIndexProperty().addListener((obs, was, now) -> updateHeader());
        newModel.versionProperty().addListener((obs, was, now) -> updateHeader());
        updateHeader();

        // Force the toolbar's track-label binding to recompute.
        statusProp.set(statusProp.get() + " ");
        statusProp.set(statusProp.get().trim());
    }

    private void updateHeader() {
        if (model == null) return;
        int i = model.currentNoteIndex();
        if (i < 0 || i >= model.cellCount()) return;
        LyricsModel.Cell c = model.cellAt(i);
        String glyph = c.isEmpty() ? "—" : c.character();
        headerGlyph.setText(glyph);
        headerLocation.setText("Note " + (i + 1) + " / " + model.cellCount());
    }

    // ── Status (FX thread; safe to call from anywhere via Platform.runLater) ──

    private void setStatus(String text) {
        if (Platform.isFxApplicationThread()) {
            statusLabel.setText(text);
        } else {
            Platform.runLater(() -> statusLabel.setText(text));
        }
    }

    private HBox buildStatusBar() {
        statusLabel = new Label("Ready.");
        statusLabel.setStyle("-fx-text-fill: #555; -fx-font-size: 11;");
        HBox bar = new HBox(statusLabel);
        bar.setPadding(new Insets(6, 12, 6, 12));
        bar.setStyle("-fx-background-color: #f4f1e8; -fx-border-color: #d4cfc0; -fx-border-width: 1 0 0 0;");
        return bar;
    }
}
