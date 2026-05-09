package music.notation.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import music.notation.songs.PieceLibrary;
import music.notation.structure.MusicalPiece;
import music.notation.structure.PieceContentProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.prefs.Preferences;

/**
 * Modal dialog for picking a {@link MusicalPiece} from
 * {@link PieceLibrary}, organised as Collection → Type → Piece →
 * Arrangement (variation) with a free-text search filter. Mirrors the
 * look of {@link InstrumentPickerDialog}.
 *
 * <p>No live preview — switching pieces is heavy (load + reset transport),
 * so we only commit on OK or double-click.</p>
 */
final class PiecePickerDialog {

    private PiecePickerDialog() {}

    private static final Preferences PREFS =
            Preferences.userNodeForPackage(PiecePickerDialog.class);
    private static final String PREF_LAST_MIDI_DIR = "piecePicker.lastMidiDir";
    private static final String PREF_LAST_MXL_DIR  = "piecePicker.lastMxlDir";
    private static final String PREF_LAST_JSON_DIR = "piecePicker.lastJsonDir";

    /** Variation row: provider index + display label (subtitle or "Default"). */
    private record Variation(int providerIndex, String label) {}

    private static List<Variation> variationsFor(MusicalPiece piece) {
        List<PieceContentProvider<?>> provs = PieceLibrary.providers(piece.title());
        if (provs.isEmpty()) return List.of();
        if (provs.size() == 1) return List.of(new Variation(0, "Default"));
        var out = new ArrayList<Variation>(provs.size());
        for (int i = 0; i < provs.size(); i++) {
            String sub = provs.get(i).subtitle();
            out.add(new Variation(i, (sub == null || sub.isBlank()) ? ("Variation " + (i + 1)) : sub));
        }
        return out;
    }

    /** Open the modal and return the chosen piece (library title or imported file). */
    static Optional<PieceChoice> show(Window owner, String title, String currentTitle) {
        Stage stage = new Stage();
        stage.setTitle(title);
        stage.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) stage.initOwner(owner);

        // Build Collection → Type → [pieces] index from the library.
        Map<String, Map<String, List<MusicalPiece>>> grouped = new TreeMap<>();
        for (MusicalPiece p : PieceLibrary.pieces()) {
            String coll = PieceLibrary.collectionOf(p);
            String type = PieceLibrary.typeOf(p);
            grouped.computeIfAbsent(coll, k -> new TreeMap<>())
                    .computeIfAbsent(type, k -> new ArrayList<>())
                    .add(p);
        }

        // ── Top: search field
        TextField search = new TextField();
        search.setPromptText("Search pieces…");
        search.setStyle("-fx-background-color: #313244; -fx-text-fill: #cdd6f4;");

        // ── Pane 1: tree of Collection → Type
        TreeItem<String> root = new TreeItem<>("All");
        root.setExpanded(true);
        for (var collEntry : grouped.entrySet()) {
            TreeItem<String> collNode = new TreeItem<>(collEntry.getKey());
            collNode.setExpanded(true);
            for (var typeKey : collEntry.getValue().keySet()) {
                collNode.getChildren().add(new TreeItem<>(typeKey));
            }
            root.getChildren().add(collNode);
        }
        TreeView<String> tree = new TreeView<>(root);
        tree.setShowRoot(false);
        tree.setPrefWidth(180);
        tree.setStyle("-fx-control-inner-background: #1e1e2e; -fx-text-fill: #cdd6f4;");
        tree.setCellFactory(lv -> {
            var cell = new TreeCell<String>() {
                @Override protected void updateItem(String name, boolean empty) {
                    super.updateItem(name, empty);
                    setText(empty || name == null ? null : name);
                    restyleTree(this);
                }
            };
            cell.selectedProperty().addListener((obs, o, n) -> restyleTree(cell));
            return cell;
        });

