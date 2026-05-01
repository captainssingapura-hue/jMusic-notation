package music.notation.ui;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import music.notation.pitch.Accidental;
import music.notation.pitch.NoteName;
import music.notation.play.SwingSetup;
import music.notation.structure.KeySignature;
import music.notation.structure.Mode;
import music.notation.structure.PieceContentProvider;

import java.util.List;
import java.util.function.Consumer;

/**
 * Composable "Control" surface — provider/arrangement, scale, BPM,
 * status, and piece-info. Owns its widgets and exposes a tight
 * listener API; the host (NotationApp) wires callbacks into model
 * state. Designed to be mounted anywhere — current placement is in a
 * right-edge drawer, but it could equally be a tab, a popup, or an
 * inlined column.
 */
final class ControlsPanel {

    private final VBox root;

    private final ComboBox<PieceContentProvider<?>> providerSelector;
    private final HBox providerRow;

    private final ComboBox<String> rootNoteCombo;
    private final ComboBox<Mode> modeCombo;

    private final Slider bpmSlider;
    private final Label bpmValueLabel;

    private final ComboBox<SwingChoice> swingCombo;

    private final Label statusLabel;
    private final Label pieceInfoLabel;

    // Listener slots populated by the host.
    private Consumer<PieceContentProvider<?>> onProviderSelected = p -> {};
    private Runnable onScaleChanged = () -> {};
    private Runnable onBpmReleased  = () -> {};
    private Consumer<SwingSetup> onSwingChanged = s -> {};

    /** Suppress flag so programmatic mutations don't fire user listeners. */
    private boolean suppressEvents;

    ControlsPanel() {
        root = new VBox(10);
        root.setPadding(new Insets(10));
        root.setStyle("-fx-background-color: #1e1e2e;");

        // ── Arrangement (provider) row
        providerRow = new HBox(10);
        providerRow.setAlignment(Pos.CENTER_LEFT);
        Label providerLabel = styledLabel("Arrangement:");
        providerSelector = new ComboBox<>();
        providerSelector.setStyle(comboStyle());
        providerSelector.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(providerSelector, Priority.ALWAYS);
        providerSelector.setCellFactory(lv -> providerCell());
        providerSelector.setButtonCell(providerCell());
        providerSelector.setOnAction(e -> {
            if (suppressEvents) return;
            onProviderSelected.accept(providerSelector.getValue());
        });
        providerRow.getChildren().addAll(providerLabel, providerSelector);

        // ── Scale row
        HBox scaleRow = new HBox(10);
        scaleRow.setAlignment(Pos.CENTER_LEFT);
        Label scaleLabel = styledLabel("Scale:");
        rootNoteCombo = new ComboBox<>(FXCollections.observableArrayList(
                "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"));
        rootNoteCombo.setStyle(comboStyle());
        styleComboCells(rootNoteCombo);
        rootNoteCombo.setOnAction(e -> { if (!suppressEvents) onScaleChanged.run(); });

        modeCombo = new ComboBox<>(FXCollections.observableArrayList(Mode.values()));
        modeCombo.setStyle(comboStyle());
        modeCombo.setCellFactory(lv -> modeCell());
        modeCombo.setButtonCell(modeCell());
        modeCombo.setOnAction(e -> { if (!suppressEvents) onScaleChanged.run(); });
        scaleRow.getChildren().addAll(scaleLabel, rootNoteCombo, modeCombo);

        // ── BPM row
        HBox bpmRow = new HBox(10);
        bpmRow.setAlignment(Pos.CENTER_LEFT);
        Label bpmLabel = styledLabel("Tempo:");
        bpmSlider = new Slider(40, 240, 120);
        bpmSlider.setBlockIncrement(1);
        bpmSlider.setMajorTickUnit(40);
        bpmSlider.setMinorTickCount(3);
        bpmSlider.setShowTickMarks(true);
        bpmSlider.setStyle("-fx-control-inner-background: #313244;");
        HBox.setHgrow(bpmSlider, Priority.ALWAYS);
        bpmSlider.setMaxWidth(Double.MAX_VALUE);
        bpmValueLabel = new Label("120 BPM");
        bpmValueLabel.setStyle("-fx-text-fill: #cdd6f4; -fx-font-size: 12; -fx-min-width: 70;");
        bpmSlider.valueProperty().addListener((obs, oldV, newV) ->
                bpmValueLabel.setText(((int) newV.doubleValue()) + " BPM"));
        bpmSlider.valueChangingProperty().addListener((obs, wasChanging, isChanging) -> {
            if (!isChanging && !suppressEvents) onBpmReleased.run();
        });
        bpmSlider.setOnKeyReleased(e -> { if (!suppressEvents) onBpmReleased.run(); });
        bpmRow.getChildren().addAll(bpmLabel, bpmSlider, bpmValueLabel);

        // ── Swing row
        HBox swingRow = new HBox(10);
        swingRow.setAlignment(Pos.CENTER_LEFT);
        Label swingLabel = styledLabel("Swing:");
        swingCombo = new ComboBox<>(FXCollections.observableArrayList(SwingChoice.values()));
        swingCombo.setStyle(comboStyle());
        swingCombo.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(swingCombo, Priority.ALWAYS);
        swingCombo.setCellFactory(lv -> swingCell());
        swingCombo.setButtonCell(swingCell());
        swingCombo.setValue(SwingChoice.OFF);
        swingCombo.setOnAction(e -> {
            if (suppressEvents) return;
            SwingChoice c = swingCombo.getValue();
            if (c != null) onSwingChanged.accept(c.setup);
        });
        swingRow.getChildren().addAll(swingLabel, swingCombo);

        // ── Status + piece info
        statusLabel = new Label("Select a piece to begin");
        statusLabel.setStyle("-fx-text-fill: #a6adc8; -fx-font-size: 12;");
        pieceInfoLabel = new Label("");
        pieceInfoLabel.setStyle("-fx-text-fill: #a6adc8; -fx-font-size: 12;");
        pieceInfoLabel.setWrapText(true);

        root.getChildren().addAll(providerRow, scaleRow, bpmRow, swingRow, statusLabel, pieceInfoLabel);
    }

