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
import music.notation.pitch.Accidental;
import music.notation.pitch.NoteName;
import music.notation.play.MidiPlayer;
import music.notation.songs.PieceLibrary;
import music.notation.structure.*;

import java.io.File;
import java.util.*;

public class NotationApp extends Application {

    private final MidiPlayer player = new MidiPlayer();
    private Piece currentPiece;

    private ComboBox<String> pieceSelector;
    private ComboBox<PieceContentProvider<?>> providerSelector;
    private VBox trackControlsBox;
    private Button playButton;
    private Button pauseButton;
    private Button stopButton;
    private Button exportButton;
    private Label statusLabel;
    private Label pieceInfoLabel;
    private PitchScroll pitchScroll;
    private ScrollPane pianoRollScrollPane;
    private KeyboardDisplay keyboardDisplay;
    private GuitarTabDisplay guitarTabDisplay;

    // Lazy bottom-tab state
    private Pane kbHolder;
    private Pane gtHolder;
    private Tab keyboardTab;
    private Tab guitarTab;
    private TabPane bottomTabs;
    private PitchScrollData currentScrollData;
    private boolean animating;
    private final Set<String> disabledVisualizerTracks = new HashSet<>();

    // Guitar filter state (persists across recreations)
    private int guitarMinFret = 0;
    private int guitarMaxFret = 15;
    private final boolean[] guitarStringEnabled = new boolean[GuitarTabDisplay.STRING_COUNT];

    // Scale adjustment controls
    private ComboBox<String> rootNoteCombo;
    private ComboBox<Mode> modeCombo;
    private Piece originalPiece; // piece before transposition / tempo override

    // Tempo adjustment controls
    private Slider bpmSlider;
    private Label bpmValueLabel;

    /** Per-track list of instrument combos (outer = track index, inner = instrument slots). */
    private final List<List<ComboBox<Instrument>>> trackInstrumentCombos = new ArrayList<>();
    /** Per-track list of volume sliders, parallel to trackInstrumentCombos. */
    private final List<List<Slider>> trackVolumeSliders = new ArrayList<>();

