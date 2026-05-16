package music.notation.ui;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import music.notation.autodrum.DrumStrategies;
import music.notation.autodrum.DrumStrategy;
import music.notation.autodrum.Energy;
import music.notation.pitch.Accidental;
import music.notation.pitch.NoteName;
import music.notation.play.HumanizerSetup;
import music.notation.play.SwingSetup;
import music.notation.structure.KeySignature;
import music.notation.structure.Mode;
import music.notation.structure.PieceContentProvider;

import java.io.File;
import java.util.ArrayList;
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

    private final Slider bpmSlider;
    private final Label bpmValueLabel;

    private final ComboBox<SwingChoice> swingCombo;

    private final ComboBox<DrumStrategy> autoDrumCombo;
    private final ComboBox<Energy> energyCombo;
    private final ComboBox<HumanizerChoice> humanizerCombo;
    private final Spinner<Integer> transposeSpinner;
    private final Label transposeLabel;
    /** Source-key context for the show-both transpose label; null = no piece loaded. */
    private KeySignature originalKey = null;
    private final javafx.scene.control.RadioButton pedalSourceRadio;
    private final javafx.scene.control.RadioButton pedalAutoRadio;
    private final javafx.scene.control.RadioButton pedalOffRadio;
    private final javafx.scene.control.ToggleGroup pedalGroup;

    private final ListView<File> soundbankList = new ListView<>();
    private final Button soundbankAddBtn = new Button("Add…");
    private final Button soundbankResetBtn = new Button("Reset");

    private final Label statusLabel;
    private final Label pieceInfoLabel;

    // Listener slots populated by the host.
    private Consumer<PieceContentProvider<?>> onProviderSelected = p -> {};
    private Runnable onBpmReleased  = () -> {};
    private Consumer<SwingSetup> onSwingChanged = s -> {};
    private Consumer<DrumStrategy> onAutoDrumChanged = s -> {};
    private Consumer<Energy> onEnergyChanged = e -> {};
    private Consumer<HumanizerSetup> onHumanizerChanged = h -> {};
    private Consumer<Integer> onTranspositionChanged = n -> {};
    private Consumer<PedalMode> onPedalModeChanged = m -> {};
    private Consumer<List<File>> onSoundbanksChanged = files -> {};

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

        // ── Auto-Drum row
        HBox drumRow = new HBox(10);
        drumRow.setAlignment(Pos.CENTER_LEFT);
        Label drumLabel = styledLabel("Auto Drum:");
        autoDrumCombo = new ComboBox<>(FXCollections.observableArrayList(DrumStrategies.available()));
        autoDrumCombo.setStyle(comboStyle());
        autoDrumCombo.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(autoDrumCombo, Priority.ALWAYS);
        autoDrumCombo.setCellFactory(lv -> drumStrategyCell());
        autoDrumCombo.setButtonCell(drumStrategyCell());
        autoDrumCombo.setValue(DrumStrategies.NONE);
        autoDrumCombo.setOnAction(e -> {
            if (suppressEvents) return;
            DrumStrategy s = autoDrumCombo.getValue();
            updateEnergyEnabled();
            if (s != null) onAutoDrumChanged.accept(s);
        });
        drumRow.getChildren().addAll(drumLabel, autoDrumCombo);

        // ── Energy row (paired with Auto Drum)
        HBox energyRow = new HBox(10);
        energyRow.setAlignment(Pos.CENTER_LEFT);
        Label energyLabel = styledLabel("Energy:");
        energyCombo = new ComboBox<>(FXCollections.observableArrayList(Energy.values()));
        energyCombo.setStyle(comboStyle());
        energyCombo.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(energyCombo, Priority.ALWAYS);
        energyCombo.setCellFactory(lv -> energyCell());
        energyCombo.setButtonCell(energyCell());
        energyCombo.setValue(Energy.MEDIUM);
        energyCombo.setDisable(true);  // disabled until a non-NONE strategy is picked
        energyCombo.setOnAction(e -> {
            if (suppressEvents) return;
            Energy v = energyCombo.getValue();
            if (v != null) onEnergyChanged.accept(v);
        });
        energyRow.getChildren().addAll(energyLabel, energyCombo);

        // ── Humanizer row
        HBox humanizerRow = new HBox(10);
        humanizerRow.setAlignment(Pos.CENTER_LEFT);
        Label humanizerLabel = styledLabel("Humanize:");
        humanizerCombo = new ComboBox<>(FXCollections.observableArrayList(HumanizerChoice.values()));
        humanizerCombo.setStyle(comboStyle());
        humanizerCombo.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(humanizerCombo, Priority.ALWAYS);
        humanizerCombo.setCellFactory(lv -> humanizerCell());
        humanizerCombo.setButtonCell(humanizerCell());
        humanizerCombo.setValue(HumanizerChoice.OFF);
        humanizerCombo.setDisable(true);  // gated by auto-drum picker
        humanizerCombo.setOnAction(e -> {
            if (suppressEvents) return;
            HumanizerChoice c = humanizerCombo.getValue();
            if (c != null) onHumanizerChanged.accept(c.setup);
        });
        humanizerRow.getChildren().addAll(humanizerLabel, humanizerCombo);

        // ── Transposition (semitone shift, applied last in the
        //    Performance-layer transform chain). Spinner -12..+12;
        //    a derived label shows "Original → Target" so the user
        //    sees both keys side by side without losing the original.
        HBox transposeRow = new HBox(10);
        transposeRow.setAlignment(Pos.CENTER_LEFT);
        Label transposeNameLabel = styledLabel("Transpose:");
        transposeSpinner = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(-12, 12, 0));
        transposeSpinner.setEditable(true);
        transposeSpinner.setPrefWidth(72);
        transposeSpinner.valueProperty().addListener((obs, oldV, newV) -> {
            if (suppressEvents || newV == null) return;
            updateTransposeLabel();
            onTranspositionChanged.accept(newV);
        });
        transposeLabel = new Label("");
        transposeLabel.setStyle("-fx-text-fill: #a6adc8; -fx-font-size: 11;");
        transposeLabel.setWrapText(true);
        HBox.setHgrow(transposeLabel, Priority.ALWAYS);
        transposeRow.getChildren().addAll(transposeNameLabel, transposeSpinner, transposeLabel);

        // ── Pedal mode (tri-state: Source · Auto · Off). Source is gated
        //    by whether the loaded piece declares <pedal/> markings; Auto
        //    is gated by whether the piece has any pitched tracks. Both
        //    can be unavailable simultaneously (only-drums score) — then
        //    the whole row is disabled and effectively pinned to Off.
        Label pedalLabel = styledLabel("Pedal:");
        pedalGroup       = new javafx.scene.control.ToggleGroup();
        pedalSourceRadio = pedalRadio("Source", pedalGroup,
                "Use the engraver's <pedal/> markings from the source.");
        pedalAutoRadio   = pedalRadio("Auto",   pedalGroup,
                "Synthesize pedaling — bar boundaries plus mid-bar bass-note changes.");
        pedalOffRadio    = pedalRadio("Off",    pedalGroup,
                "No sustain pedal — dry playback.");
        pedalAutoRadio.setSelected(true);
        HBox pedalRow = new HBox(10, pedalLabel,
                pedalSourceRadio, pedalAutoRadio, pedalOffRadio);
        pedalRow.setAlignment(Pos.CENTER_LEFT);
        pedalGroup.selectedToggleProperty().addListener((obs, oldT, newT) -> {
            if (suppressEvents || newT == null) return;
            onPedalModeChanged.accept(getPedalMode());
        });

        // ── Status + piece info
        statusLabel = new Label("Select a piece to begin");
        statusLabel.setStyle("-fx-text-fill: #a6adc8; -fx-font-size: 12;");
        pieceInfoLabel = new Label("");
        pieceInfoLabel.setStyle("-fx-text-fill: #a6adc8; -fx-font-size: 12;");
        pieceInfoLabel.setWrapText(true);

        // ── Soundbank section (top of drawer)
        Label sbHeader = styledLabel("Soundbanks:");
        soundbankList.setStyle("-fx-control-inner-background: #1e1e2e; -fx-text-fill: #cdd6f4;"
                + " -fx-background-color: #1e1e2e;");
        soundbankList.setPrefHeight(96);
        soundbankList.setPlaceholder(new Label("Default Java synth"));
        soundbankList.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(File f, boolean empty) {
                super.updateItem(f, empty);
                if (empty || f == null) { setText(null); setGraphic(null); return; }
                Button rm = new Button("×");
                rm.setStyle("-fx-background-color: transparent; -fx-text-fill: #f38ba8;"
                        + " -fx-font-size: 12; -fx-padding: 0 6;");
                rm.setOnAction(ev -> {
                    var items = new ArrayList<>(soundbankList.getItems());
                    items.remove(f);
                    soundbankList.getItems().setAll(items);
                    if (!suppressEvents) onSoundbanksChanged.accept(items);
                });
                Label name = new Label(f.getName());
                name.setStyle("-fx-text-fill: #cdd6f4;");
                Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
                HBox row = new HBox(6, name, sp, rm);
                row.setAlignment(Pos.CENTER_LEFT);
                setText(null);
                setGraphic(row);
                setStyle("-fx-background-color: #1e1e2e;");
            }
        });
        soundbankAddBtn.setStyle("-fx-background-color: #45475a; -fx-text-fill: #cdd6f4; -fx-padding: 4 10;");
        soundbankResetBtn.setStyle("-fx-background-color: #45475a; -fx-text-fill: #cdd6f4; -fx-padding: 4 10;");
        soundbankResetBtn.setOnAction(e -> {
            soundbankList.getItems().clear();
            if (!suppressEvents) onSoundbanksChanged.accept(List.of());
        });
        // Add button is wired by the host (needs the owning Stage for FileChooser).
        HBox sbButtons = new HBox(8, soundbankAddBtn, soundbankResetBtn);
        VBox sbBox = new VBox(4, sbHeader, soundbankList, sbButtons);

        root.getChildren().addAll(sbBox, providerRow, bpmRow, swingRow,
                drumRow, energyRow, humanizerRow, transposeRow, pedalRow,
                statusLabel, pieceInfoLabel);
    }

    Node getRoot() { return root; }

    // ── Listener wiring ─────────────────────────────────────────────

    void setOnProviderSelected(Consumer<PieceContentProvider<?>> handler) {
        this.onProviderSelected = handler == null ? p -> {} : handler;
    }

    /** Fires when the user releases the BPM slider (mouse or keyboard). */
    void setOnBpmReleased(Runnable handler) {
        this.onBpmReleased = handler == null ? () -> {} : handler;
    }

    /** Fires when the swing selector changes; receives the chosen {@link SwingSetup}. */
    void setOnSwingChanged(Consumer<SwingSetup> handler) {
        this.onSwingChanged = handler == null ? s -> {} : handler;
    }

    /** Fires when the auto-drum strategy selector changes. */
    void setOnAutoDrumChanged(Consumer<DrumStrategy> handler) {
        this.onAutoDrumChanged = handler == null ? s -> {} : handler;
    }

    /** Fires when the energy selector changes. */
    void setOnEnergyChanged(Consumer<Energy> handler) {
        this.onEnergyChanged = handler == null ? e -> {} : handler;
    }

    void setEnergy(Energy energy) {
        suppressEvents = true;
        try {
            energyCombo.setValue(energy == null ? Energy.MEDIUM : energy);
        } finally {
            suppressEvents = false;
        }
    }

    Energy getEnergy() { return energyCombo.getValue(); }

    /**
     * Programmatically reset the auto-drum selector (e.g. when a new
     * piece loads, or the host wants to revert to NONE).
     */
    void setAutoDrum(DrumStrategy strategy) {
        suppressEvents = true;
        try {
            autoDrumCombo.setValue(strategy == null ? DrumStrategies.NONE : strategy);
        } finally {
            suppressEvents = false;
        }
    }

    /**
     * Enable/disable the auto-drum picker. Hosts call this with
     * {@code false} when the loaded piece already carries its own drum
     * track, so the picker is greyed out.
     */
    void setAutoDrumEnabled(boolean enabled) {
        autoDrumCombo.setDisable(!enabled);
        if (!enabled) setAutoDrum(DrumStrategies.NONE);
        updateEnergyEnabled();
    }

    /** Energy + humanizer are meaningful only when an active strategy is staged. */
    private void updateEnergyEnabled() {
        boolean active = !autoDrumCombo.isDisabled()
                && autoDrumCombo.getValue() != null
                && autoDrumCombo.getValue() != DrumStrategies.NONE;
        energyCombo.setDisable(!active);
        humanizerCombo.setDisable(!active);
    }

    /** Fires when the humanizer selector changes. */
    void setOnHumanizerChanged(Consumer<HumanizerSetup> handler) {
        this.onHumanizerChanged = handler == null ? h -> {} : handler;
    }

    void setHumanizer(HumanizerSetup setup) {
        suppressEvents = true;
        try {
            humanizerCombo.setValue(HumanizerChoice.from(setup));
        } finally {
            suppressEvents = false;
        }
    }

    HumanizerSetup getHumanizer() {
        var v = humanizerCombo.getValue();
        return v == null ? HumanizerSetup.OFF : v.setup;
    }

    /**
     * Fires when the user changes the transposition spinner. The
     * integer is the semitone shift in [-12, +12]. The host typically
     * forwards it as a {@code TransposeTransform.Params} to the player.
     */
    void setOnTranspositionChanged(Consumer<Integer> handler) {
        this.onTranspositionChanged = handler == null ? n -> {} : handler;
    }

    /**
     * Sync the spinner from external state (e.g. on startup or after
     * loading a piece). Suppresses the change listener so the host's
     * own state isn't echoed back.
     */
    void setTransposition(int semitoneShift) {
        int clamped = Math.max(-12, Math.min(12, semitoneShift));
        suppressEvents = true;
        try {
            transposeSpinner.getValueFactory().setValue(clamped);
            updateTransposeLabel();
        } finally {
            suppressEvents = false;
        }
    }

    int getTransposition() {
        Integer v = transposeSpinner.getValue();
        return v == null ? 0 : v;
    }

    /**
     * Set the original-key context for the show-both transpose label.
     * Pass {@code null} to clear (no piece loaded → no label).
     * Doesn't change the transposition value; only the displayed label.
     */
    void setOriginalKey(KeySignature key) {
        this.originalKey = key;
        updateTransposeLabel();
    }

    /** Fires when the user picks a different pedal mode. */
    void setOnPedalModeChanged(Consumer<PedalMode> handler) {
        this.onPedalModeChanged = handler == null ? m -> {} : handler;
    }

    /**
     * Update which pedal modes are selectable. {@code source} = the
     * loaded piece declared {@code <pedal/>} markings; {@code auto} =
     * the piece has pitched content auto-pedal can act on. Off is
     * always selectable. If the currently-picked mode is no longer
     * available, the selection falls back to the next sensible mode
     * (Source → Auto → Off) and the listener fires with the new mode.
     */
    void setPedalAvailability(boolean source, boolean auto) {
        pedalSourceRadio.setDisable(!source);
        pedalAutoRadio.setDisable(!auto);
        // Off radio is never disabled.
        PedalMode current = getPedalMode();
        PedalMode resolved = current;
        if (current == PedalMode.SOURCE && !source) resolved = auto ? PedalMode.AUTO : PedalMode.OFF;
        if (current == PedalMode.AUTO   && !auto)   resolved = PedalMode.OFF;
        if (resolved != current) setPedalMode(resolved);
    }

    /**
     * Programmatically set the pedal mode without firing the change
     * listener. Use this when applying a fresh piece's natural default
     * or restoring sticky preferences on startup.
     */
    void setPedalMode(PedalMode mode) {
        if (mode == null) mode = PedalMode.OFF;
        suppressEvents = true;
        try {
            switch (mode) {
                case SOURCE -> pedalSourceRadio.setSelected(true);
                case AUTO   -> pedalAutoRadio.setSelected(true);
                case OFF    -> pedalOffRadio.setSelected(true);
            }
        } finally {
            suppressEvents = false;
        }
    }

    PedalMode getPedalMode() {
        if (pedalSourceRadio.isSelected()) return PedalMode.SOURCE;
        if (pedalAutoRadio.isSelected())   return PedalMode.AUTO;
        return PedalMode.OFF;
    }

    /** Tri-state pedal selector. */
    enum PedalMode { SOURCE, AUTO, OFF }

    private static javafx.scene.control.RadioButton pedalRadio(
            String label, javafx.scene.control.ToggleGroup group, String tooltip) {
        var rb = new javafx.scene.control.RadioButton(label);
        rb.setToggleGroup(group);
        rb.setStyle("-fx-text-fill: #cdd6f4; -fx-font-size: 12;");
        rb.setTooltip(new javafx.scene.control.Tooltip(tooltip));
        return rb;
    }

    DrumStrategy getAutoDrum() { return autoDrumCombo.getValue(); }

    /** Wire the Add-soundbank action — host owns the file chooser. */
    void setOnSoundbankAddRequested(Runnable action) {
        soundbankAddBtn.setOnAction(e -> { if (action != null) action.run(); });
    }

    /** Fired when the user removes/clears soundbanks — host persists + restages on player. */
    void setOnSoundbanksChanged(Consumer<List<File>> handler) {
        this.onSoundbanksChanged = handler == null ? files -> {} : handler;
    }

    /** Append a file to the visible list (host calls after file picker). */
    void addSoundbank(File f) {
        if (f == null) return;
        if (soundbankList.getItems().contains(f)) return;
        soundbankList.getItems().add(f);
        if (!suppressEvents) onSoundbanksChanged.accept(List.copyOf(soundbankList.getItems()));
    }

    /** Reset list (host calls on Preferences load). */
    void setSoundbanks(List<File> files) {
        suppressEvents = true;
        try {
            soundbankList.getItems().setAll(files == null ? List.of() : files);
        } finally {
            suppressEvents = false;
        }
    }

    List<File> getSoundbanks() { return List.copyOf(soundbankList.getItems()); }

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
        setProviders(providers, 0);
    }

    void setProviders(List<PieceContentProvider<?>> providers, int initialIndex) {
        suppressEvents = true;
        try {
            providerSelector.setItems(FXCollections.observableArrayList(providers));
            if (!providers.isEmpty()) {
                int idx = (initialIndex >= 0 && initialIndex < providers.size()) ? initialIndex : 0;
                providerSelector.getSelectionModel().select(idx);
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

    private static ListCell<PieceContentProvider<?>> providerCell() {
        return new ListCell<>() {
            @Override protected void updateItem(PieceContentProvider<?> item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.subtitle());
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

    // ── Transposition label helpers ─────────────────────────────────────

    /**
     * Chromatic spelling preferring sharps. Used when the transposition
     * shift is non-negative (going up the keyboard tends to read with
     * sharps). Indexed by pitch class 0..11 with C = 0.
     */
    private static final String[] SHARP_SPELLING =
            {"C", "C♯", "D", "D♯", "E", "F",
             "F♯", "G", "G♯", "A", "A♯", "B"};

    /**
     * Chromatic spelling preferring flats. Used when the transposition
     * shift is negative (going down tends to read with flats).
     */
    private static final String[] FLAT_SPELLING =
            {"C", "D♭", "D", "E♭", "E", "F",
             "G♭", "G", "A♭", "A", "B♭", "B"};

    /**
     * Update the show-both transpose label based on the current spinner
     * value + the {@code originalKey} context. Cases:
     * <ul>
     *   <li>No piece loaded ({@code originalKey == null}) → empty label.</li>
     *   <li>Shift = 0 → "(playing in &lt;original&gt;)".</li>
     *   <li>Shift &ne; 0 → "&lt;original&gt; → &lt;target&gt; (&plusmn;N)".</li>
     * </ul>
     */
    private void updateTransposeLabel() {
        if (originalKey == null) {
            transposeLabel.setText("");
            return;
        }
        Integer raw = transposeSpinner.getValue();
        int shift = raw == null ? 0 : raw;
        String original = formatKey(originalKey.tonic(), originalKey.accidental(),
                                     originalKey.mode());
        if (shift == 0) {
            transposeLabel.setText("(playing in " + original + ")");
            return;
        }
        String target = transposedKeyName(originalKey, shift);
        String sign = shift > 0 ? "+" + shift : Integer.toString(shift);
        transposeLabel.setText(original + " → " + target + " (" + sign + ")");
    }

    /**
     * Format a key signature as a human label, e.g. "A minor" or
     * "B&flat; major" or just "C" when the mode is unknown.
     */
    private static String formatKey(NoteName tonic, Accidental acc, Mode mode) {
        String tonicStr = spellTonic(tonic, acc);
        return switch (mode) {
            case NONE -> tonicStr;
            case MAJOR -> tonicStr + " major";
            case MINOR -> tonicStr + " minor";
            default -> tonicStr + " " + mode.name().toLowerCase();
        };
    }

    /**
     * Compute the transposed key name. Mode is preserved (per the
     * scale-only-not-mode constraint of the transposition feature).
     * Spelling prefers sharps for upward shifts, flats for downward.
     */
    private static String transposedKeyName(KeySignature source, int shift) {
        int srcPc = pitchClassOf(source.tonic(), source.accidental());
        int tgtPc = Math.floorMod(srcPc + shift, 12);
        String[] table = shift < 0 ? FLAT_SPELLING : SHARP_SPELLING;
        String tonicStr = table[tgtPc];
        return switch (source.mode()) {
            case NONE -> tonicStr;
            case MAJOR -> tonicStr + " major";
            case MINOR -> tonicStr + " minor";
            default -> tonicStr + " " + source.mode().name().toLowerCase();
        };
    }

    private static String spellTonic(NoteName tonic, Accidental acc) {
        return switch (acc) {
            case DOUBLE_FLAT  -> tonic.name() + "𝄫";   // 𝄫
            case FLAT         -> tonic.name() + "♭";          // ♭
            case NATURAL      -> tonic.name();
            case SHARP        -> tonic.name() + "♯";          // ♯
            case DOUBLE_SHARP -> tonic.name() + "𝄪";   // 𝄪
        };
    }

    /**
     * Map a (letter, accidental) to a chromatic pitch class 0..11.
     * C = 0, C&sharp; = 1, D = 2, etc. Wraps modulo 12 so e.g. C&flat;
     * yields 11 (B), F&sharp; yields 6, etc.
     */
    private static int pitchClassOf(NoteName tonic, Accidental acc) {
        int base = switch (tonic) {
            case C -> 0;
            case D -> 2;
            case E -> 4;
            case F -> 5;
            case G -> 7;
            case A -> 9;
            case B -> 11;
        };
        int off = switch (acc) {
            case DOUBLE_FLAT  -> -2;
            case FLAT         -> -1;
            case NATURAL      ->  0;
            case SHARP        ->  1;
            case DOUBLE_SHARP ->  2;
        };
        return Math.floorMod(base + off, 12);
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

    private static ListCell<Energy> energyCell() {
        return new ListCell<>() {
            @Override protected void updateItem(Energy item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : labelFor(item));
                setStyle("-fx-text-fill: #cdd6f4; -fx-background-color: #313244;");
            }
            private String labelFor(Energy e) {
                return switch (e) {
                    case LOW    -> "Low";
                    case MEDIUM -> "Medium";
                    case HIGH   -> "High";
                };
            }
        };
    }

    private static ListCell<HumanizerChoice> humanizerCell() {
        return new ListCell<>() {
            @Override protected void updateItem(HumanizerChoice item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.label);
                setStyle("-fx-text-fill: #cdd6f4; -fx-background-color: #313244;");
            }
        };
    }

    /** Discrete humaniser choices surfaced to the UI. */
    enum HumanizerChoice {
        OFF   ("Off",    HumanizerSetup.OFF),
        LIGHT ("Light",  HumanizerSetup.LIGHT),
        MEDIUM("Medium", HumanizerSetup.MEDIUM),
        LOOSE ("Loose",  HumanizerSetup.LOOSE);

        final String label;
        final HumanizerSetup setup;
        HumanizerChoice(String label, HumanizerSetup setup) {
            this.label = label; this.setup = setup;
        }
        static HumanizerChoice from(HumanizerSetup setup) {
            if (setup == null || setup.isOff()) return OFF;
            int j = setup.maxJitterMs();
            if (j <= 6)  return LIGHT;
            if (j <= 12) return MEDIUM;
            return LOOSE;
        }
    }

    private static ListCell<DrumStrategy> drumStrategyCell() {
        return new ListCell<>() {
            @Override protected void updateItem(DrumStrategy item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.displayName());
                setStyle("-fx-text-fill: #cdd6f4; -fx-background-color: #313244;");
                if (item != null && !empty) {
                    setTooltip(new javafx.scene.control.Tooltip(item.description()));
                }
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
