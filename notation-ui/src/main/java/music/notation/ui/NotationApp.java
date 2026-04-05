package music.notation.ui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import music.notation.event.Instrument;
import music.notation.play.MidiPlayer;
import music.notation.songs.PieceLibrary;
import music.notation.structure.Piece;
import music.notation.structure.Track;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class NotationApp extends Application {

    private final MidiPlayer player = new MidiPlayer();
    private Piece currentPiece;

    private ComboBox<String> pieceSelector;
    private VBox trackControlsBox;
    private Button playButton;
    private Button pauseButton;
    private Button stopButton;
    private Button exportButton;
    private Label statusLabel;
    private Label pieceInfoLabel;
    private PitchScroll pitchScroll;

    /** Per-track list of instrument combos (outer = track index, inner = instrument slots). */
    private final List<List<ComboBox<Instrument>>> trackInstrumentCombos = new ArrayList<>();

    @Override
    public void start(Stage stage) {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #1e1e2e;");

        // === Top toolbar: title + playback controls ===
        HBox toolbar = new HBox(16);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(10, 16, 10, 16));
        toolbar.setStyle("-fx-background-color: #181825;");

        Label title = new Label("Music Notation Player");
        title.setFont(Font.font("System", FontWeight.BOLD, 16));
        title.setStyle("-fx-text-fill: #cdd6f4;");

        playButton = styledButton("Play");
        pauseButton = styledButton("Pause");
        stopButton = styledButton("Stop");
        exportButton = styledButton("Export MIDI");
        playButton.setOnAction(e -> onPlay());
        pauseButton.setOnAction(e -> onPause());
        stopButton.setOnAction(e -> onStop());
        exportButton.setOnAction(e -> onExport(stage));
        playButton.setDisable(true);
        pauseButton.setDisable(true);
        stopButton.setDisable(true);
        exportButton.setDisable(true);

        statusLabel = new Label("Select a piece to begin");
        statusLabel.setStyle("-fx-text-fill: #a6adc8; -fx-font-size: 12;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        toolbar.getChildren().addAll(title, spacer, playButton, pauseButton, stopButton, exportButton, statusLabel);
        root.setTop(toolbar);

        // === 2x2 SplitPane workspace ===

        // -- Top-left: Library --
        VBox libraryContent = new VBox(10);
        libraryContent.setPadding(new Insets(10));
        libraryContent.setStyle("-fx-background-color: #1e1e2e;");

        HBox selectorRow = new HBox(10);
        selectorRow.setAlignment(Pos.CENTER_LEFT);
        Label selectLabel = styledLabel("Piece:");
        pieceSelector = new ComboBox<>(FXCollections.observableArrayList(PieceLibrary.titles()));
        pieceSelector.setStyle(comboStyle());
        styleComboBoxCells(pieceSelector);
        pieceSelector.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(pieceSelector, Priority.ALWAYS);
        pieceSelector.setOnAction(e -> onPieceSelected());
        selectorRow.getChildren().addAll(selectLabel, pieceSelector);

        pieceInfoLabel = new Label("");
        pieceInfoLabel.setStyle("-fx-text-fill: #a6adc8; -fx-font-size: 12;");
        pieceInfoLabel.setWrapText(true);

        libraryContent.getChildren().addAll(selectorRow, pieceInfoLabel);

        // -- Top-right: Tracks --
        trackControlsBox = new VBox(8);
        trackControlsBox.setPadding(new Insets(10));
        trackControlsBox.setStyle("-fx-background-color: #1e1e2e;");
        ScrollPane trackScroll = new ScrollPane(trackControlsBox);
        trackScroll.setFitToWidth(true);
        trackScroll.setStyle("-fx-background: #1e1e2e; -fx-background-color: #1e1e2e;");

        // -- Bottom-left: Piano Roll --
        final ScrollPane[] scrollPaneRef = {null}; // forward reference for the callback
        pitchScroll = new PitchScroll(
                tick -> player.setTickPosition(tick),
                cursorX -> {
                    // Auto-scroll: when cursor enters the rightmost 30% of the viewport,
                    // scroll to keep it at that boundary.
                    final ScrollPane sp = scrollPaneRef[0];
                    if (sp == null) return;
                    final double contentWidth = pitchScroll.getWidth();
                    final double vpWidth = sp.getViewportBounds().getWidth();
                    if (contentWidth <= vpWidth) return;

                    final double scrollable = contentWidth - vpWidth;
                    final double scrollOffset = sp.getHvalue() * scrollable;
                    final double cursorInViewport = cursorX - scrollOffset;
                    final double threshold = vpWidth * 0.7;

                    if (cursorInViewport > threshold) {
                        final double targetOffset = cursorX - threshold;
                        sp.setHvalue(Math.clamp(targetOffset / scrollable, 0, 1));
                    }
                }
        );
        final Pane canvasHolder = new Pane(pitchScroll);
        canvasHolder.setStyle("-fx-background-color: #1e1e2e;");
        final ScrollPane pianoRollScrollPane = new ScrollPane(canvasHolder);
        scrollPaneRef[0] = pianoRollScrollPane;
        pianoRollScrollPane.setFitToHeight(true);
        pianoRollScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        pianoRollScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        pianoRollScrollPane.setStyle("-fx-background: #1e1e2e; -fx-background-color: #1e1e2e;");

        pitchScroll.heightProperty().bind(canvasHolder.heightProperty());

        final Runnable updateCanvasWidth = () -> {
            final double vpw = pianoRollScrollPane.getViewportBounds().getWidth();
            final double effectiveWidth = Math.max(vpw, pitchScroll.getMinContentWidth());
            pitchScroll.setWidth(effectiveWidth);
            canvasHolder.setMinWidth(effectiveWidth);
            canvasHolder.setPrefWidth(effectiveWidth);
        };
        pianoRollScrollPane.viewportBoundsProperty().addListener((obs, o, n) -> updateCanvasWidth.run());

        // Zoom slider: controls minimum pixels per quarter note (1–20, default 2)
        final Label zoomLabel = styledLabel("Zoom:");
        final Slider zoomSlider = new Slider(2, 80, 4);
        zoomSlider.setMaxWidth(120);
        zoomSlider.valueProperty().addListener((obs, o, n) -> {
            pitchScroll.setMinQuarterPx(n.doubleValue());
            updateCanvasWidth.run();
        });

        final HBox zoomBar = new HBox(8, zoomLabel, zoomSlider);
        zoomBar.setAlignment(Pos.CENTER_RIGHT);
        zoomBar.setPadding(new Insets(2, 8, 2, 8));
        zoomBar.setStyle("-fx-background-color: #181825;");

        final VBox pianoRollBox = new VBox(pianoRollScrollPane, zoomBar);
        VBox.setVgrow(pianoRollScrollPane, Priority.ALWAYS);

        // -- Bottom-right: placeholder for future content --
        VBox infoContent = new VBox(10);
        infoContent.setPadding(new Insets(10));
        infoContent.setStyle("-fx-background-color: #1e1e2e;");

        Label placeholder = new Label("Ready");
        placeholder.setStyle("-fx-text-fill: #585b70; -fx-font-size: 12;");
        infoContent.getChildren().add(placeholder);

        // === Assemble tabs and split panes ===
        TabPane topLeftTabs = createTabPane(tab("Library", libraryContent));
        TabPane topRightTabs = createTabPane(tab("Tracks", trackScroll));
        TabPane bottomLeftTabs = createTabPane(tab("Piano Roll", pianoRollBox));
        TabPane bottomRightTabs = createTabPane(tab("Info", infoContent));

        SplitPane topSplit = new SplitPane(topLeftTabs, topRightTabs);
        topSplit.setDividerPositions(0.45);
        styleSplitPane(topSplit);

        SplitPane bottomSplit = new SplitPane(bottomLeftTabs, bottomRightTabs);
        bottomSplit.setDividerPositions(0.65);
        styleSplitPane(bottomSplit);

        SplitPane mainSplit = new SplitPane(topSplit, bottomSplit);
        mainSplit.setOrientation(Orientation.VERTICAL);
        mainSplit.setDividerPositions(0.35);
        styleSplitPane(mainSplit);

        root.setCenter(mainSplit);

        Scene scene = new Scene(root, 1100, 720);
        scene.getStylesheets().add(getClass().getResource("/dark-theme.css").toExternalForm());
        stage.setTitle("Music Notation Player");
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> {
            player.stop();
            Platform.exit();
        });
        stage.show();

        // Auto-select first piece
        if (!PieceLibrary.titles().isEmpty()) {
            pieceSelector.getSelectionModel().selectFirst();
            onPieceSelected();
        }
    }

    private void onPieceSelected() {
        String selected = pieceSelector.getValue();
        if (selected == null) return;

        onStop();

        currentPiece = PieceLibrary.get(selected);
        pieceInfoLabel.setText(String.format("%s by %s\n%s  |  %d/%d  |  %d BPM",
                currentPiece.title(), currentPiece.composer(),
                currentPiece.key().tonic() + " " + currentPiece.key().mode(),
                currentPiece.timeSig().beats(), currentPiece.timeSig().beatValue(),
                currentPiece.tempo().bpm()));

        buildTrackControls();
        pitchScroll.load(PitchScrollData.fromPiece(currentPiece));
        playButton.setDisable(false);
        exportButton.setDisable(false);
        statusLabel.setText("Ready");
    }

    // ── Track controls with dynamic instrument slots ──────────────────────

    private void buildTrackControls() {
        trackControlsBox.getChildren().clear();
        trackInstrumentCombos.clear();

        List<Track> tracks = currentPiece.tracks();
        for (int i = 0; i < tracks.size(); i++) {
            Track track = tracks.get(i);
            int trackIndex = i;

            // Per-track container: label + instrument rows + add button
            VBox trackBox = new VBox(4);
            trackBox.setPadding(new Insets(4, 0, 4, 0));

            // Track header row: name + "+" button
            HBox headerRow = new HBox(8);
            headerRow.setAlignment(Pos.CENTER_LEFT);
            Label trackLabel = styledLabel(track.name());
            trackLabel.setMinWidth(80);
            trackLabel.setStyle("-fx-text-fill: #cdd6f4; -fx-font-size: 13; -fx-font-weight: bold;");

            Button addBtn = smallButton("+");
            addBtn.setOnAction(e -> addInstrumentSlot(trackIndex, trackBox, track.defaultInstrument()));

            headerRow.getChildren().addAll(trackLabel, addBtn);
            trackBox.getChildren().add(headerRow);

            // Instrument combo list for this track
            var combos = new ArrayList<ComboBox<Instrument>>();
            trackInstrumentCombos.add(combos);

            // Start with one slot showing the default instrument
            addInstrumentRow(trackIndex, trackBox, combos, track.defaultInstrument(), false);

            // Separator
            var sep = new Separator();
            sep.setStyle("-fx-background-color: #313244;");

            trackControlsBox.getChildren().addAll(trackBox, sep);
        }
    }

    /** Add a new instrument slot to a track (called by "+" button). */
    private void addInstrumentSlot(int trackIndex, VBox trackBox, Instrument defaultValue) {
        var combos = trackInstrumentCombos.get(trackIndex);
        addInstrumentRow(trackIndex, trackBox, combos, defaultValue, true);
    }

    /** Create one instrument combo row inside a track box. */
    private void addInstrumentRow(int trackIndex, VBox trackBox,
                                  List<ComboBox<Instrument>> combos,
                                  Instrument value, boolean removable) {
        HBox row = new HBox(6);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(1, 0, 1, 16)); // indent under track name

        ComboBox<Instrument> combo = new ComboBox<>(
                FXCollections.observableArrayList(Instrument.values()));
        combo.setValue(value);
        combo.setStyle(comboStyle());
        styleComboBoxCells(combo);
        combo.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(combo, Priority.ALWAYS);

        combos.add(combo);
        row.getChildren().add(combo);

        if (removable) {
            Button removeBtn = smallButton("\u2212"); // minus sign
            removeBtn.setOnAction(e -> {
                combos.remove(combo);
                trackBox.getChildren().remove(row);
            });
            row.getChildren().add(removeBtn);
        }

        // Insert before the separator (which is in the parent, not trackBox)
        trackBox.getChildren().add(row);
    }

    // ── Playback ──────────────────────────────────────────────────────────

    private void onPlay() {
        if (currentPiece == null) return;

        var assignments = collectInstrumentAssignments();

        playButton.setDisable(true);
        pauseButton.setDisable(false);
        pauseButton.setText("Pause");
        stopButton.setDisable(false);
        pieceSelector.setDisable(true);
        statusLabel.setText("Playing...");
        pitchScroll.load(PitchScrollData.fromPiece(currentPiece));

        Thread playThread = new Thread(() -> {
            try {
                player.start(currentPiece, assignments);
                Platform.runLater(() -> pitchScroll.startAnimation(player::getTickPosition));
                while (player.isPlaying() || player.isPaused()) {
                    Thread.sleep(100);
                }
                Thread.sleep(300);
            } catch (Exception ex) {
                ex.printStackTrace();
            } finally {
                player.stop();
                Platform.runLater(() -> {
                    pitchScroll.stopAnimation();
                    playButton.setDisable(false);
                    pauseButton.setDisable(true);
                    pauseButton.setText("Pause");
                    stopButton.setDisable(true);
                    pieceSelector.setDisable(false);
                    statusLabel.setText("Finished");
                });
            }
        });
        playThread.setDaemon(true);
        playThread.start();
    }

    private void onPause() {
        if (player.isPaused()) {
            player.resume();
            pauseButton.setText("Pause");
            statusLabel.setText("Playing...");
            pitchScroll.startAnimation(player::getTickPosition);
        } else {
            player.pause();
            pauseButton.setText("Resume");
            statusLabel.setText("Paused");
            pitchScroll.stopAnimation();
        }
    }

    private void onStop() {
        pitchScroll.stopAnimation();
        player.stop();
        playButton.setDisable(currentPiece == null);
        pauseButton.setDisable(true);
        pauseButton.setText("Pause");
        stopButton.setDisable(true);
        pieceSelector.setDisable(false);
        statusLabel.setText("Stopped");
    }

    private void onExport(Stage stage) {
        if (currentPiece == null) return;

        // Suggest filename from piece title
        String safeName = currentPiece.title()
                .replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fff _-]", "")
                .replace(' ', '_');

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export MIDI");
        chooser.setInitialFileName(safeName + ".mid");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("MIDI Files", "*.mid", "*.midi"));
        File file = chooser.showSaveDialog(stage);
        if (file == null) return;

        // Collect instrument assignments from the UI (same logic as onPlay)
        var assignments = collectInstrumentAssignments();

        try {
            MidiPlayer.exportMidi(currentPiece, assignments, file);
            statusLabel.setText("Exported: " + file.getName());
        } catch (Exception ex) {
            statusLabel.setText("Export failed: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /** Collect the current instrument assignments from all track combos. */
    private List<List<Instrument>> collectInstrumentAssignments() {
        var assignments = new ArrayList<List<Instrument>>();
        for (int t = 0; t < trackInstrumentCombos.size(); t++) {
            var combos = trackInstrumentCombos.get(t);
            var instruments = new ArrayList<Instrument>();
            for (var combo : combos) {
                if (combo.getValue() != null) {
                    instruments.add(combo.getValue());
                }
            }
            if (instruments.isEmpty()) {
                instruments.add(currentPiece.tracks().get(t).defaultInstrument());
            }
            assignments.add(instruments);
        }
        return assignments;
    }

    // --- UI helpers ---

    private static Tab tab(String title, Node content) {
        Tab t = new Tab(title, content);
        t.setClosable(false);
        return t;
    }

    private static TabPane createTabPane(Tab... tabs) {
        TabPane tp = new TabPane(tabs);
        tp.setTabMinWidth(60);
        tp.setStyle(
                "-fx-background-color: #1e1e2e; " +
                "-fx-tab-min-height: 28;");
        tp.getStyleClass().add("dark-tabs");
        return tp;
    }

    private static void styleSplitPane(SplitPane sp) {
        sp.setStyle("-fx-background-color: #1e1e2e; -fx-padding: 0;");
    }

    private static Label styledLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: #cdd6f4; -fx-font-size: 13;");
        return l;
    }

    private static Button styledButton(String text) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color: #45475a; -fx-text-fill: #cdd6f4; "
                + "-fx-font-size: 13; -fx-padding: 6 18; -fx-background-radius: 6;");
        b.setOnMouseEntered(e -> b.setStyle("-fx-background-color: #585b70; -fx-text-fill: #cdd6f4; "
                + "-fx-font-size: 13; -fx-padding: 6 18; -fx-background-radius: 6;"));
        b.setOnMouseExited(e -> b.setStyle("-fx-background-color: #45475a; -fx-text-fill: #cdd6f4; "
                + "-fx-font-size: 13; -fx-padding: 6 18; -fx-background-radius: 6;"));
        return b;
    }

    private static Button smallButton(String text) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color: #45475a; -fx-text-fill: #a6e3a1; "
                + "-fx-font-size: 13; -fx-padding: 2 8; -fx-background-radius: 4; -fx-font-weight: bold;");
        b.setOnMouseEntered(e -> b.setStyle("-fx-background-color: #585b70; -fx-text-fill: #a6e3a1; "
                + "-fx-font-size: 13; -fx-padding: 2 8; -fx-background-radius: 4; -fx-font-weight: bold;"));
        b.setOnMouseExited(e -> b.setStyle("-fx-background-color: #45475a; -fx-text-fill: #a6e3a1; "
                + "-fx-font-size: 13; -fx-padding: 2 8; -fx-background-radius: 4; -fx-font-weight: bold;"));
        return b;
    }

    private static String comboStyle() {
        return "-fx-background-color: #313244; -fx-text-fill: #cdd6f4; "
                + "-fx-font-size: 12; -fx-background-radius: 4;";
    }

    private static <T> void styleComboBoxCells(ComboBox<T> combo) {
        javafx.util.Callback<ListView<T>, ListCell<T>> cellFactory = lv -> new ListCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.toString());
                setStyle("-fx-text-fill: #cdd6f4; -fx-background-color: #313244;");
            }
        };
        combo.setCellFactory(cellFactory);
        combo.setButtonCell(cellFactory.call(null));
    }

    public static void main(String[] args) {
        launch(args);
    }
}