        // ── Pane 2: piece list (one row per MusicalPiece)
        ObservableList<MusicalPiece> pieceItems = FXCollections.observableArrayList();
        ListView<MusicalPiece> pieceList = new ListView<>(pieceItems);
        pieceList.setPrefWidth(260);
        pieceList.setStyle("-fx-control-inner-background: #1e1e2e; -fx-text-fill: #cdd6f4;");
        pieceList.setCellFactory(lv -> {
            var cell = new javafx.scene.control.ListCell<MusicalPiece>() {
                @Override protected void updateItem(MusicalPiece p, boolean empty) {
                    super.updateItem(p, empty);
                    setText(empty || p == null ? null : p.title() + "   — " + p.composer());
                    restyleList(this);
                }
            };
            cell.selectedProperty().addListener((obs, o, n) -> restyleList(cell));
            return cell;
        });

        // ── Pane 3: variation list (providers for the currently-selected piece)
        ObservableList<Variation> variationItems = FXCollections.observableArrayList();
        ListView<Variation> variationList = new ListView<>(variationItems);
        variationList.setPrefWidth(180);
        variationList.setStyle("-fx-control-inner-background: #1e1e2e; -fx-text-fill: #cdd6f4;");
        variationList.setCellFactory(lv -> {
            var cell = new javafx.scene.control.ListCell<Variation>() {
                @Override protected void updateItem(Variation v, boolean empty) {
                    super.updateItem(v, empty);
                    setText(empty || v == null ? null : v.label());
                    restyleList(this);
                }
            };
            cell.selectedProperty().addListener((obs, o, n) -> restyleList(cell));
            return cell;
        });

        // ── Refresh piece list: filter by tree selection + search.
        Runnable refreshPieces = () -> {
            String q = search.getText() == null ? "" : search.getText().trim().toLowerCase();
            pieceItems.clear();
            if (!q.isEmpty()) {
                for (MusicalPiece p : PieceLibrary.pieces()) {
                    if (p.title().toLowerCase().contains(q)
                            || p.composer().toLowerCase().contains(q)) {
                        pieceItems.add(p);
                    }
                }
            } else {
                TreeItem<String> sel = tree.getSelectionModel().getSelectedItem();
                if (sel == null || sel == root) {
                    for (var coll : grouped.values()) {
                        for (var pieces : coll.values()) pieceItems.addAll(pieces);
                    }
                } else if (sel.getParent() == root) {
                    var coll = grouped.get(sel.getValue());
                    if (coll != null) for (var pieces : coll.values()) pieceItems.addAll(pieces);
                } else {
                    String collName = sel.getParent().getValue();
                    String typeName = sel.getValue();
                    var coll = grouped.get(collName);
                    if (coll != null) {
                        var pieces = coll.get(typeName);
                        if (pieces != null) pieceItems.addAll(pieces);
                    }
                }
            }
            // Pre-select current title if present, else first.
            int chosen = -1;
            if (currentTitle != null) {
                for (int i = 0; i < pieceItems.size(); i++) {
                    if (pieceItems.get(i).title().equals(currentTitle)) { chosen = i; break; }
                }
            }
            if (chosen < 0 && !pieceItems.isEmpty()) chosen = 0;
            if (chosen >= 0) pieceList.getSelectionModel().select(chosen);
        };

        // Variations populate from the selected piece.
        Runnable refreshVariations = () -> {
            var piece = pieceList.getSelectionModel().getSelectedItem();
            variationItems.clear();
            if (piece == null) return;
            variationItems.addAll(variationsFor(piece));
            if (!variationItems.isEmpty()) {
                variationList.getSelectionModel().select(0);
            }
        };

        tree.getSelectionModel().selectedItemProperty()
                .addListener((obs, o, n) -> refreshPieces.run());
        search.textProperty().addListener((obs, o, n) -> refreshPieces.run());
        pieceList.getSelectionModel().selectedItemProperty()
                .addListener((obs, o, n) -> refreshVariations.run());

