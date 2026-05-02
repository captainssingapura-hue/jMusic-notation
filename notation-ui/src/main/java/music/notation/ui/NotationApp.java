package music.notation.ui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import music.notation.event.Instrument;
import music.notation.play.ChannelSetup;
import music.notation.play.MidiPlayer;
import music.notation.play.SoundbankSetup;
import music.notation.play.SwingSetup;
import music.notation.play.TempoSetup;
import music.notation.songs.PieceLibrary;
import music.notation.structure.*;

import java.io.File;
import java.util.*;
import java.util.function.Consumer;

/**
 * Composition root. Wires composable panels (TransportBar,
 * ControlsPanel, PitchScroll, bottom Keyboard/Guitar tabs) into the
 * scene graph and routes their events into model state on
 * {@link MidiPlayer}.
 */
public class NotationApp extends Application {

    private final MidiPlayer player = new MidiPlayer();
    private Piece currentPiece;
    private Piece originalPiece;
    private String currentPieceTitle;
    /** Set when a session-only MIDI import is loaded; mutually exclusive with currentPiece. */
    private music.notation.performance.MidiImport currentImport;
    /** Currently-staged swing (used at next play, and as the "previous" value for cancel-revert). */
    private SwingSetup currentSwing = SwingSetup.OFF;

    private TransportBar transportBar;
    private ControlsPanel controls;

    private PitchScroll pitchScroll;
    private KeyboardDisplay keyboardDisplay;
    private GuitarTabDisplay guitarTabDisplay;

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

    /** Per-track selected instrument (single-instrument-per-track post-Phase-5). */
    private final List<Instrument> selectedInstruments = new ArrayList<>();
    /** Per-track selected volume level 0–127. */
    private final List<Integer> selectedVolumes = new ArrayList<>();
    /** Per-track selected pan 0–127 (64 = center). */
    private final List<Integer> selectedPans = new ArrayList<>();
    /** Per-track instrument button (label updated when selection changes). */
    private final List<Button> instrumentButtons = new ArrayList<>();
    /** The host stage — passed to InstrumentPickerDialog as modal owner. */
    private Stage hostStage;