    @Override
    public void start(Stage stage) {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #1e1e2e;");

        // === Playback controls (icon-only; placed inside the Library panel below) ===
        playButton   = iconButton("▶", "Play (Space)");
        pauseButton  = iconButton("⏸", "Pause");
        stopButton   = iconButton("⏹", "Stop");
        exportButton = iconButton("⬇", "Export MIDI…");
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

        HBox providerRow = new HBox(10);
        providerRow.setAlignment(Pos.CENTER_LEFT);
        Label providerLabel = styledLabel("Arrangement:");
        providerSelector = new ComboBox<>();
        providerSelector.setStyle(comboStyle());
        providerSelector.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(providerSelector, Priority.ALWAYS);
        providerSelector.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(PieceContentProvider<?> item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.subtitle());
                setStyle("-fx-text-fill: #cdd6f4; -fx-background-color: #313244;");
            }
        });
        providerSelector.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(PieceContentProvider<?> item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.subtitle());
                setStyle("-fx-text-fill: #cdd6f4; -fx-background-color: #313244;");
            }
        });
        providerSelector.setOnAction(e -> onProviderSelected());
        providerRow.getChildren().addAll(providerLabel, providerSelector);

        // -- Scale adjustment row --
        HBox scaleRow = new HBox(10);
        scaleRow.setAlignment(Pos.CENTER_LEFT);
        Label scaleLabel = styledLabel("Scale:");

        // Root note: C, C#, D, D#, E, F, F#, G, G#, A, A#, B
        rootNoteCombo = new ComboBox<>(FXCollections.observableArrayList(
                "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"));
        rootNoteCombo.setStyle(comboStyle());
        styleComboBoxCells(rootNoteCombo);
        rootNoteCombo.setOnAction(e -> onScaleChanged());

        modeCombo = new ComboBox<>(FXCollections.observableArrayList(Mode.values()));
        modeCombo.setStyle(comboStyle());
        modeCombo.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Mode item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : modeName(item));
                setStyle("-fx-text-fill: #cdd6f4; -fx-background-color: #313244;");
            }
        });
        modeCombo.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(Mode item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : modeName(item));
                setStyle("-fx-text-fill: #cdd6f4; -fx-background-color: #313244;");
            }
        });
        modeCombo.setOnAction(e -> onScaleChanged());

        scaleRow.getChildren().addAll(scaleLabel, rootNoteCombo, modeCombo);

        // -- BPM adjustment row --
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

        // Live BPM label update; rebuild piece only when user releases the slider
        bpmSlider.valueProperty().addListener((obs, oldV, newV) ->
                bpmValueLabel.setText(((int) newV.doubleValue()) + " BPM"));
        bpmSlider.valueChangingProperty().addListener((obs, wasChanging, isChanging) -> {
            if (!isChanging) onBpmChanged();
        });
        // Fallback: keyboard-driven changes (arrow keys) don't flip valueChanging
        bpmSlider.setOnKeyReleased(e -> onBpmChanged());

        bpmRow.getChildren().addAll(bpmLabel, bpmSlider, bpmValueLabel);

        pieceInfoLabel = new Label("");
        pieceInfoLabel.setStyle("-fx-text-fill: #a6adc8; -fx-font-size: 12;");
        pieceInfoLabel.setWrapText(true);

        // -- Playback row: icon-only transport + export, equal-width flex --
        HBox playbackRow = new HBox(8);
        playbackRow.setAlignment(Pos.CENTER_LEFT);
        for (Button btn : new Button[] {playButton, pauseButton, stopButton, exportButton}) {
            HBox.setHgrow(btn, Priority.ALWAYS);
            btn.setMaxWidth(Double.MAX_VALUE);
        }
        playbackRow.getChildren().addAll(playButton, pauseButton, stopButton, exportButton);

        libraryContent.getChildren().addAll(
                selectorRow, providerRow, scaleRow, bpmRow, playbackRow, statusLabel, pieceInfoLabel);

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
                    if (sp == null || sp.getViewportBounds() == null) return;
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
        pianoRollScrollPane = new ScrollPane(canvasHolder);
        scrollPaneRef[0] = pianoRollScrollPane;
        pianoRollScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        pianoRollScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        pianoRollScrollPane.setStyle("-fx-background: #1e1e2e; -fx-background-color: #1e1e2e;");

        // JavaFX Canvas backed by GPU textures — cap to avoid allocation failure
        final double MAX_CANVAS_DIM = 8192;
        final Runnable updateCanvasSize = () -> {
            if (pianoRollScrollPane.getViewportBounds() == null) return;
            final double vpw = pianoRollScrollPane.getViewportBounds().getWidth();
            final double effectiveWidth = Math.min(Math.max(vpw, pitchScroll.getMinContentWidth()), MAX_CANVAS_DIM);
            pitchScroll.setWidth(effectiveWidth);
            canvasHolder.setMinWidth(effectiveWidth);
            canvasHolder.setPrefWidth(effectiveWidth);

            final double vpH = pianoRollScrollPane.getViewportBounds().getHeight();
            final double contentH = pitchScroll.computeContentHeight();
            final double effectiveH = Math.min(Math.max(contentH, vpH), MAX_CANVAS_DIM);
            pitchScroll.setHeight(effectiveH);
            canvasHolder.setMinHeight(effectiveH);
            canvasHolder.setPrefHeight(effectiveH);
        };
        pianoRollScrollPane.viewportBoundsProperty().addListener((obs, o, n) -> updateCanvasSize.run());

        // Zoom slider: controls minimum pixels per quarter note (1–20, default 2)
        final Label zoomLabel = styledLabel("Zoom:");
        final Slider zoomSlider = new Slider(2, 80, 4);
        zoomSlider.setMaxWidth(120);
        zoomSlider.valueProperty().addListener((obs, o, n) -> {
            pitchScroll.setMinQuarterPx(n.doubleValue());
            updateCanvasSize.run();
        });

        final HBox zoomBar = new HBox(8, zoomLabel, zoomSlider);
        zoomBar.setAlignment(Pos.CENTER_RIGHT);
        zoomBar.setPadding(new Insets(2, 8, 2, 8));
        zoomBar.setStyle("-fx-background-color: #181825;");

        final VBox pianoRollBox = new VBox(pianoRollScrollPane, zoomBar);
        VBox.setVgrow(pianoRollScrollPane, Priority.ALWAYS);

        // -- Bottom: Keyboard holder (canvas created lazily) --
        kbHolder = new Pane();
        kbHolder.setStyle("-fx-background-color: #1e1e2e;");
        VBox keyboardBox = new VBox(kbHolder);
        keyboardBox.setStyle("-fx-background-color: #1e1e2e;");
        VBox.setVgrow(kbHolder, Priority.ALWAYS);

        // -- Bottom: Guitar tab holder (canvas created lazily) --
        gtHolder = new Pane();
        gtHolder.setStyle("-fx-background-color: #1e1e2e;");
        Arrays.fill(guitarStringEnabled, true);

        // Guitar filter controls
        HBox gtFilters = new HBox(12);
        gtFilters.setAlignment(Pos.CENTER_LEFT);
        gtFilters.setPadding(new Insets(4, 8, 4, 8));
        gtFilters.setStyle("-fx-background-color: #181825;");

        Label fretLabel = styledLabel("Frets:");
        Spinner<Integer> minFretSpinner = new Spinner<>(0, GuitarTabDisplay.MAX_FRETS, 0);
        minFretSpinner.setPrefWidth(64);
        minFretSpinner.setStyle("-fx-background-color: #313244; -fx-font-size: 11;");
        Label fretDash = styledLabel("–");
        Spinner<Integer> maxFretSpinner = new Spinner<>(0, GuitarTabDisplay.MAX_FRETS, 15);
        maxFretSpinner.setPrefWidth(64);
        maxFretSpinner.setStyle("-fx-background-color: #313244; -fx-font-size: 11;");
        minFretSpinner.valueProperty().addListener((obs, o, n) -> {
            guitarMinFret = n;
            if (guitarTabDisplay != null) guitarTabDisplay.setFretRange(n, maxFretSpinner.getValue());
        });
        maxFretSpinner.valueProperty().addListener((obs, o, n) -> {
            guitarMaxFret = n;
            if (guitarTabDisplay != null) guitarTabDisplay.setFretRange(minFretSpinner.getValue(), n);
        });
        gtFilters.getChildren().addAll(fretLabel, minFretSpinner, fretDash, maxFretSpinner);

        Label strLabel = styledLabel("Strings:");
        gtFilters.getChildren().add(strLabel);
        for (int s = 0; s < GuitarTabDisplay.STRING_COUNT; s++) {
            int si = s;
            CheckBox cb = new CheckBox(GuitarTabDisplay.STRING_NAMES[s]);
            cb.setSelected(true);
            cb.setStyle("-fx-text-fill: #a6adc8; -fx-font-size: 11;");
            cb.selectedProperty().addListener((obs, o, n) -> {
                guitarStringEnabled[si] = n;
                if (guitarTabDisplay != null) guitarTabDisplay.setStringEnabled(si, n);
            });
            gtFilters.getChildren().add(cb);
        }

        VBox guitarBox = new VBox(gtHolder, gtFilters);
        guitarBox.setStyle("-fx-background-color: #1e1e2e;");
        VBox.setVgrow(gtHolder, Priority.ALWAYS);

        // === Assemble: Top (Library | Tracks) / Middle (Piano Roll) / Bottom (Keyboard | Guitar) ===
        TabPane topLeftTabs = createTabPane(tab("Library", libraryContent));
        TabPane topRightTabs = createTabPane(tab("Tracks", trackScroll));
        TabPane middleTabs = createTabPane(tab("Piano Roll", pianoRollBox));
        keyboardTab = tab("Keyboard", keyboardBox);
        guitarTab = tab("Guitar", guitarBox);
        bottomTabs = createTabPane(keyboardTab, guitarTab);

        // Lazy: create display for initially visible tab, destroy on switch
        bottomTabs.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            tearDownBottomDisplay(oldTab);
            setUpBottomDisplay(newTab);
        });
        // Create the initial display (keyboard is first tab)
        setUpBottomDisplay(keyboardTab);

        SplitPane topSplit = new SplitPane(topLeftTabs, topRightTabs);
        topSplit.setDividerPositions(0.45);
        styleSplitPane(topSplit);

        SplitPane mainSplit = new SplitPane(topSplit, middleTabs, bottomTabs);
        mainSplit.setOrientation(Orientation.VERTICAL);
        mainSplit.setDividerPositions(0.25, 0.82);
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
        final String selected = pieceSelector.getValue();
        if (selected == null) return;

        onStop();

        // Populate provider selector
        final List<PieceContentProvider<?>> providers = PieceLibrary.providers(selected);
        providerSelector.setItems(FXCollections.observableArrayList(providers));
        if (!providers.isEmpty()) {
            providerSelector.getSelectionModel().selectFirst();
        }
        providerSelector.setVisible(providers.size() > 1);
        providerSelector.setManaged(providers.size() > 1);

        loadFromSelectedProvider();
    }

    private void onProviderSelected() {
        if (providerSelector.getValue() == null) return;
        onStop();
        loadFromSelectedProvider();
    }

    // ── Scale & tempo adjustments ────────────────────────────────────

    private void onScaleChanged() { rebuildPiece(); }

    private void onBpmChanged() { rebuildPiece(); }

    /**
     * Apply scale transposition and tempo override to {@link #originalPiece},
     * assigning the result to {@link #currentPiece} and reloading the UI.
     */
    private void rebuildPiece() {
        if (originalPiece == null || rootNoteCombo.getValue() == null || modeCombo.getValue() == null) return;
        onStop();

        Piece p = originalPiece;

        // --- Scale transposition ---
        KeySignature targetKey = parseKeyFromUI();
        KeySignature sourceKey = originalPiece.key();
        boolean sameKey = targetKey.tonic() == sourceKey.tonic()
                && targetKey.accidental() == sourceKey.accidental()
                && targetKey.mode() == sourceKey.mode();
        if (!sameKey) {
            p = transposePiece(p, sourceKey, targetKey);
        }

        // --- Tempo override ---
        int targetBpm = (int) Math.round(bpmSlider.getValue());
        if (targetBpm != originalPiece.tempo().bpm()) {
            p = new Piece(p.title(), p.composer(), p.key(), p.timeSig(),
                    new Tempo(targetBpm, p.tempo().beatUnit()),
                    p.tracks());
        }

        currentPiece = p;
        loadPiece();
    }

    /**
     * Phase 4d transitional: transposition is currently a no-op. The
     * legacy {@code ShiftedPhrase} wrapper went away with the legacy
     * phrase family; transposition will be reimplemented as a Bar-level
     * pitch transform once the bar abstract-note shape stabilises.
     */
    private static Piece transposePiece(Piece source, KeySignature sourceKey, KeySignature targetKey) {
        return source;
    }

    private static Instrument defaultInstrumentOf(Track track) {
        return switch (track) {
            case MelodicTrack mt -> mt.defaultInstrument();
            case DrumTrack dt -> Instrument.DRUM_KIT;
        };
    }

    // ── Key label helpers ────────────────────────────────────────────

    private KeySignature parseKeyFromUI() {
        String rootLabel = rootNoteCombo.getValue();
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

    private static String keySignatureToRootLabel(KeySignature key) {
        String root = key.tonic().name();
        if (key.accidental() == Accidental.SHARP) root += "#";
        else if (key.accidental() == Accidental.FLAT) {
            // Map flats to their sharp equivalents for the UI combo
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

    private void loadFromSelectedProvider() {
        final PieceContentProvider<?> provider = providerSelector.getValue();
        if (provider == null) return;

        originalPiece = provider.create();

        // Initialize scale selectors to piece's original key (suppress handler)
        final KeySignature origKey = originalPiece.key();
        rootNoteCombo.setOnAction(null);
        modeCombo.setOnAction(null);
        rootNoteCombo.setValue(keySignatureToRootLabel(origKey));
        modeCombo.setValue(origKey.mode());
        rootNoteCombo.setOnAction(e -> onScaleChanged());
        modeCombo.setOnAction(e -> onScaleChanged());

        // Initialize BPM slider to piece's original tempo (suppress handler)
        final int origBpm = originalPiece.tempo().bpm();
        bpmSlider.setValue(origBpm);
        bpmValueLabel.setText(origBpm + " BPM");

        currentPiece = originalPiece;
        loadPiece();
    }

    private void loadPiece() {
        pieceInfoLabel.setText(String.format("%s by %s\n%s  |  %d/%d  |  %d BPM",
                currentPiece.title(), currentPiece.composer(),
                currentPiece.key().tonic() + " " + currentPiece.key().mode(),
                currentPiece.timeSig().beats(), currentPiece.timeSig().beatValue(),
                currentPiece.tempo().bpm()));

        currentScrollData = PitchScrollData.fromPiece(currentPiece);
        disabledVisualizerTracks.clear();
        pitchScroll.load(currentScrollData);
        if (keyboardDisplay != null) keyboardDisplay.load(currentScrollData);
        if (guitarTabDisplay != null) guitarTabDisplay.load(currentScrollData);
        buildTrackControls();
        playButton.setDisable(false);
        exportButton.setDisable(false);
        statusLabel.setText("Ready");
    }

    // ── Track controls with dynamic instrument slots ──────────────────────

    private void buildTrackControls() {
        trackControlsBox.getChildren().clear();
        trackInstrumentCombos.clear();
        trackVolumeSliders.clear();

        List<Track> tracks = currentPiece.tracks();
        for (int i = 0; i < tracks.size(); i++) {
            Track track = tracks.get(i);
            int trackIndex = i;
            // Lyrics tracks no longer exist post-4d; every track is audio.
            boolean isAudio = true;

            // Per-track container: label + instrument rows + add button
            VBox trackBox = new VBox(4);
            trackBox.setPadding(new Insets(4, 0, 4, 0));

            // Track header row: name + "+" button
            HBox headerRow = new HBox(8);
            headerRow.setAlignment(Pos.CENTER_LEFT);
            Label trackLabel = styledLabel(track.name());
            trackLabel.setMinWidth(80);
            trackLabel.setStyle("-fx-text-fill: #cdd6f4; -fx-font-size: 13; -fx-font-weight: bold;");

            headerRow.getChildren().add(trackLabel);

            // Keyboard/guitar toggle checkbox (audio tracks only)
            if (isAudio) {
                CheckBox kbToggle = new CheckBox("KB");
                kbToggle.setSelected(true);
                kbToggle.setStyle("-fx-text-fill: #a6adc8; -fx-font-size: 11;");
                String trackKey = track.name();
                kbToggle.selectedProperty().addListener((obs, o, n) -> {
                    if (n) disabledVisualizerTracks.remove(trackKey);
                    else   disabledVisualizerTracks.add(trackKey);
                    if (keyboardDisplay != null) keyboardDisplay.setTrackEnabled(trackKey, n);
                    if (guitarTabDisplay != null) guitarTabDisplay.setTrackEnabled(trackKey, n);
                });
                headerRow.getChildren().add(kbToggle);
            }

            Button addBtn = smallButton("+");
            addBtn.setOnAction(e -> addInstrumentSlot(trackIndex, trackBox, defaultInstrumentOf(track)));

            headerRow.getChildren().add(addBtn);
            trackBox.getChildren().add(headerRow);

            // Instrument combo list and volume slider list for this track
            var combos = new ArrayList<ComboBox<Instrument>>();
            var sliders = new ArrayList<Slider>();
            trackInstrumentCombos.add(combos);
            trackVolumeSliders.add(sliders);

            // Start with one slot showing the default instrument
            addInstrumentRow(trackIndex, trackBox, combos, sliders, defaultInstrumentOf(track), false);

            // Separator
            var sep = new Separator();
            sep.setStyle("-fx-background-color: #313244;");

            trackControlsBox.getChildren().addAll(trackBox, sep);
        }
    }

    /** Add a new instrument slot to a track (called by "+" button). */
    private void addInstrumentSlot(int trackIndex, VBox trackBox, Instrument defaultValue) {
        var combos = trackInstrumentCombos.get(trackIndex);
        var sliders = trackVolumeSliders.get(trackIndex);
        addInstrumentRow(trackIndex, trackBox, combos, sliders, defaultValue, true);
    }

    /** Create one instrument combo row with volume slider inside a track box. */
    private void addInstrumentRow(int trackIndex, VBox trackBox,
                                  List<ComboBox<Instrument>> combos,
                                  List<Slider> sliders,
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

        // Volume slider (0–127, default 100)
        Slider volSlider = new Slider(0, 127, 100);
        volSlider.setPrefWidth(80);
        volSlider.setBlockIncrement(1);
        volSlider.setStyle("-fx-control-inner-background: #313244;");

        Label volLabel = styledLabel(String.valueOf((int) volSlider.getValue()));
        volLabel.setMinWidth(28);
        volLabel.setStyle("-fx-text-fill: #a6adc8; -fx-font-size: 11;");
        volSlider.valueProperty().addListener((obs, oldVal, newVal) ->
                volLabel.setText(String.valueOf(newVal.intValue())));

        combos.add(combo);
        sliders.add(volSlider);
        row.getChildren().addAll(combo, volSlider, volLabel);

        if (removable) {
            Button removeBtn = smallButton("\u2212"); // minus sign
            removeBtn.setOnAction(e -> {
                combos.remove(combo);
                sliders.remove(volSlider);
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
        var volumes = collectVolumeAssignments();

        playButton.setDisable(true);
        pauseButton.setDisable(false);
        pauseButton.setText("Pause");
        stopButton.setDisable(false);
        pieceSelector.setDisable(true);
        statusLabel.setText("Playing...");
        pianoRollScrollPane.setHvalue(0);

        Thread playThread = new Thread(() -> {
            try {
                player.start(currentPiece, assignments, volumes);
                Platform.runLater(() -> {
                    animating = true;
                    pitchScroll.startAnimation(player::getTickPosition);
                    startActiveBottomDisplay();
                });
                while (player.isPlaying() || player.isPaused()) {
                    Thread.sleep(100);
                }
                Thread.sleep(300);
            } catch (Exception ex) {
                ex.printStackTrace();
            } finally {
                player.stop();
                Platform.runLater(() -> {
                    animating = false;
                    pitchScroll.stopAnimation();
                    stopActiveBottomDisplay();
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
            animating = true;
            pitchScroll.startAnimation(player::getTickPosition);
            startActiveBottomDisplay();
        } else {
            player.pause();
            pauseButton.setText("Resume");
            statusLabel.setText("Paused");
            animating = false;
            pitchScroll.stopAnimation();
            stopActiveBottomDisplay();
        }
    }

    private void onStop() {
        animating = false;
        pitchScroll.stopAnimation();
        stopActiveBottomDisplay();
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

        // Collect instrument and volume assignments from the UI
        var assignments = collectInstrumentAssignments();
        var volumes = collectVolumeAssignments();

        try {
            MidiPlayer.exportMidi(currentPiece, assignments, volumes, file);
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
                instruments.add(defaultInstrumentOf(currentPiece.tracks().get(t)));
            }
            assignments.add(instruments);
        }
        return assignments;
    }

    /** Collect the current volume settings from all track sliders. */
    private List<List<Integer>> collectVolumeAssignments() {
        var volumes = new ArrayList<List<Integer>>();
        for (int t = 0; t < trackVolumeSliders.size(); t++) {
            var sliders = trackVolumeSliders.get(t);
            var trackVols = new ArrayList<Integer>();
            for (var slider : sliders) {
                trackVols.add((int) slider.getValue());
            }
            if (trackVols.isEmpty()) {
                trackVols.add(100);
            }
            volumes.add(trackVols);
        }
        return volumes;
    }

    // ── Lazy bottom-display lifecycle ───────────────────────────────────

    private void setUpBottomDisplay(Tab tab) {
        if (tab == keyboardTab) {
            keyboardDisplay = new KeyboardDisplay();
            keyboardDisplay.widthProperty().bind(kbHolder.widthProperty());
            keyboardDisplay.heightProperty().bind(kbHolder.heightProperty());
            kbHolder.getChildren().add(keyboardDisplay);
            if (currentScrollData != null) {
                keyboardDisplay.load(currentScrollData);
                for (String tk : disabledVisualizerTracks) {
                    keyboardDisplay.setTrackEnabled(tk, false);
                }
            }
            if (animating) keyboardDisplay.startAnimation(player::getTickPosition);
        } else if (tab == guitarTab) {
            guitarTabDisplay = new GuitarTabDisplay();
            guitarTabDisplay.widthProperty().bind(gtHolder.widthProperty());
            guitarTabDisplay.heightProperty().bind(gtHolder.heightProperty());
            gtHolder.getChildren().add(guitarTabDisplay);
            guitarTabDisplay.setFretRange(guitarMinFret, guitarMaxFret);
            for (int s = 0; s < GuitarTabDisplay.STRING_COUNT; s++) {
                guitarTabDisplay.setStringEnabled(s, guitarStringEnabled[s]);
            }
            if (currentScrollData != null) {
                guitarTabDisplay.load(currentScrollData);
                for (String tk : disabledVisualizerTracks) {
                    guitarTabDisplay.setTrackEnabled(tk, false);
                }
            }
            if (animating) guitarTabDisplay.startAnimation(player::getTickPosition);
        }
    }

    private void tearDownBottomDisplay(Tab tab) {
        if (tab == keyboardTab && keyboardDisplay != null) {
            keyboardDisplay.stopAnimation();
            kbHolder.getChildren().clear();
            keyboardDisplay = null;
        } else if (tab == guitarTab && guitarTabDisplay != null) {
            guitarTabDisplay.stopAnimation();
            gtHolder.getChildren().clear();
            guitarTabDisplay = null;
        }
    }

    private void startActiveBottomDisplay() {
        if (keyboardDisplay != null) keyboardDisplay.startAnimation(player::getTickPosition);
        if (guitarTabDisplay != null) guitarTabDisplay.startAnimation(player::getTickPosition);
    }

    private void stopActiveBottomDisplay() {
        if (keyboardDisplay != null) keyboardDisplay.stopAnimation();
        if (guitarTabDisplay != null) guitarTabDisplay.stopAnimation();
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

    /**
     * Icon-only playback button. Uses a Unicode glyph as the button "icon" —
     * no icon font dependency needed. Tooltip carries the textual meaning so
     * keyboard/screen-reader users still get the action name.
     */
    private static Button iconButton(String icon, String tooltipText) {
        Button b = new Button(icon);
        final String base = "-fx-background-color: #45475a; -fx-text-fill: #cdd6f4; "
                + "-fx-font-size: 18; -fx-padding: 6 10; -fx-background-radius: 6;";
        final String hover = "-fx-background-color: #585b70; -fx-text-fill: #cdd6f4; "
                + "-fx-font-size: 18; -fx-padding: 6 10; -fx-background-radius: 6;";
        b.setStyle(base);
        b.setOnMouseEntered(e -> b.setStyle(hover));
        b.setOnMouseExited(e -> b.setStyle(base));
        Tooltip tip = new Tooltip(tooltipText);
        tip.setShowDelay(javafx.util.Duration.millis(300));
        tip.setStyle("-fx-font-size: 12; -fx-background-color: #313244; -fx-text-fill: #cdd6f4;");
        Tooltip.install(b, tip);
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
