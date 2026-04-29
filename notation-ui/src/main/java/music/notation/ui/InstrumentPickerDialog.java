package music.notation.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import music.notation.event.Instrument;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Modal dialog for picking a {@link Instrument} from the GM catalogue,
 * grouped by family with a search filter.
 *
 * <p>Pure UI; no model coupling beyond the {@code Instrument} enum.
 * Returns {@code Optional.empty()} on cancel.</p>
 */
final class InstrumentPickerDialog {

    private InstrumentPickerDialog() {}

    /** GM family grouping by program-number range. */
    private static final List<Family> FAMILIES = List.of(
            new Family("Piano",            0, 7),
            new Family("Chromatic Perc.",  8, 15),
            new Family("Organ",           16, 23),
            new Family("Guitar",          24, 31),
            new Family("Bass",            32, 39),
            new Family("Strings",         40, 47),
            new Family("Ensemble",        48, 55),
            new Family("Brass",           56, 63),
            new Family("Reed",            64, 71),
            new Family("Pipe",            72, 79),
            new Family("Synth Lead",      80, 87),
            new Family("Synth Pad",       88, 95),
            new Family("Synth FX",        96, 103),
            new Family("Ethnic",         104, 111),
            new Family("Percussive",     112, 119),
            new Family("Sound FX",       120, 127),
            new Family("Drum Kit",        -1, -1)  // special: only DRUM_KIT
    );

    /** Open the modal and return the chosen instrument, if any. */
    static Optional<Instrument> show(Window owner, String title, Instrument current) {
        Stage stage = new Stage();
        stage.setTitle(title);
        stage.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) stage.initOwner(owner);

        // Group instruments by family (and pre-compute a "All matching" list for search).
        Map<String, List<Instrument>> byFamily = new LinkedHashMap<>();
        for (Family f : FAMILIES) byFamily.put(f.name(), new ArrayList<>());
        for (Instrument ins : Instrument.values()) {
            String fam = familyOf(ins);
            byFamily.get(fam).add(ins);
        }

        // ── Top: search field
        TextField search = new TextField();
        search.setPromptText("Search instruments…");
        search.setStyle("-fx-background-color: #313244; -fx-text-fill: #cdd6f4;");

        // ── Left: family list
        ObservableList<String> familyNames = FXCollections.observableArrayList();
        for (Family f : FAMILIES) {
            if (!byFamily.get(f.name()).isEmpty()) familyNames.add(f.name());
        }
        ListView<String> familyList = new ListView<>(familyNames);
        familyList.setPrefWidth(160);
        familyList.setStyle("-fx-control-inner-background: #1e1e2e; -fx-text-fill: #cdd6f4;");

        // ── Right: instrument list
        ObservableList<Instrument> instItems = FXCollections.observableArrayList();
        ListView<Instrument> instList = new ListView<>(instItems);
        instList.setStyle("-fx-control-inner-background: #1e1e2e; -fx-text-fill: #cdd6f4;");
        instList.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(Instrument ins, boolean empty) {
                super.updateItem(ins, empty);
                setText(empty || ins == null ? null : displayName(ins) + "   (" + ins.program() + ")");
                setStyle("-fx-text-fill: #cdd6f4;");
            }
        });

        // ── Refresh handler: filter by family + search.
        Runnable refresh = () -> {
            String q = search.getText() == null ? "" : search.getText().trim().toLowerCase();
            String fam = familyList.getSelectionModel().getSelectedItem();
            instItems.clear();
            if (!q.isEmpty()) {
                // Search overrides family filter.
                for (Instrument ins : Instrument.values()) {
                    if (displayName(ins).toLowerCase().contains(q)) instItems.add(ins);
                }
            } else if (fam != null) {
                instItems.addAll(byFamily.get(fam));
            }
            // Pre-select current if present in the filtered list.
            if (current != null && instItems.contains(current)) {
                instList.getSelectionModel().select(current);
            } else if (!instItems.isEmpty()) {
                instList.getSelectionModel().select(0);
            }
        };

        familyList.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> refresh.run());
        search.textProperty().addListener((obs, o, n) -> refresh.run());

        // Pre-select current's family.
        String currentFamily = current != null ? familyOf(current) : familyNames.get(0);
        if (familyNames.contains(currentFamily)) {
            familyList.getSelectionModel().select(currentFamily);
        } else {
            familyList.getSelectionModel().select(0);
        }

        // ── Bottom: OK / Cancel
        Button okBtn = new Button("OK");
        Button cancelBtn = new Button("Cancel");
        Instrument[] result = {null};
        okBtn.setOnAction(e -> {
            result[0] = instList.getSelectionModel().getSelectedItem();
            stage.close();
        });
        cancelBtn.setOnAction(e -> stage.close());
        instList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && instList.getSelectionModel().getSelectedItem() != null) {
                result[0] = instList.getSelectionModel().getSelectedItem();
                stage.close();
            }
        });
        okBtn.setStyle("-fx-background-color: #89b4fa; -fx-text-fill: #1e1e2e; -fx-font-weight: bold;");
        cancelBtn.setStyle("-fx-background-color: #45475a; -fx-text-fill: #cdd6f4;");

        HBox buttons = new HBox(8, okBtn, cancelBtn);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPadding(new Insets(8, 8, 8, 8));

        // ── Layout
        HBox lists = new HBox(0, familyList, instList);
        HBox.setHgrow(instList, Priority.ALWAYS);
        VBox.setVgrow(lists, Priority.ALWAYS);

        Label header = new Label("Choose an instrument");
        header.setStyle("-fx-text-fill: #cdd6f4; -fx-font-size: 14; -fx-font-weight: bold;");
        VBox top = new VBox(6, header, search);
        top.setPadding(new Insets(8, 8, 8, 8));

        VBox root = new VBox(top, lists, buttons);
        root.setStyle("-fx-background-color: #1e1e2e;");
        ((Region) lists).setPrefHeight(360);

        Scene scene = new Scene(root, 560, 480);
        stage.setScene(scene);
        stage.setMinWidth(420);
        stage.setMinHeight(360);

        // Initial population.
        refresh.run();

        stage.showAndWait();
        return Optional.ofNullable(result[0]);
    }

    private static String familyOf(Instrument ins) {
        if (ins == Instrument.DRUM_KIT) return "Drum Kit";
        int p = ins.program();
        for (Family f : FAMILIES) {
            if (f.lo() < 0) continue;
            if (p >= f.lo() && p <= f.hi()) return f.name();
        }
        return "Sound FX";
    }

    /** Pretty-print "ACOUSTIC_GRAND_PIANO" → "Acoustic Grand Piano". */
    static String displayName(Instrument ins) {
        String[] parts = ins.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (p.isEmpty()) continue;
            if (i > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    private record Family(String name, int lo, int hi) {}
}