    Node getRoot() { return root; }

    // ── Listener wiring ─────────────────────────────────────────────

    void setOnProviderSelected(Consumer<PieceContentProvider<?>> handler) {
        this.onProviderSelected = handler == null ? p -> {} : handler;
    }

    void setOnScaleChanged(Runnable handler) {
        this.onScaleChanged = handler == null ? () -> {} : handler;
    }

    /** Fires when the user releases the BPM slider (mouse or keyboard). */
    void setOnBpmReleased(Runnable handler) {
        this.onBpmReleased = handler == null ? () -> {} : handler;
    }

    /** Fires when the swing selector changes; receives the chosen {@link SwingSetup}. */
    void setOnSwingChanged(Consumer<SwingSetup> handler) {
        this.onSwingChanged = handler == null ? s -> {} : handler;
    }

    /** Programmatically reset the swing selector (e.g. on dialog cancel). */
    void setSwing(SwingSetup setup) {
        suppressEvents = true;
        try {
            swingCombo.setValue(SwingChoice.from(setup));
        } finally {
            suppressEvents = false;
        }
    }

    // ── State setters / getters ─────────────────────────────────────

    void setProviders(List<PieceContentProvider<?>> providers) {
        suppressEvents = true;
        try {
            providerSelector.setItems(FXCollections.observableArrayList(providers));
            if (!providers.isEmpty()) {
                providerSelector.getSelectionModel().selectFirst();
            }
            boolean show = providers.size() > 1;
            providerRow.setVisible(show);
            providerRow.setManaged(show);
        } finally {
            suppressEvents = false;
        }
    }

    PieceContentProvider<?> getSelectedProvider() {
        return providerSelector.getValue();
    }

    void setKey(KeySignature key) {
        suppressEvents = true;
        try {
            rootNoteCombo.setValue(keyToRootLabel(key));
            modeCombo.setValue(key.mode());
        } finally {
            suppressEvents = false;
        }
    }

