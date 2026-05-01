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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Modal dialog for picking a {@link MusicalPiece} from
 * {@link PieceLibrary}, organised as Collection → Type → Piece with a
 * free-text search filter. Mirrors the look of
 * {@link InstrumentPickerDialog}.
 *
 * <p>No live preview — switching pieces is heavy (load + reset transport),
 * so we only commit on OK or double-click.</p>
 */
final class PiecePickerDialog {

    private PiecePickerDialog() {}

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

        // ── Left: tree of Collection → Type
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
        tree.setPrefWidth(200);
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

        // ── Right: piece list
        ObservableList<MusicalPiece> items = FXCollections.observableArrayList();
        ListView<MusicalPiece> pieceList = new ListView<>(items);
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

        // ── Refresh: filter by tree selection + search.
        Runnable refresh = () -> {
            String q = search.getText() == null ? "" : search.getText().trim().toLowerCase();
            items.clear();
            if (!q.isEmpty()) {
                // Search overrides tree filter.
                for (MusicalPiece p : PieceLibrary.pieces()) {
                    if (p.title().toLowerCase().contains(q)
                            || p.composer().toLowerCase().contains(q)) {
                        items.add(p);
                    }
                }
            } else {
                TreeItem<String> sel = tree.getSelectionModel().getSelectedItem();
                if (sel == null || sel == root) {
                    // Show everything in stable order.
                    for (var coll : grouped.values()) {
                        for (var pieces : coll.values()) items.addAll(pieces);
                    }
                } else if (sel.getParent() == root) {
                    // Collection-level: show all types under it.
                    var coll = grouped.get(sel.getValue());
                    if (coll != null) for (var pieces : coll.values()) items.addAll(pieces);
                } else {
                    // Type-level: a specific type within a specific collection.
                    String collName = sel.getParent().getValue();
                    String typeName = sel.getValue();
                    var coll = grouped.get(collName);
                    if (coll != null) {
                        var pieces = coll.get(typeName);
                        if (pieces != null) items.addAll(pieces);
                    }
                }
            }
            // Pre-select current title if it's in the filtered list.
            if (currentTitle != null) {
                for (int i = 0; i < items.size(); i++) {
                    if (items.get(i).title().equals(currentTitle)) {
                        pieceList.getSelectionModel().select(i);
                        return;
                    }
                }
            }
            if (!items.isEmpty()) pieceList.getSelectionModel().select(0);
        };

        tree.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> refresh.run());
        search.textProperty().addListener((obs, o, n) -> refresh.run());

        // Pre-select the tree node that matches the current piece (best-effort).
        if (currentTitle != null) {
            outer:
            for (var collEntry : grouped.entrySet()) {
                for (var typeEntry : collEntry.getValue().entrySet()) {
                    for (MusicalPiece p : typeEntry.getValue()) {
                        if (p.title().equals(currentTitle)) {
                            // Find and select the matching type node.
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

        // ── Bottom: Load file / OK / Cancel
        Button okBtn = new Button("OK");
        Button cancelBtn = new Button("Cancel");
        Button loadBtn = new Button("Load file…");
        PieceChoice[] result = { null };
        okBtn.setOnAction(e -> {
            var sel = pieceList.getSelectionModel().getSelectedItem();
            if (sel != null) result[0] = new PieceChoice.Library(sel.title());
            stage.close();
        });
        cancelBtn.setOnAction(e -> stage.close());
        pieceList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && pieceList.getSelectionModel().getSelectedItem() != null) {
                result[0] = new PieceChoice.Library(pieceList.getSelectionModel().getSelectedItem().title());
                stage.close();
            }
        });
        loadBtn.setOnAction(e -> {
            var chooser = new javafx.stage.FileChooser();
            chooser.setTitle("Load MIDI file");
            chooser.getExtensionFilters().add(
                    new javafx.stage.FileChooser.ExtensionFilter("MIDI Files", "*.mid", "*.midi"));
            java.io.File f = chooser.showOpenDialog(stage);
            if (f == null) return;
            try {
                byte[] bytes = java.nio.file.Files.readAllBytes(f.toPath());
                String label = f.getName().replaceFirst("\\.[^.]+$", "");
                var imp = music.notation.performance.MidiCodec.fromMidiWithMeta(bytes, label);
                result[0] = new PieceChoice.Imported(imp);
                stage.close();
            } catch (Exception ex) {
                ex.printStackTrace();
                var alert = new javafx.scene.control.Alert(
                        javafx.scene.control.Alert.AlertType.ERROR,
                        "Failed to load MIDI: " + ex.getMessage());
                alert.initOwner(stage);
                alert.showAndWait();
            }
        });
        okBtn.setStyle("-fx-background-color: #89b4fa; -fx-text-fill: #1e1e2e; -fx-font-weight: bold;");
        cancelBtn.setStyle("-fx-background-color: #45475a; -fx-text-fill: #cdd6f4;");
        loadBtn.setStyle("-fx-background-color: #45475a; -fx-text-fill: #cdd6f4;");

        Region buttonSpacer = new Region();
        HBox.setHgrow(buttonSpacer, Priority.ALWAYS);
        HBox buttons = new HBox(8, loadBtn, buttonSpacer, cancelBtn, okBtn);
        buttons.setAlignment(Pos.CENTER_LEFT);
        buttons.setPadding(new Insets(8, 8, 8, 8));

        // ── Layout
        HBox lists = new HBox(0, tree, pieceList);
        HBox.setHgrow(pieceList, Priority.ALWAYS);
        VBox.setVgrow(lists, Priority.ALWAYS);

        Label header = new Label("Choose a piece");
        header.setStyle("-fx-text-fill: #cdd6f4; -fx-font-size: 14; -fx-font-weight: bold;");
        VBox top = new VBox(6, header, search);
        top.setPadding(new Insets(8, 8, 8, 8));

        VBox rootBox = new VBox(top, lists, buttons);
        rootBox.setStyle("-fx-background-color: #1e1e2e;");
        ((Region) lists).setPrefHeight(420);

        Scene scene = new Scene(rootBox, 640, 520);
        stage.setScene(scene);
        stage.setMinWidth(480);
        stage.setMinHeight(380);

        refresh.run();
        stage.showAndWait();
        return Optional.ofNullable(result[0]);
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