    @Override
    public void start(Stage stage) {
        this.hostStage = stage;
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #1e1e2e;");

        // ── Composable panels ───────────────────────────────────────
        transportBar = new TransportBar();
        controls = new ControlsPanel();
        // Note: ControlsPanel is built but NOT mounted in the scene graph
        // for Phase 7.0. Phase 7.1 will mount it inside a right-edge
        // drawer toggled by the gear button.

        transportBar.playButton().setOnAction(e -> onPlayPause());
        transportBar.stopButton().setOnAction(e -> onStop());
        transportBar.exportButton().setOnAction(e -> onExport(stage));
        transportBar.pieceButton().setOnAction(e -> openPiecePicker());
        // Drawer wired below once the centre StackPane exists.

        controls.setOnProviderSelected(p -> onProviderSelected());
        controls.setOnScaleChanged(this::rebuildPiece);
        controls.setOnBpmReleased(this::onBpmReleased);
        controls.setOnSwingChanged(this::onSwingChanged);
        controls.setOnSoundbankAddRequested(() -> onAddSoundbank(stage));
        controls.setOnSoundbanksChanged(this::onSoundbanksChanged);
        loadPersistedSoundbanks();

        // ── Piano roll (centre) ─────────────────────────────────────
        final double CONTROL_PANEL_WIDTH = 180;
        pitchScroll = new PitchScroll(
                tick -> player.setTickPosition(tick),
                this::onPianoRollCursorMoved,
                this::buildTrackRowControlPanel,
                CONTROL_PANEL_WIDTH
        );
        // PitchScroll is self-contained (its own h/v scrollbars).
        // Wire the host-viewport-changed signal so it can recompute on resize.
        pitchScroll.widthProperty().addListener((obs, o, n) -> pitchScroll.hostViewportChanged());
        pitchScroll.heightProperty().addListener((obs, o, n) -> pitchScroll.hostViewportChanged());

        // Cursor-readout strip on top of the piano roll.
        Button skipStartBtn = new Button("⏮");
        skipStartBtn.setTooltip(new Tooltip("Seek to start"));
        skipStartBtn.setStyle("-fx-background-color: #313244; -fx-text-fill: #cdd6f4; -fx-font-size: 11;");
        skipStartBtn.setOnAction(e -> {
            pitchScroll.seekTo(0);
            pitchScroll.setLaneScrollHvalue(0);
        });
        Label cursorReadout = new Label("Bar – · Beat –");
        cursorReadout.setStyle("-fx-text-fill: #cdd6f4; -fx-font-size: 12; -fx-font-family: monospace;");
        pitchScroll.setOnCursorTick(tick -> {
            if (currentScrollData == null) {
                cursorReadout.setText("Bar – · Beat –");
                return;
            }
            cursorReadout.setText(formatCursorReadout(tick, currentScrollData));
        });
        HBox cursorBar = new HBox(10, skipStartBtn, cursorReadout);
        cursorBar.setAlignment(Pos.CENTER_LEFT);
        cursorBar.setPadding(new Insets(4, 8, 4, 8));
        cursorBar.setStyle("-fx-background-color: #181825;");

        // Zoom slider footer.
        final Label zoomLabel = styledLabel("Zoom:");
        final Slider zoomSlider = new Slider(2, 80, 4);
        zoomSlider.setMaxWidth(120);
        zoomSlider.valueProperty().addListener((obs, o, n) -> pitchScroll.setMinQuarterPx(n.doubleValue()));
        final HBox zoomBar = new HBox(8, zoomLabel, zoomSlider);
        zoomBar.setAlignment(Pos.CENTER_RIGHT);
        zoomBar.setPadding(new Insets(2, 8, 2, 8));
        zoomBar.setStyle("-fx-background-color: #181825;");

        final VBox pianoRollBox = new VBox(cursorBar, pitchScroll, zoomBar);
        VBox.setVgrow(pitchScroll, Priority.ALWAYS);

        // ── Bottom: Keyboard | Guitar tabs ─────────────────────────
        kbHolder = new Pane();
        kbHolder.setStyle("-fx-background-color: #1e1e2e;");
        VBox keyboardBox = new VBox(kbHolder);
        keyboardBox.setStyle("-fx-background-color: #1e1e2e;");
        VBox.setVgrow(kbHolder, Priority.ALWAYS);

        gtHolder = new Pane();
        gtHolder.setStyle("-fx-background-color: #1e1e2e;");
        Arrays.fill(guitarStringEnabled, true);

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

        // ── Assembly ────────────────────────────────────────────────
        keyboardTab = tab("Keyboard", keyboardBox);
        guitarTab = tab("Guitar", guitarBox);
        bottomTabs = createTabPane(keyboardTab, guitarTab);

        bottomTabs.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            tearDownBottomDisplay(oldTab);
            setUpBottomDisplay(newTab);
        });
        setUpBottomDisplay(keyboardTab);

        // Two-row layout: piano roll (top, dominant) + bottom tabs.
        // The piano roll is the focal surface — no tab-pane wrapper.
        SplitPane mainSplit = new SplitPane(pianoRollBox, bottomTabs);
        mainSplit.setOrientation(Orientation.VERTICAL);
        mainSplit.setDividerPositions(0.78);
        styleSplitPane(mainSplit);

        // Right-edge drawer hosting the ControlsPanel. Sits above the
        // mainSplit in a StackPane so it floats over the piano roll
        // without resizing it. Toggled by the ⚙ button; ESC closes it.
        Button drawerClose = new Button("×");
        drawerClose.setStyle("-fx-background-color: transparent; -fx-text-fill: #cdd6f4; "
                + "-fx-font-size: 16; -fx-padding: 0 6 0 6;");
        Region drawerSpacer = new Region();
        HBox.setHgrow(drawerSpacer, Priority.ALWAYS);
        Label drawerTitle = new Label("Controls");
        drawerTitle.setStyle("-fx-text-fill: #cdd6f4; -fx-font-size: 13; -fx-font-weight: bold;");
        HBox drawerHeader = new HBox(8, drawerTitle, drawerSpacer, drawerClose);
        drawerHeader.setAlignment(Pos.CENTER_LEFT);
        drawerHeader.setPadding(new Insets(6, 8, 6, 10));
        drawerHeader.setStyle("-fx-background-color: #181825; "
                + "-fx-border-color: transparent transparent #313244 transparent; -fx-border-width: 1;");

        VBox drawer = new VBox(drawerHeader, controls.getRoot());
        drawer.setPrefWidth(280);
        drawer.setMinWidth(280);
        drawer.setMaxWidth(280);
        drawer.setStyle("-fx-background-color: #1e1e2e; "
                + "-fx-border-color: transparent transparent transparent #313244; -fx-border-width: 1;");
        drawer.setVisible(false);
        drawer.setManaged(false);
        StackPane.setAlignment(drawer, Pos.TOP_RIGHT);

        StackPane centerStack = new StackPane(mainSplit, drawer);
        Runnable toggleDrawer = () -> {
            boolean show = !drawer.isVisible();
            drawer.setVisible(show);
            drawer.setManaged(show);
        };
        transportBar.settingsButton().setOnAction(e -> toggleDrawer.run());
        drawerClose.setOnAction(e -> {
            drawer.setVisible(false);
            drawer.setManaged(false);
        });

        root.setTop(transportBar.getRoot());
        root.setCenter(centerStack);

        Scene scene = new Scene(root, 1100, 720);
        scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE && drawer.isVisible()) {
                drawer.setVisible(false);
                drawer.setManaged(false);
                e.consume();
            }
        });
        scene.getStylesheets().add(getClass().getResource("/dark-theme.css").toExternalForm());
        stage.setTitle("Music Notation Player");
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> {
            player.stop();
            Platform.exit();
        });
        stage.show();

        if (!PieceLibrary.titles().isEmpty()) {
            selectPieceTitle(PieceLibrary.titles().get(0));
        }
    }

    // ── Piano-roll auto-scroll callback ─────────────────────────────

    private void onPianoRollCursorMoved(double cursorX) {
        if (pitchScroll == null) return;
        double vpW = pitchScroll.getLaneViewportWidth();
        double contentW = pitchScroll.getLaneContentWidth();
        if (vpW <= 0 || contentW <= vpW) return;
        double scrollable = contentW - vpW;
        double scrollOffset = pitchScroll.getLaneScrollHvalue() * scrollable;
        double cursorInViewport = cursorX - scrollOffset;
        double threshold = vpW * 0.7;
        if (cursorInViewport > threshold) {
            double targetOffset = cursorX - threshold;
            pitchScroll.setLaneScrollHvalue(Math.clamp(targetOffset / scrollable, 0, 1));
        }
    }

    // ── Piece selection ──────────────────────────────────────────────

    // ── Soundbank handling ──────────────────────────────────────────

    private static final String PREF_SOUNDBANK_PATHS = "soundbank.paths";
    private final java.util.prefs.Preferences prefs =
            java.util.prefs.Preferences.userNodeForPackage(NotationApp.class);

    private void loadPersistedSoundbanks() {
        String saved = prefs.get(PREF_SOUNDBANK_PATHS, "");
        if (saved.isBlank()) return;
        var files = new java.util.ArrayList<java.io.File>();
        for (String line : saved.split("\n")) {
            if (line.isBlank()) continue;
            var f = new java.io.File(line.trim());
            if (f.isFile()) files.add(f);
        }
        controls.setSoundbanks(files);
        player.setSoundbankSetup(new SoundbankSetup(files));
    }

    private void onAddSoundbank(Stage stage) {
        var chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Add soundbank");
        chooser.getExtensionFilters().add(
                new javafx.stage.FileChooser.ExtensionFilter("Soundbanks", "*.sf2", "*.dls", "*.sbk"));
        var f = chooser.showOpenDialog(stage);
        if (f != null) controls.addSoundbank(f);
    }

    private void onSoundbanksChanged(java.util.List<java.io.File> files) {
        // Persist + stage on player. Apply lands on next start().
        if (files.isEmpty()) prefs.remove(PREF_SOUNDBANK_PATHS);
        else prefs.put(PREF_SOUNDBANK_PATHS,
                files.stream().map(java.io.File::getAbsolutePath)
                        .collect(java.util.stream.Collectors.joining("\n")));
        player.setSoundbankSetup(new SoundbankSetup(files));
        if (player.isPlaying() || player.isPaused()) {
            controls.setStatus(files.isEmpty()
                    ? "Soundbank reset (applies on next play)"
                    : "Soundbank list updated (applies on next play)");
        } else {
            controls.setStatus(files.isEmpty()
                    ? "Soundbank reset to default"
                    : "Soundbank list: " + files.size() + " file" + (files.size() == 1 ? "" : "s"));
        }
    }

    private void openPiecePicker() {
        PiecePickerDialog.show(hostStage, "Choose Piece", currentPieceTitle)
                .ifPresent(choice -> {
                    if (choice instanceof PieceChoice.Library lib) {
                        selectPieceTitle(lib.title());
                    } else if (choice instanceof PieceChoice.Imported imp) {
                        loadImport(imp.imp());
                    }
                });
    }

    /** Switch to a freshly-loaded MIDI import. Drops any current Piece. */
    private void loadImport(music.notation.performance.MidiImport imp) {
        onStop();
        currentPiece = null;
        originalPiece = null;
        currentPieceTitle = null;
        currentImport = imp;
        transportBar.setPieceLabel("▾  " + imp.displayName() + "   —   imported");

        // Disable Piece-only controls (scale combo / arrangement combo).
        controls.setProviders(java.util.List.of());

        // Reset per-track state — the new lane factory will repopulate.
        selectedInstruments.clear();
        selectedVolumes.clear();
        selectedPans.clear();
        instrumentButtons.clear();

        currentScrollData = PitchScrollData.fromImport(imp);
        disabledVisualizerTracks.clear();
        pitchScroll.load(currentScrollData);
        if (keyboardDisplay != null) keyboardDisplay.load(currentScrollData);
        if (guitarTabDisplay != null) guitarTabDisplay.load(currentScrollData);

        controls.setBpm(imp.initialBpm());
        controls.setSwing(SwingSetup.OFF);
        currentSwing = SwingSetup.OFF;
        controls.setPieceInfo(String.format(
                "%s — imported\n%d/%d  |  %d BPM",
                imp.displayName(),
                imp.timeSig().beats(), imp.timeSig().beatValue(),
                imp.initialBpm()));
        controls.setStatus("Imported · " + imp.performance().score().tracks().size() + " tracks");
        transportBar.playButton().setDisable(false);
        transportBar.exportButton().setDisable(false);
    }

    private void selectPieceTitle(final String title) {
        if (title == null) return;
        currentPieceTitle = title;
        currentImport = null;     // mutually exclusive with library piece
        String label = title;
        for (var p : PieceLibrary.pieces()) {
            if (p.title().equals(title)) {
                label = "▾  " + title + "   —   " + p.composer();
                break;
            }
        }
        transportBar.setPieceLabel(label);

        onStop();
        controls.setProviders(PieceLibrary.providers(title));
        loadFromSelectedProvider();
    }

    private void onProviderSelected() {
        if (controls.getSelectedProvider() == null) return;
        onStop();
        loadFromSelectedProvider();
    }

    // ── Scale & tempo adjustments ────────────────────────────────────

    /**
     * BPM release applies a {@link TempoSetup} live — no piece rebuild,
     * no playback restart.
     */
    /**
     * Swing changed in the Controls drawer. When playback is stopped we
     * just stage the new value for the next play. When playing, prompt
     * the user for a restart anchor (beginning vs current bar) and apply
     * via {@link MidiPlayer#applySwing}; on cancel, revert the combo.
     */
    private void onSwingChanged(SwingSetup picked) {
        if (picked == null) return;
        if (!player.isPlaying() && !player.isPaused()) {
            currentSwing = picked;
            return;
        }
        var prev = currentSwing;
        var choice = SwingRestartDialog.show(hostStage, "Apply Swing");
        if (choice.isEmpty()) {
            // Cancel — revert combo.
            controls.setSwing(prev);
            return;
        }
        long resumeTick = switch (choice.get()) {
            case FROM_START -> 0L;
            case FROM_BAR   -> snapToCurrentBar(player.getTickPosition());
        };
        try {
            player.applySwing(picked, resumeTick);
            currentSwing = picked;
            // Re-bind the playhead animation against the (re-loaded) sequencer.
            pitchScroll.stopAnimation();
            pitchScroll.startAnimation(player::getTickPosition);
        } catch (Exception ex) {
            ex.printStackTrace();
            controls.setSwing(prev);
        }
    }

    /** Snap a tick to the start of the bar that contains it. */
    private long snapToCurrentBar(long tick) {
        if (currentScrollData == null || currentScrollData.barTickWidth() <= 0) return 0L;
        long bw = currentScrollData.barTickWidth();
        return (tick / bw) * bw;
    }

    private void onBpmReleased() {
        int authoredBpm = (currentImport != null)
                ? currentImport.initialBpm()
                : (originalPiece != null ? originalPiece.tempo().bpm() : 0);
        if (authoredBpm <= 0) return;
        int targetBpm = controls.getSelectedBpm();
        player.applyTempo(TempoSetup.atBpm(targetBpm, authoredBpm));
    }

    /**
     * Apply scale transposition and tempo override to {@link #originalPiece},
     * reloading the UI. (Tempo via this path causes a reload; for live tempo,
     * the BPM slider's own listener calls {@link #onBpmReleased()} instead.)
     */
    private void rebuildPiece() {
        // Imports don't have a Piece to rebuild — scale change is a no-op.
        if (currentImport != null) return;
        if (originalPiece == null || controls.getSelectedKey() == null) return;
        onStop();

        Piece p = originalPiece;

        KeySignature targetKey = controls.getSelectedKey();
        KeySignature sourceKey = originalPiece.key();
        boolean sameKey = targetKey.tonic() == sourceKey.tonic()
                && targetKey.accidental() == sourceKey.accidental()
                && targetKey.mode() == sourceKey.mode();
        if (!sameKey) {
            p = transposePiece(p, sourceKey, targetKey);
        }

        int targetBpm = controls.getSelectedBpm();
        if (targetBpm != originalPiece.tempo().bpm()) {
            p = new Piece(p.title(), p.composer(), p.key(), p.timeSig(),
                    new Tempo(targetBpm, p.tempo().beatUnit()),
                    p.tracks());
        }

        currentPiece = p;
        loadPiece();
    }

    /**
     * Phase 4d transitional: transposition is currently a no-op. Will be
     * reimplemented as a Bar-level pitch transform once the bar
     * abstract-note shape stabilises.
     */
    private static Piece transposePiece(Piece source, KeySignature sourceKey, KeySignature targetKey) {
        return source;
    }

    private Instrument defaultInstrumentForImportTrack(int idx) {
        if (currentImport == null) return Instrument.ACOUSTIC_GRAND_PIANO;
        var t = currentImport.performance().score().tracks().get(idx);
        return t.kind() == music.notation.performance.TrackKind.DRUM
                ? Instrument.DRUM_KIT : Instrument.ACOUSTIC_GRAND_PIANO;
    }

    private static Instrument defaultInstrumentOf(Track track) {
        return switch (track) {
            case MelodicTrack mt -> mt.defaultInstrument();
            case DrumTrack dt -> Instrument.DRUM_KIT;
        };
    }

    /**
     * Rebuild the {@link ChannelSetup} from the current per-track UI
     * selections and push it to the player. Idempotent.
     */
    private void applyChannelSetupLive() {
        try {
            ChannelSetup setup = buildChannelSetup();
            if (setup != null) player.applySetup(setup);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /** Build a ChannelSetup from current UI state, branching on Piece vs Import. */
    private ChannelSetup buildChannelSetup() {
        if (currentImport != null) {
            return ChannelSetup.fromPerformanceTracks(
                    currentImport.performance().score().tracks(),
                    selectedInstruments, selectedVolumes, selectedPans);
        }
        if (currentPiece != null) {
            return ChannelSetup.from(currentPiece,
                    selectedInstruments, selectedVolumes, selectedPans);
        }
        return null;
    }

    /** Format "Bar X · Beat Y · tick T" for the cursor readout label. */
    private static String formatCursorReadout(long tick, PitchScrollData data) {
        long barWidth = data.barTickWidth();
        if (barWidth <= 0 || data.ticksPerQuarter() <= 0) {
            return "tick " + tick;
        }
        boolean hasPickup = data.pickupOffsetTicks() > 0;
        long rawBar = tick / barWidth;
        long tickInBar = tick % barWidth;
        long beat = tickInBar / data.ticksPerQuarter() + 1;
        long barIdx = hasPickup ? rawBar : rawBar + 1;
        if (hasPickup && rawBar == 0) {
            return String.format("Pickup · Beat %d · tick %d", beat, tick);
        }
        return String.format("Bar %d · Beat %d · tick %d", barIdx, beat, tick);
    }

    private void loadFromSelectedProvider() {
        final PieceContentProvider<?> provider = controls.getSelectedProvider();
        if (provider == null) return;

        originalPiece = provider.create();

        controls.setKey(originalPiece.key());
        controls.setBpm(originalPiece.tempo().bpm());
        controls.setSwing(SwingSetup.OFF);
        currentSwing = SwingSetup.OFF;

        currentPiece = originalPiece;
        loadPiece();
    }

    private void loadPiece() {
        controls.setPieceInfo(String.format("%s by %s\n%s  |  %d/%d  |  %d BPM",
                currentPiece.title(), currentPiece.composer(),
                currentPiece.key().tonic() + " " + currentPiece.key().mode(),
                currentPiece.timeSig().beats(), currentPiece.timeSig().beatValue(),
                currentPiece.tempo().bpm()));

        currentScrollData = PitchScrollData.fromPiece(currentPiece);
        disabledVisualizerTracks.clear();
        // Reset per-track state BEFORE rebuilding lanes — the lane factory
        // grows these lists and bakes the indices into button listeners.
        selectedInstruments.clear();
        selectedVolumes.clear();
        selectedPans.clear();
        instrumentButtons.clear();
        pitchScroll.load(currentScrollData);
        if (keyboardDisplay != null) keyboardDisplay.load(currentScrollData);
        if (guitarTabDisplay != null) guitarTabDisplay.load(currentScrollData);
        transportBar.playButton().setDisable(false);
        transportBar.exportButton().setDisable(false);
        controls.setStatus("Ready");
    }

    // ── Per-track row control panel (GarageBand-style) ─────────────────

    /**
     * Build the control panel that sits to the LEFT of each piano-roll
     * lane: track name + instrument-picker button + volume slider + KB
     * visualizer toggle. Called by {@link PitchScroll} during lane rebuild.
     */
    private Node buildTrackRowControlPanel(int trackIndex, String trackName) {
        Instrument defaultIns;
        if (currentImport != null) {
            var t = currentImport.performance().score().tracks().get(trackIndex);
            defaultIns = (t.kind() == music.notation.performance.TrackKind.DRUM)
                    ? Instrument.DRUM_KIT : Instrument.ACOUSTIC_GRAND_PIANO;
        } else {
            Track track = currentPiece.tracks().get(trackIndex);
            defaultIns = defaultInstrumentOf(track);
        }

        while (selectedInstruments.size() <= trackIndex) selectedInstruments.add(null);
        while (selectedVolumes.size() <= trackIndex) selectedVolumes.add(null);
        while (selectedPans.size() <= trackIndex) selectedPans.add(null);
        while (instrumentButtons.size() <= trackIndex) instrumentButtons.add(null);
        if (selectedInstruments.get(trackIndex) == null) selectedInstruments.set(trackIndex, defaultIns);
        if (selectedVolumes.get(trackIndex) == null) selectedVolumes.set(trackIndex, 100);
        if (selectedPans.get(trackIndex) == null) selectedPans.set(trackIndex, 64);

        Label nameLabel = new Label(trackName);
        nameLabel.setStyle("-fx-text-fill: #cdd6f4; -fx-font-size: 12; -fx-font-weight: bold;");
        nameLabel.setMaxWidth(Double.MAX_VALUE);

        Button instrButton = new Button(InstrumentPickerDialog.displayName(selectedInstruments.get(trackIndex)));
        instrButton.setMaxWidth(Double.MAX_VALUE);
        instrButton.setStyle("-fx-background-color: #313244; -fx-text-fill: #cdd6f4; -fx-font-size: 11;");
        instrButton.setOnAction(e -> {
            Instrument current = selectedInstruments.get(trackIndex);
            Consumer<Instrument> preview = picked -> {
                selectedInstruments.set(trackIndex, picked);
                applyChannelSetupLive();
            };
            var chosen = InstrumentPickerDialog.show(
                    hostStage, "Instrument · " + trackName, current, preview);
            if (chosen.isPresent()) {
                instrButton.setText(InstrumentPickerDialog.displayName(chosen.get()));
            } else {
                instrButton.setText(InstrumentPickerDialog.displayName(current));
            }
        });
        instrumentButtons.set(trackIndex, instrButton);

        Slider volSlider = new Slider(0, 127, selectedVolumes.get(trackIndex));
        volSlider.setBlockIncrement(1);
        volSlider.setStyle("-fx-control-inner-background: #313244;");
        Label volLabel = new Label(String.valueOf(selectedVolumes.get(trackIndex)));
        volLabel.setStyle("-fx-text-fill: #a6adc8; -fx-font-size: 11; -fx-min-width: 28;");
        volSlider.valueProperty().addListener((obs, oldV, newV) -> {
            int v = newV.intValue();
            volLabel.setText(String.valueOf(v));
            selectedVolumes.set(trackIndex, v);
            applyChannelSetupLive();
        });
        HBox volRow = new HBox(4, volSlider, volLabel);
        volRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(volSlider, Priority.ALWAYS);

        // Pan slider — 0=hard-L, 64=C, 127=hard-R; double-click resets to 64.
        Slider panSlider = new Slider(0, 127, selectedPans.get(trackIndex));
        panSlider.setBlockIncrement(1);
        panSlider.setMajorTickUnit(64);
        panSlider.setStyle("-fx-control-inner-background: #313244;");
        Label panLabel = new Label(panText(selectedPans.get(trackIndex)));
        panLabel.setStyle("-fx-text-fill: #a6adc8; -fx-font-size: 11; -fx-min-width: 28;");
        panSlider.valueProperty().addListener((obs, oldV, newV) -> {
            int v = newV.intValue();
            panLabel.setText(panText(v));
            selectedPans.set(trackIndex, v);
            applyChannelSetupLive();
        });
        // Double-click anywhere on the slider to recentre.
        panSlider.setOnMouseClicked(ev -> {
            if (ev.getClickCount() == 2) panSlider.setValue(64);
        });
        HBox panRow = new HBox(4, panSlider, panLabel);
        panRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(panSlider, Priority.ALWAYS);

        CheckBox kbToggle = new CheckBox("KB");
        kbToggle.setSelected(true);
        kbToggle.setStyle("-fx-text-fill: #a6adc8; -fx-font-size: 11;");
        kbToggle.selectedProperty().addListener((obs, o, n) -> {
            if (n) disabledVisualizerTracks.remove(trackName);
            else disabledVisualizerTracks.add(trackName);
            if (keyboardDisplay != null) keyboardDisplay.setTrackEnabled(trackName, n);
            if (guitarTabDisplay != null) guitarTabDisplay.setTrackEnabled(trackName, n);
            pitchScroll.setTrackEnabled(trackName, n);
        });

        // Compact "Vol"/"Pan" prefix labels.
        Label volPrefix = mini("Vol");
        Label panPrefix = mini("Pan");
        HBox volRowFull = new HBox(4, volPrefix, volRow);
        volRowFull.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(volRow, Priority.ALWAYS);
        HBox panRowFull = new HBox(4, panPrefix, panRow);
        panRowFull.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(panRow, Priority.ALWAYS);

        VBox panel = new VBox(3, nameLabel, instrButton, volRowFull, panRowFull, kbToggle);
        panel.setPadding(new Insets(4, 6, 4, 6));
        panel.setStyle("-fx-background-color: #181825; -fx-border-color: transparent #313244 transparent transparent; -fx-border-width: 1;");
        return panel;
    }

    // ── Playback ──────────────────────────────────────────────────────────

    private void onPlay() {
        if (currentPiece == null && currentImport == null) return;

        int trackCount = (currentImport != null)
                ? currentImport.performance().score().tracks().size()
                : currentPiece.tracks().size();
        ChannelSetup channelSetup = (currentImport != null)
                ? ChannelSetup.fromPerformanceTracks(
                        currentImport.performance().score().tracks(),
                        flatList(selectedInstruments, trackCount,
                                i -> defaultInstrumentForImportTrack(i)),
                        flatIntList(selectedVolumes, trackCount, 100),
                        flatIntList(selectedPans, trackCount, 64))
                : ChannelSetup.from(currentPiece,
                        flatList(selectedInstruments, trackCount,
                                i -> defaultInstrumentOf(currentPiece.tracks().get(i))),
                        flatIntList(selectedVolumes, trackCount, 100),
                        flatIntList(selectedPans, trackCount, 64));

        int authoredBpm = (currentImport != null)
                ? currentImport.initialBpm()
                : (originalPiece != null ? originalPiece.tempo().bpm() : currentPiece.tempo().bpm());
        int targetBpm = controls.getSelectedBpm();
        var tempoSetup = (authoredBpm > 0 && targetBpm != authoredBpm)
                ? TempoSetup.atBpm(targetBpm, authoredBpm)
                : TempoSetup.unity();

        transportBar.setPlayGlyph("⏸");
        transportBar.stopButton().setDisable(false);
        transportBar.setPieceEnabled(false);
        controls.setStatus("Playing...");
        pitchScroll.setLaneScrollHvalue(0);

        SwingSetup swingAtStart = currentSwing;
        var importAtStart = currentImport;
        var pieceAtStart = currentPiece;
        Thread playThread = new Thread(() -> {
            try {
                if (importAtStart != null) {
                    player.start(importAtStart.performance(), channelSetup, tempoSetup, swingAtStart);
                } else {
                    player.start(pieceAtStart, channelSetup, tempoSetup, swingAtStart);
                }
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
                    transportBar.setPlayGlyph("▶");
                    transportBar.playButton().setDisable(false);
                    transportBar.stopButton().setDisable(true);
                    transportBar.setPieceEnabled(true);
                    controls.setStatus("Finished");
                });
            }
        });
        playThread.setDaemon(true);
        playThread.start();
    }

    private void onPlayPause() {
        if (player.isPaused()) {
            player.resume();
            transportBar.setPlayGlyph("⏸");
            controls.setStatus("Playing...");
            animating = true;
            pitchScroll.startAnimation(player::getTickPosition);
            startActiveBottomDisplay();
        } else if (player.isPlaying()) {
            player.pause();
            transportBar.setPlayGlyph("▶");
            controls.setStatus("Paused");
            animating = false;
            pitchScroll.stopAnimation();
            stopActiveBottomDisplay();
        } else {
            onPlay();
        }
    }

    private void onStop() {
        animating = false;
        pitchScroll.stopAnimation();
        stopActiveBottomDisplay();
        player.stop();
        transportBar.setPlayGlyph("▶");
        transportBar.playButton().setDisable(currentPiece == null && currentImport == null);
        transportBar.stopButton().setDisable(true);
        transportBar.setPieceEnabled(true);
        controls.setStatus("Stopped");
    }

    private void onExport(Stage stage) {
        if (currentPiece == null && currentImport == null) return;

        String baseName = (currentImport != null)
                ? currentImport.displayName()
                : currentPiece.title();
        String safeName = baseName
                .replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fff _-]", "")
                .replace(' ', '_');

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export MIDI");
        chooser.setInitialFileName(safeName + ".mid");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("MIDI Files", "*.mid", "*.midi"));
        File file = chooser.showSaveDialog(stage);
        if (file == null) return;

        ChannelSetup channelSetup = buildChannelSetup();
        try {
            if (currentImport != null) {
                MidiPlayer.exportMidi(currentImport.performance(), channelSetup, file);
            } else {
                MidiPlayer.exportMidi(currentPiece, channelSetup, file);
            }
            controls.setStatus("Exported: " + file.getName());
        } catch (Exception ex) {
            controls.setStatus("Export failed: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /** Pad/coerce a per-track value list to the track count, falling back to {@code defaultFor}. */
    private static List<Instrument> flatList(List<Instrument> src, int n,
                                             java.util.function.IntFunction<Instrument> defaultFor) {
        var out = new ArrayList<Instrument>(n);
        for (int i = 0; i < n; i++) {
            Instrument v = (src != null && i < src.size()) ? src.get(i) : null;
            out.add(v != null ? v : defaultFor.apply(i));
        }
        return out;
    }

    private static List<Integer> flatIntList(List<Integer> src, int n, int defaultV) {
        var out = new ArrayList<Integer>(n);
        for (int i = 0; i < n; i++) {
            Integer v = (src != null && i < src.size()) ? src.get(i) : null;
            out.add(v != null ? v : defaultV);
        }
        return out;
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

    // ── Local widget helpers (small enough to leave inline) ────────────

    private static Tab tab(String title, Node content) {
        Tab t = new Tab(title, content);
        t.setClosable(false);
        return t;
    }

    private static TabPane createTabPane(Tab... tabs) {
        TabPane tp = new TabPane(tabs);
        tp.setTabMinWidth(60);
        tp.setStyle("-fx-background-color: #1e1e2e; -fx-tab-min-height: 28;");
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

    private static Label mini(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: #6c7086; -fx-font-size: 10; -fx-min-width: 22;");
        return l;
    }

    /** GarageBand-style pan readout: "L42", "C", "R30". */
    private static String panText(int v) {
        if (v == 64) return "C";
        if (v < 64) return "L" + (64 - v);
        return "R" + (v - 64);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