    KeySignature getSelectedKey() {
        String rootLabel = rootNoteCombo.getValue();
        if (rootLabel == null || modeCombo.getValue() == null) return null;
        NoteName tonic;
        Accidental acc = Accidental.NATURAL;
        if (rootLabel.endsWith("#")) {
            tonic = NoteName.valueOf(rootLabel.substring(0, 1));
            acc = Accidental.SHARP;
        } else {
            tonic = NoteName.valueOf(rootLabel);
        }
        return new KeySignature(tonic, acc, modeCombo.getValue());
    }

    void setBpm(int bpm) {
        suppressEvents = true;
        try {
            bpmSlider.setValue(bpm);
            bpmValueLabel.setText(bpm + " BPM");
        } finally {
            suppressEvents = false;
        }
    }

    int getSelectedBpm() {
        return (int) Math.round(bpmSlider.getValue());
    }

    void setStatus(String text)    { statusLabel.setText(text); }
    void setPieceInfo(String text) { pieceInfoLabel.setText(text); }

    // ── Helpers ─────────────────────────────────────────────────────

    private static String keyToRootLabel(KeySignature key) {
        String root = key.tonic().name();
        if (key.accidental() == Accidental.SHARP) root += "#";
        else if (key.accidental() == Accidental.FLAT) {
            root = switch (key.tonic()) {
                case D -> "C#"; case E -> "D#"; case G -> "F#";
                case A -> "G#"; case B -> "A#";
                default -> root;
            };
        }
        return root;
    }

    private static String modeName(Mode mode) {
        return switch (mode) {
            case MAJOR -> "Major (Ionian)";
            case MINOR -> "Minor (Aeolian)";
            case DORIAN -> "Dorian";
            case PHRYGIAN -> "Phrygian";
            case LYDIAN -> "Lydian";
            case MIXOLYDIAN -> "Mixolydian";
            case AEOLIAN -> "Aeolian";
            case LOCRIAN -> "Locrian";
        };
    }

    private static ListCell<PieceContentProvider<?>> providerCell() {
        return new ListCell<>() {
            @Override protected void updateItem(PieceContentProvider<?> item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.subtitle());
                setStyle("-fx-text-fill: #cdd6f4; -fx-background-color: #313244;");
            }
        };
    }

    private static ListCell<Mode> modeCell() {
        return new ListCell<>() {
            @Override protected void updateItem(Mode item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : modeName(item));
                setStyle("-fx-text-fill: #cdd6f4; -fx-background-color: #313244;");
            }
        };
    }

    private static <T> void styleComboCells(ComboBox<T> combo) {
        javafx.util.Callback<javafx.scene.control.ListView<T>, ListCell<T>> cellFactory = lv -> new ListCell<>() {
            @Override protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.toString());
                setStyle("-fx-text-fill: #cdd6f4; -fx-background-color: #313244;");
            }
        };
        combo.setCellFactory(cellFactory);
        combo.setButtonCell(cellFactory.call(null));
    }

    private static String comboStyle() {
        return "-fx-background-color: #313244; -fx-text-fill: #cdd6f4; "
                + "-fx-font-size: 12; -fx-background-radius: 4;";
    }

    private static Label styledLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: #cdd6f4; -fx-font-size: 13;");
        return l;
    }

    private static ListCell<SwingChoice> swingCell() {
        return new ListCell<>() {
            @Override protected void updateItem(SwingChoice item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.label);
                setStyle("-fx-text-fill: #cdd6f4; -fx-background-color: #313244;");
            }
        };
    }

    /** Discrete swing choices surfaced to the UI. */
    enum SwingChoice {
        OFF("Off",         SwingSetup.OFF),
        LIGHT("Light",     SwingSetup.LIGHT),
        MEDIUM("Medium",   SwingSetup.MEDIUM),
        TRIPLET("Triplet", SwingSetup.TRIPLET);

        final String label;
        final SwingSetup setup;
        SwingChoice(String label, SwingSetup setup) { this.label = label; this.setup = setup; }

        static SwingChoice from(SwingSetup s) {
            if (s == null || s.isOff()) return OFF;
            for (var c : values()) if (Math.abs(c.setup.ratio() - s.ratio()) < 1e-6) return c;
            return OFF;
        }
    }
}