        // Pre-select the tree node that matches the current piece.
        if (currentTitle != null) {
            outer:
            for (var collEntry : grouped.entrySet()) {
                for (var typeEntry : collEntry.getValue().entrySet()) {
                    for (MusicalPiece p : typeEntry.getValue()) {
                        if (p.title().equals(currentTitle)) {
                            for (TreeItem<String> collNode : root.getChildren()) {
                                if (collNode.getValue().equals(collEntry.getKey())) {
                                    for (TreeItem<String> typeNode : collNode.getChildren()) {
                                        if (typeNode.getValue().equals(typeEntry.getKey())) {
                                            tree.getSelectionModel().select(typeNode);
                                            break outer;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (tree.getSelectionModel().getSelectedItem() == null && !root.getChildren().isEmpty()) {
            tree.getSelectionModel().select(root.getChildren().get(0));
        }

        // ── Import panel state — built first so the toggle can refer to it
        var importPanel = buildImportPanel(stage,
                () -> { /* on success, dialog closes via handleLoadFile */ });

        // ── Bottom buttons: OK / Cancel on the left, Import toggle on the right
        Button okBtn = new Button("OK");
        Button cancelBtn = new Button("Cancel");
        javafx.scene.control.ToggleButton importToggle =
                new javafx.scene.control.ToggleButton("Import…");
        importToggle.setTooltip(new javafx.scene.control.Tooltip(
                "Open the import panel — load a MIDI file, an .mxl, or a pre-processed JSON folder."));
        PieceChoice[] result = { null };

        // Wire the three load buttons. Voice-split + quantizer-profile apply
        // only to MIDI; for already-concretized sources (MXL / JSON) they're
        // passed through as PRESERVE / STANDARD no-ops.
        importPanel.loadMidiBtn.setOnAction(e ->
                handleLoadMidi(stage, result,
                        importPanel.voiceSplitBox.isSelected(),
                        importPanel.profileCombo.getValue().profile));
        importPanel.loadMxlBtn.setOnAction(e -> handleLoadMxl(stage, result));
        importPanel.loadJsonBtn.setOnAction(e -> handleLoadJsonFolder(stage, result));

        // Toggle visibility/managed of the import panel.
        importPanel.root.setVisible(false);
        importPanel.root.setManaged(false);
        importToggle.setOnAction(e -> {
            boolean show = importToggle.isSelected();
            importPanel.root.setVisible(show);
            importPanel.root.setManaged(show);
        });

        Runnable commit = () -> {
            var piece = pieceList.getSelectionModel().getSelectedItem();
            var variation = variationList.getSelectionModel().getSelectedItem();
            if (piece != null) {
                int idx = variation == null ? 0 : variation.providerIndex();
                result[0] = new PieceChoice.Library(piece.title(), idx);
            }
            stage.close();
        };

        okBtn.setOnAction(e -> commit.run());
        cancelBtn.setOnAction(e -> stage.close());
        pieceList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && pieceList.getSelectionModel().getSelectedItem() != null) {
                commit.run();
            }
        });
        variationList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && variationList.getSelectionModel().getSelectedItem() != null) {
                commit.run();
            }
        });

        okBtn.setStyle("-fx-background-color: #89b4fa; -fx-text-fill: #1e1e2e; -fx-font-weight: bold;");
        cancelBtn.setStyle("-fx-background-color: #45475a; -fx-text-fill: #cdd6f4;");
        importToggle.setStyle("-fx-background-color: #45475a; -fx-text-fill: #cdd6f4;");

        Region buttonSpacer = new Region();
        HBox.setHgrow(buttonSpacer, Priority.ALWAYS);
        HBox buttons = new HBox(8, okBtn, cancelBtn, buttonSpacer, importToggle);
        buttons.setAlignment(Pos.CENTER_LEFT);
        buttons.setPadding(new Insets(8, 8, 8, 8));

        // ── Layout: three panes, all sized to share horizontal space
        HBox lists = new HBox(0, tree, pieceList, variationList);
        HBox.setHgrow(pieceList, Priority.ALWAYS);
        VBox.setVgrow(lists, Priority.ALWAYS);

        Label header = new Label("Choose a piece");
        header.setStyle("-fx-text-fill: #cdd6f4; -fx-font-size: 14; -fx-font-weight: bold;");
        VBox top = new VBox(6, header, search);
        top.setPadding(new Insets(8, 8, 8, 8));

        VBox rootBox = new VBox(top, lists, buttons, importPanel.root);
        rootBox.setStyle("-fx-background-color: #1e1e2e;");
        ((Region) lists).setPrefHeight(420);

        Scene scene = new Scene(rootBox, 760, 520);
        stage.setScene(scene);
        stage.setMinWidth(620);
        stage.setMinHeight(380);

        refreshPieces.run();
        refreshVariations.run();
        stage.showAndWait();
        return Optional.ofNullable(result[0]);
    }

    // ── Import panel ────────────────────────────────────────────────

    /** Display-friendly wrapper for {@link music.notation.performance.QuantizerProfile}. */
    private record ProfileChoice(String label,
            music.notation.performance.QuantizerProfile profile) {
        @Override public String toString() { return label; }
    }

    private static final java.util.List<ProfileChoice> PROFILE_CHOICES = java.util.List.of(
            new ProfileChoice("Standard  (powers of 2 + dotted)",
                    music.notation.performance.QuantizerProfile.STANDARD),
            new ProfileChoice("With triplets  (Mozart, Beethoven, pop/rock)",
                    music.notation.performance.QuantizerProfile.WITH_TRIPLETS),
            new ProfileChoice("Full  (Chopin, Liszt, jazz — quintuplets + septuplets)",
                    music.notation.performance.QuantizerProfile.FULL),
            new ProfileChoice("Improvisation  (live MIDI keyboard, permissive)",
                    music.notation.performance.QuantizerProfile.IMPROV));

    /** Bundle of widgets used by the import panel. Returned to the caller. */
    private static final class ImportPanel {
        final VBox root;
        final javafx.scene.control.ComboBox<ProfileChoice> profileCombo;
        final javafx.scene.control.CheckBox voiceSplitBox;
        final Button loadMidiBtn;
        final Button loadMxlBtn;
        final Button loadJsonBtn;

        ImportPanel(VBox root,
                    javafx.scene.control.ComboBox<ProfileChoice> profileCombo,
                    javafx.scene.control.CheckBox voiceSplitBox,
                    Button loadMidiBtn,
                    Button loadMxlBtn,
                    Button loadJsonBtn) {
            this.root = root;
            this.profileCombo = profileCombo;
            this.voiceSplitBox = voiceSplitBox;
            this.loadMidiBtn = loadMidiBtn;
            this.loadMxlBtn = loadMxlBtn;
            this.loadJsonBtn = loadJsonBtn;
        }
    }

    private static ImportPanel buildImportPanel(Stage owner, Runnable onClose) {
        // Profile picker — defaults to STANDARD. MIDI-only knob; MXL/JSON ignore it.
        var profileLabel = new Label("Quantizer:");
        profileLabel.setStyle("-fx-text-fill: #cdd6f4; -fx-font-size: 12;");
        var profileCombo = new javafx.scene.control.ComboBox<ProfileChoice>();
        profileCombo.getItems().addAll(PROFILE_CHOICES);
        profileCombo.getSelectionModel().select(0);
        profileCombo.setStyle("-fx-background-color: #313244; -fx-text-fill: #cdd6f4;");
        profileCombo.setTooltip(new javafx.scene.control.Tooltip(
                "MIDI-only. Note values the quantizer recognises during import.\n"
              + "Standard:       only powers of 2 + dotted (folk/pop).\n"
              + "With triplets:  adds triplet values (Mozart, Beethoven).\n"
              + "Full:           adds quintuplets + septuplets (Chopin, Liszt).\n"
              + "Improvisation:  permissive — for live keyboard capture.\n"
              + "Ignored for MXL and JSON imports."));

        // Voice-split — MIDI-only.
        var voiceSplitBox = new javafx.scene.control.CheckBox("Voice-split tracks");
        voiceSplitBox.setStyle("-fx-text-fill: #cdd6f4;");
        voiceSplitBox.setTooltip(new javafx.scene.control.Tooltip(
                "MIDI-only.\n"
              + "Off: each input track → one Piece track (use for properly-arranged MIDIs).\n"
              + "On:  break each track into RH/LH and detected voices (use for piano blobs).\n"
              + "Ignored for MXL and JSON imports — those already arrive voice-separated."));

        // Three load buttons — one per source format.
        var loadMidiBtn = new Button("Load MIDI…");
        loadMidiBtn.setStyle("-fx-background-color: #89b4fa; -fx-text-fill: #1e1e2e; -fx-font-weight: bold;");
        loadMidiBtn.setTooltip(new javafx.scene.control.Tooltip(
                "Load a .mid / .midi file. Quantizer + voice-split apply."));

        var loadMxlBtn = new Button("Load MusicXML…");
        loadMxlBtn.setStyle("-fx-background-color: #89b4fa; -fx-text-fill: #1e1e2e; -fx-font-weight: bold;");
        loadMxlBtn.setTooltip(new javafx.scene.control.Tooltip(
                "Load a compressed MusicXML (.mxl) file."));

        var loadJsonBtn = new Button("Load JSON folder…");
        loadJsonBtn.setStyle("-fx-background-color: #89b4fa; -fx-text-fill: #1e1e2e; -fx-font-weight: bold;");
        loadJsonBtn.setTooltip(new javafx.scene.control.Tooltip(
                "Load a previously-extracted piece (folder containing meta.json + tempo.json + track-*.json)."));

        var profileRow = new HBox(8, profileLabel, profileCombo);
        profileRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(profileCombo, Priority.ALWAYS);

        var loadRow = new HBox(8, voiceSplitBox);
        Region loadSpacer = new Region();
        HBox.setHgrow(loadSpacer, Priority.ALWAYS);
        loadRow.getChildren().addAll(loadSpacer, loadMidiBtn, loadMxlBtn, loadJsonBtn);
        loadRow.setAlignment(Pos.CENTER_LEFT);

        var sectionHeader = new Label("Import file");
        sectionHeader.setStyle("-fx-text-fill: #89b4fa; -fx-font-size: 12; -fx-font-weight: bold;");

        var root = new VBox(8, sectionHeader, profileRow, loadRow);
        root.setPadding(new Insets(10, 10, 10, 10));
        root.setStyle("-fx-background-color: #181825; -fx-border-color: #45475a; -fx-border-width: 1 0 0 0;");

        return new ImportPanel(root, profileCombo, voiceSplitBox,
                loadMidiBtn, loadMxlBtn, loadJsonBtn);
    }

    /** Open MIDI file chooser, remembering the last directory in {@link Preferences}. */
    private static void handleLoadMidi(Stage stage, PieceChoice[] result,
                                       boolean voiceSplit,
                                       music.notation.performance.QuantizerProfile profile) {
        java.io.File f = openFileChooser(stage, "Load MIDI file", PREF_LAST_MIDI_DIR,
                "MIDI Files", "*.mid", "*.midi");
        if (f == null) return;
        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(f.toPath());
            String label = stripExt(f.getName());
            var imp = music.notation.performance.MidiCodec.fromMidiWithMeta(bytes, label);
            result[0] = new PieceChoice.Imported(imp, voiceSplit, profile);
            stage.close();
        } catch (Exception ex) {
            showLoadError(stage, "MIDI", ex);
        }
    }

    /**
     * Open MusicXML (.mxl) chooser and parse via {@code notation-mxl}.
     * Parsing runs on a background thread via {@link MxlImportTask};
     * a {@link ProgressDialog} keeps the UI responsive while the
     * (possibly multi-second) parse completes. Subsequent loads of
     * the same file hit the on-disk cache and return near-instantly.
     */
    private static void handleLoadMxl(Stage stage, PieceChoice[] result) {
        java.io.File f = openFileChooser(stage, "Load MusicXML file", PREF_LAST_MXL_DIR,
                "Compressed MusicXML", "*.mxl");
        if (f == null) return;

        var task = new MxlImportTask(f.toPath());
        var dialog = new ProgressDialog(stage, task);
        task.setOnSucceeded(e -> {
            dialog.close();
            result[0] = new PieceChoice.Imported(task.getValue());
            stage.close();
        });
        task.setOnFailed(e -> {
            dialog.close();
            Throwable cause = task.getException();
            Exception wrapped = (cause instanceof Exception ex) ? ex
                    : new RuntimeException(cause == null ? "unknown failure" : cause.getMessage(), cause);
            showLoadError(stage, "MusicXML", wrapped);
        });
        Thread t = new Thread(task, "mxl-import");
        t.setDaemon(true);
        t.start();
        dialog.show();
    }

    /** Open directory chooser for a previously-extracted JSON piece folder. */
    private static void handleLoadJsonFolder(Stage stage, PieceChoice[] result) {
        var chooser = new javafx.stage.DirectoryChooser();
        chooser.setTitle("Load JSON piece folder");
        String lastDir = PREFS.get(PREF_LAST_JSON_DIR, null);
        if (lastDir != null) {
            var dir = new java.io.File(lastDir);
            if (dir.isDirectory()) chooser.setInitialDirectory(dir);
        }
        java.io.File dir = chooser.showDialog(stage);
        if (dir == null) return;
        var parent = dir.getParentFile();
        if (parent != null && parent.isDirectory()) {
            PREFS.put(PREF_LAST_JSON_DIR, parent.getAbsolutePath());
        }
        try {
            var imp = music.notation.mxl.MxlSplitJsonReader.read(dir.toPath());
            result[0] = new PieceChoice.Imported(imp);
            stage.close();
        } catch (Exception ex) {
            showLoadError(stage, "JSON folder", ex);
        }
    }

    private static java.io.File openFileChooser(Stage stage, String title, String prefKey,
                                                 String filterName, String... patterns) {
        var chooser = new javafx.stage.FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(
                new javafx.stage.FileChooser.ExtensionFilter(filterName, patterns));
        String lastDir = PREFS.get(prefKey, null);
        if (lastDir != null) {
            var dir = new java.io.File(lastDir);
            if (dir.isDirectory()) chooser.setInitialDirectory(dir);
        }
        java.io.File f = chooser.showOpenDialog(stage);
        if (f != null) {
            var parent = f.getParentFile();
            if (parent != null && parent.isDirectory()) {
                PREFS.put(prefKey, parent.getAbsolutePath());
            }
        }
        return f;
    }

    private static void showLoadError(Stage stage, String kind, Exception ex) {
        ex.printStackTrace();
        var alert = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.ERROR,
                "Failed to load " + kind + ": " + ex.getMessage());
        alert.initOwner(stage);
        alert.showAndWait();
    }

    private static String stripExt(String name) {
        return name.replaceFirst("\\.[^.]+$", "");
    }

    private static void restyleList(javafx.scene.control.ListCell<?> cell) {
        if (cell.isEmpty()) {
            cell.setStyle("");
            return;
        }
        if (cell.isSelected()) {
            cell.setStyle("-fx-background-color: #89b4fa; -fx-text-fill: #1e1e2e; -fx-font-weight: bold;");
        } else {
            cell.setStyle("-fx-background-color: transparent; -fx-text-fill: #cdd6f4;");
        }
    }

    private static void restyleTree(TreeCell<?> cell) {
        if (cell.isEmpty()) {
            cell.setStyle("");
            return;
        }
        if (cell.isSelected()) {
            cell.setStyle("-fx-background-color: #89b4fa; -fx-text-fill: #1e1e2e; -fx-font-weight: bold;");
        } else {
            cell.setStyle("-fx-background-color: transparent; -fx-text-fill: #cdd6f4;");
        }
    }
}
