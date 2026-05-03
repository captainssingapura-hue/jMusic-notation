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
import music.notation.play.PatchRef;
import music.notation.play.SoundBankInstrument;
import music.notation.play.SoundBankRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

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

    /** Open the modal and return the chosen instrument, if any. No live preview. */
    static Optional<Instrument> show(Window owner, String title, Instrument current) {
        return show(owner, title, current, null);
    }

    /**
     * Open the modal with live preview. Each selection change in the
     * instrument list invokes {@code onPreview}, so the host can apply
     * the candidate instrument immediately. On cancel, {@code onPreview}
     * is invoked once more with the original {@code current} value so
     * the host can revert. If {@code onPreview} is null, behaves like
     * the no-preview overload.
     */
    static Optional<Instrument> show(Window owner, String title, Instrument current,
                                     Consumer<Instrument> onPreview) {
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
        familyList.setCellFactory(lv -> {
            var cell = new javafx.scene.control.ListCell<String>() {
                @Override protected void updateItem(String name, boolean empty) {
                    super.updateItem(name, empty);
                    setText(empty || name == null ? null : name);
                    restyle(this);
                }
            };
            cell.selectedProperty().addListener((obs, o, n) -> restyle(cell));
            return cell;
        });

        // ── Right: instrument list
        ObservableList<Instrument> instItems = FXCollections.observableArrayList();
        ListView<Instrument> instList = new ListView<>(instItems);
        instList.setStyle("-fx-control-inner-background: #1e1e2e; -fx-text-fill: #cdd6f4;");
        instList.setCellFactory(lv -> {
            var cell = new javafx.scene.control.ListCell<Instrument>() {
                @Override protected void updateItem(Instrument ins, boolean empty) {
                    super.updateItem(ins, empty);
                    setText(empty || ins == null ? null : displayName(ins) + "   (" + ins.program() + ")");
                    restyle(this);
                }
            };
            // Re-apply styling whenever the cell's selection state flips,
            // so the selected row gets readable contrast.
            cell.selectedProperty().addListener((obs, o, n) -> restyle(cell));
            return cell;
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

        // Live preview: every selection change in the instrument list
        // fires onPreview so the host can apply the candidate now.
        if (onPreview != null) {
            instList.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
                if (n != null) onPreview.accept(n);
            });
        }

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
        boolean[] committed = {false};
        okBtn.setOnAction(e -> {
            result[0] = instList.getSelectionModel().getSelectedItem();
            committed[0] = true;
            stage.close();
        });
        cancelBtn.setOnAction(e -> stage.close());
        instList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && instList.getSelectionModel().getSelectedItem() != null) {
                result[0] = instList.getSelectionModel().getSelectedItem();
                committed[0] = true;
                stage.close();
            }
        });
        // Window-close (X button) is treated as cancel.
        stage.setOnCloseRequest(e -> { /* no commit */ });
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

        // On cancel, revert the live-preview by re-applying the original.
        if (!committed[0] && onPreview != null && current != null) {
            onPreview.accept(current);
        }
        return Optional.ofNullable(result[0]);
    }

    /**
     * Patch-level overload: family → instrument → soundbank-variant
     * drill-down. The third pane lists {@link SoundBankInstrument}s
     * available from the registry under the selected GM family,
     * preceded by an explicit "(Use GM default)" option. Live preview
     * fires with the resolved {@link PatchRef} on every selection
     * change. On cancel, the original {@code current} is reapplied via
     * {@code onPreview}.
     */
    static Optional<PatchRef> showPatch(Window owner, String title,
                                         PatchRef current,
                                         SoundBankRegistry registry,
                                         Consumer<PatchRef> onPreview) {
        Stage stage = new Stage();
        stage.setTitle(title);
        stage.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) stage.initOwner(owner);

        SoundBankRegistry reg = registry == null ? SoundBankRegistry.empty() : registry;
        Instrument currentGm = current != null ? current.instrument()
                : Instrument.ACOUSTIC_GRAND_PIANO;

        // Group GM instruments by family for the family/instrument columns.
        Map<String, List<Instrument>> byFamily = new LinkedHashMap<>();
        for (Family f : FAMILIES) byFamily.put(f.name(), new ArrayList<>());
        for (Instrument ins : Instrument.values()) byFamily.get(familyOf(ins)).add(ins);

        TextField search = new TextField();
        search.setPromptText("Search instruments…");
        search.setStyle("-fx-background-color: #313244; -fx-text-fill: #cdd6f4;");

        ObservableList<String> familyNames = FXCollections.observableArrayList();
        for (Family f : FAMILIES) {
            if (!byFamily.get(f.name()).isEmpty()) familyNames.add(f.name());
        }
        ListView<String> familyList = new ListView<>(familyNames);
        familyList.setPrefWidth(160);
        familyList.setStyle("-fx-control-inner-background: #1e1e2e; -fx-text-fill: #cdd6f4;");
        familyList.setCellFactory(lv -> {
            var cell = new javafx.scene.control.ListCell<String>() {
                @Override protected void updateItem(String name, boolean empty) {
                    super.updateItem(name, empty);
                    setText(empty || name == null ? null : name);
                    restyle(this);
                }
            };
            cell.selectedProperty().addListener((obs, o, n) -> restyle(cell));
            return cell;
        });

        ObservableList<Instrument> instItems = FXCollections.observableArrayList();
        ListView<Instrument> instList = new ListView<>(instItems);
        instList.setPrefWidth(220);
        instList.setStyle("-fx-control-inner-background: #1e1e2e; -fx-text-fill: #cdd6f4;");
        instList.setCellFactory(lv -> {
            var cell = new javafx.scene.control.ListCell<Instrument>() {
                @Override protected void updateItem(Instrument ins, boolean empty) {
                    super.updateItem(ins, empty);
                    setText(empty || ins == null ? null : displayName(ins) + "   (" + ins.program() + ")");
                    restyle(this);
                }
            };
            cell.selectedProperty().addListener((obs, o, n) -> restyle(cell));
            return cell;
        });

        // ── Third pane: SBI variants for the selected GM instrument.
        // First entry is sentinel "(Use GM default)" — null payload means no SBI override.
        ObservableList<SoundBankInstrument> variantItems = FXCollections.observableArrayList();
        ListView<SoundBankInstrument> variantList = new ListView<>(variantItems);
        variantList.setStyle("-fx-control-inner-background: #1e1e2e; -fx-text-fill: #cdd6f4;");
        variantList.setCellFactory(lv -> {
            var cell = new javafx.scene.control.ListCell<SoundBankInstrument>() {
                @Override protected void updateItem(SoundBankInstrument sbi, boolean empty) {
                    super.updateItem(sbi, empty);
                    if (empty) { setText(null); restyle(this); return; }
                    if (sbi == null) {
                        setText("(Use GM default)");
                    } else {
                        String suffix = sbi.soundbankName().isEmpty() ? "" : "   · " + sbi.soundbankName();
                        setText(sbi.displayName() + suffix);
                    }
                    restyle(this);
                }
            };
            cell.selectedProperty().addListener((obs, o, n) -> restyle(cell));
            return cell;
        });

        VBox variantBox = new VBox(4);
        Label variantHeader = new Label("Soundbank variations");
        variantHeader.setStyle("-fx-text-fill: #a6adc8; -fx-font-size: 11; -fx-padding: 4 6 4 6;");
        variantBox.getChildren().addAll(variantHeader, variantList);
        VBox.setVgrow(variantList, Priority.ALWAYS);
        variantBox.setStyle("-fx-background-color: #1e1e2e;");

        // ── Refresh handlers.
        Runnable refreshInstruments = () -> {
            String q = search.getText() == null ? "" : search.getText().trim().toLowerCase();
            String fam = familyList.getSelectionModel().getSelectedItem();
            instItems.clear();
            if (!q.isEmpty()) {
                for (Instrument ins : Instrument.values()) {
                    if (displayName(ins).toLowerCase().contains(q)) instItems.add(ins);
                }
            } else if (fam != null) {
                instItems.addAll(byFamily.get(fam));
            }
            if (currentGm != null && instItems.contains(currentGm)) {
                instList.getSelectionModel().select(currentGm);
            } else if (!instItems.isEmpty()) {
                instList.getSelectionModel().select(0);
            }
        };

        Runnable refreshVariants = () -> {
            Instrument inst = instList.getSelectionModel().getSelectedItem();
            variantItems.clear();
            variantItems.add(null);  // sentinel: null → "(Use GM default)"
            if (inst != null) {
                variantItems.addAll(reg.variantsFor(inst));
            }
            int selectIdx = 0;
            if (current != null && current.isCustom() && inst != null && inst == current.instrument()) {
                int b = current.bankOverride() == null ? -1 : current.bankOverride();
                int p = current.programOverride() == null ? -1 : current.programOverride();
                for (int i = 1; i < variantItems.size(); i++) {
                    var sbi = variantItems.get(i);
                    if (sbi.bank() == b && sbi.program() == p) {
                        selectIdx = i;
                        break;
                    }
                }
            }
            variantList.getSelectionModel().select(selectIdx);
        };

        familyList.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> refreshInstruments.run());
        search.textProperty().addListener((obs, o, n) -> refreshInstruments.run());
        instList.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> refreshVariants.run());

        // Live preview fires on every variant or instrument change.
        Runnable firePreview = () -> {
            if (onPreview == null) return;
            Instrument inst = instList.getSelectionModel().getSelectedItem();
            if (inst == null) return;
            SoundBankInstrument sbi = variantList.getSelectionModel().getSelectedItem();
            onPreview.accept(sbi != null ? PatchRef.soundbank(sbi) : PatchRef.gm(inst));
        };
        variantList.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> firePreview.run());
        instList.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> firePreview.run());

        // Pre-select current's family.
        String currentFamily = familyOf(currentGm);
        if (familyNames.contains(currentFamily)) {
            familyList.getSelectionModel().select(currentFamily);
        } else if (!familyNames.isEmpty()) {
            familyList.getSelectionModel().select(0);
        }

        Button okBtn = new Button("OK");
        Button cancelBtn = new Button("Cancel");
        PatchRef[] result = { null };
        boolean[] committed = { false };
        okBtn.setOnAction(e -> {
            Instrument inst = instList.getSelectionModel().getSelectedItem();
            if (inst != null) {
                SoundBankInstrument sbi = variantList.getSelectionModel().getSelectedItem();
                result[0] = sbi != null ? PatchRef.soundbank(sbi) : PatchRef.gm(inst);
                committed[0] = true;
            }
            stage.close();
        });
        cancelBtn.setOnAction(e -> stage.close());
        variantList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                Instrument inst = instList.getSelectionModel().getSelectedItem();
                if (inst != null) {
                    SoundBankInstrument sbi = variantList.getSelectionModel().getSelectedItem();
                    result[0] = sbi != null ? PatchRef.soundbank(sbi) : PatchRef.gm(inst);
                    committed[0] = true;
                    stage.close();
                }
            }
        });
        stage.setOnCloseRequest(e -> { /* no commit */ });
        okBtn.setStyle("-fx-background-color: #89b4fa; -fx-text-fill: #1e1e2e; -fx-font-weight: bold;");
        cancelBtn.setStyle("-fx-background-color: #45475a; -fx-text-fill: #cdd6f4;");

        HBox buttons = new HBox(8, okBtn, cancelBtn);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPadding(new Insets(8, 8, 8, 8));

        HBox lists = new HBox(0, familyList, instList, variantBox);
        HBox.setHgrow(variantBox, Priority.ALWAYS);
        VBox.setVgrow(lists, Priority.ALWAYS);

        Label header = new Label("Choose an instrument");
        header.setStyle("-fx-text-fill: #cdd6f4; -fx-font-size: 14; -fx-font-weight: bold;");
        VBox top = new VBox(6, header, search);
        top.setPadding(new Insets(8, 8, 8, 8));

        VBox root = new VBox(top, lists, buttons);
        root.setStyle("-fx-background-color: #1e1e2e;");
        ((Region) lists).setPrefHeight(420);

        Scene scene = new Scene(root, 760, 520);
        stage.setScene(scene);
        stage.setMinWidth(620);
        stage.setMinHeight(380);

        refreshInstruments.run();
        refreshVariants.run();

        stage.showAndWait();

        if (!committed[0] && onPreview != null && current != null) {
            onPreview.accept(current);
        }
        return Optional.ofNullable(result[0]);
    }

    /** Apply readable colours to a list cell, accounting for selection state. */
    private static void restyle(javafx.scene.control.ListCell<?> cell) {
        if (cell.isEmpty()) {
            cell.setStyle("");
            return;
        }
        if (cell.isSelected()) {
            // Dark text on light-blue selection — high contrast.
            cell.setStyle("-fx-background-color: #89b4fa; -fx-text-fill: #1e1e2e; -fx-font-weight: bold;");
        } else {
            cell.setStyle("-fx-background-color: transparent; -fx-text-fill: #cdd6f4;");
        }
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
