package music.notation.recorder;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import music.notation.expressivity.Articulations;
import music.notation.expressivity.TrackId;
import music.notation.expressivity.Velocities;
import music.notation.expressivity.VelocityChange;
import music.notation.expressivity.VelocityControl;
import music.notation.input.AudioFileInputSource;
import music.notation.input.MidiInputBinding;
import music.notation.input.MidiKeyboardInputSource;
import music.notation.input.NoteInputListener;
import music.notation.input.NoteInputSource;
import music.notation.input.PitchEstimate;
import music.notation.input.VocalMicInputSource;
import music.notation.mxl.MxlImport;
import music.notation.mxl.MxlSplitJsonWriter;
import music.notation.performance.Instrumentation;
import music.notation.performance.Performance;
import music.notation.performance.PitchedNote;
import music.notation.performance.Score;
import music.notation.performance.TempoTrack;
import music.notation.performance.Track;
import music.notation.performance.TrackKind;
import music.notation.pitch.NoteName;
import music.notation.structure.KeySignature;
import music.notation.structure.Mode;
import music.notation.structure.TimeSignature;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dedicated JavaFX app for capturing vocal input via the system mic and
 * exporting the detected ConcreteNotes as PerformanceJson — the same
 * serialisation format the rest of the notation system reads.
 *
 * <h2>Workflow</h2>
 *
 * <ol>
 *   <li><b>● Record</b> — opens the system mic, runs YIN + segmentation.
 *       Live pitch readout updates as the user sings; segmented notes
 *       append to the captured list.</li>
 *   <li><b>■ Stop</b> — closes the mic; any held note is flushed.</li>
 *   <li><b>Save JSON…</b> — wraps the captured notes in a single
 *       VOCAL-track Performance, includes per-note velocities, writes
 *       {@link PerformanceJson} to the chosen file.</li>
 *   <li>The exported JSON is importable by the main NotationApp's
 *       MIDI-import or any other reader.</li>
 * </ol>
 *
 * <p>Threading note: input-source callbacks arrive on the audio capture
 * thread; this app marshals every UI mutation through
 * {@link Platform#runLater} to keep the JavaFX scene graph happy.</p>
 */
public final class RecorderApp extends Application {

    public static void main(String[] args) {
        Application.launch(RecorderApp.class, args);
    }

    // ── Model state ───────────────────────────────────────────────────

    private final VocalMicInputSource input = new VocalMicInputSource();
    private final ObservableList<CapturedNote> captured = FXCollections.observableArrayList();

    private Stage stage;
    private Button recordBtn;
    private Button stopBtn;
    private Button importBtn;
    private Button clearBtn;
    private Button saveBtn;
    private Button midiRecordBtn;
    private Button midiStopBtn;
    private Button midiRefreshBtn;
    private ComboBox<javax.sound.midi.MidiDevice.Info> midiDeviceCombo;
    private CheckBox midiAuditionCheck;
    /** Lazily-opened synth used for audition during MIDI recording. */
    private javax.sound.midi.Synthesizer auditionSynth;
    private Label pitchLabel;
    private Label statusLabel;
    private Canvas levelMeter;
    private ListView<CapturedNote> noteList;

    private long sessionStartMs;
    /** Non-null while an offline audio-file analysis is running. */
    private AudioFileInputSource activeImport;
    /** Non-null while a MIDI keyboard recording is in progress. */
    private MidiKeyboardInputSource activeMidi;

    // ── App entry ─────────────────────────────────────────────────────

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        primaryStage.setTitle("Notation · Vocal Recorder");

        VBox topBar = new VBox(buildToolbar(), buildMidiBar());
        BorderPane root = new BorderPane();
        root.setTop(topBar);
        root.setCenter(buildBody());
        root.setBottom(buildStatusBar());

        Scene scene = new Scene(root, 820, 540);
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> {
            input.close();
            if (activeMidi != null) activeMidi.close();
            closeAuditionSynth();
        });
        primaryStage.show();

        wireInput();
        refreshMidiDevices();
        updateButtonState(/*isRecording=*/ false);
    }

    @Override
    public void stop() {
        input.close();
        if (activeMidi != null) activeMidi.close();
        closeAuditionSynth();
    }

    // ── UI ────────────────────────────────────────────────────────────

    private HBox buildToolbar() {
        Label title = new Label("Vocal Recorder");
        title.setFont(Font.font("System", FontWeight.BOLD, 14));

        recordBtn = new Button("● Record");
        recordBtn.setStyle("-fx-font-weight: bold;");
        recordBtn.setOnAction(e -> onRecord());

        stopBtn = new Button("■ Stop");
        stopBtn.setOnAction(e -> onStop());

        importBtn = new Button("Import audio…");
        importBtn.setOnAction(e -> onImportAudio());

        clearBtn = new Button("Clear");
        clearBtn.setOnAction(e -> onClear());

        saveBtn = new Button("Save JSON folder…");
        saveBtn.setStyle("-fx-font-weight: bold;");
        saveBtn.setOnAction(e -> onSave());

        HBox bar = new HBox(10, title, sep(),
                recordBtn, stopBtn, sep(),
                importBtn, sep(),
                clearBtn, sep(),
                saveBtn);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10, 14, 10, 14));
        bar.setStyle("-fx-background-color: #f4f1e8; -fx-border-color: #d4cfc0; -fx-border-width: 0 0 1 0;");
        return bar;
    }

    private BorderPane buildBody() {
        // ── Pitch + level readout strip
        pitchLabel = new Label("—");
        pitchLabel.setFont(Font.font("Menlo", 14));
        pitchLabel.setStyle("-fx-text-fill: #3a3a3a;");

        levelMeter = new Canvas(220, 14);
        paintLevelMeter(0.0);

        Label pitchTag = new Label("Pitch:");
        pitchTag.setStyle("-fx-text-fill: #8b3a3a; -fx-font-weight: bold; -fx-font-size: 10;");

        Label levelTag = new Label("Level:");
        levelTag.setStyle("-fx-text-fill: #8b3a3a; -fx-font-weight: bold; -fx-font-size: 10;");

        HBox pitchRow = new HBox(8, pitchTag, pitchLabel, sep(), levelTag, levelMeter);
        pitchRow.setAlignment(Pos.CENTER_LEFT);
        pitchRow.setPadding(new Insets(10, 14, 10, 14));
        pitchRow.setStyle("-fx-background-color: #fffef9; -fx-border-color: #d4cfc0; -fx-border-width: 0 0 1 0;");

        // ── Captured notes table
        noteList = new ListView<>(captured);
        noteList.setPlaceholder(new Label("Press ● Record and sing — captured notes will appear here."));
        noteList.setCellFactory(lv -> new NoteCell());

        BorderPane body = new BorderPane();
        body.setTop(pitchRow);
        body.setCenter(noteList);
        return body;
    }

    private HBox buildMidiBar() {
        Label midiLabel = new Label("MIDI in:");
        midiLabel.setFont(Font.font("System", FontWeight.BOLD, 11));
        midiLabel.setStyle("-fx-text-fill: #6e4218;");

        midiDeviceCombo = new ComboBox<>();
        midiDeviceCombo.setPrefWidth(280);
        midiDeviceCombo.setPlaceholder(new Label("No MIDI inputs found"));
        midiDeviceCombo.setCellFactory(lv -> midiInfoCell());
        midiDeviceCombo.setButtonCell(midiInfoCell());

        midiRefreshBtn = new Button("↻");
        midiRefreshBtn.setStyle("-fx-font-size: 11;");
        midiRefreshBtn.setOnAction(e -> refreshMidiDevices());

        midiRecordBtn = new Button("● Record MIDI");
        midiRecordBtn.setStyle("-fx-font-weight: bold;");
        midiRecordBtn.setOnAction(e -> onRecordMidi());

        midiStopBtn = new Button("■ Stop MIDI");
        midiStopBtn.setOnAction(e -> onStopMidi());

        midiAuditionCheck = new CheckBox("♪ Audition");
        midiAuditionCheck.setSelected(true);
        midiAuditionCheck.setTooltip(new javafx.scene.control.Tooltip(
                "Play incoming MIDI through the JDK synth so you hear what you play. "
                + "Toggle off if your keyboard already produces sound."));

        HBox bar = new HBox(8, midiLabel, midiDeviceCombo, midiRefreshBtn,
                sep(), midiRecordBtn, midiStopBtn, sep(), midiAuditionCheck);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8, 14, 8, 14));
        bar.setStyle("-fx-background-color: #fdf6e6; -fx-border-color: #d4cfc0; -fx-border-width: 0 0 1 0;");
        return bar;
    }

    private static ListCell<javax.sound.midi.MidiDevice.Info> midiInfoCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(javax.sound.midi.MidiDevice.Info item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        };
    }

    private HBox buildStatusBar() {
        statusLabel = new Label("Ready.");
        statusLabel.setStyle("-fx-text-fill: #555;");
        HBox bar = new HBox(statusLabel);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(6, 14, 6, 14));
        bar.setStyle("-fx-background-color: #f4f1e8; -fx-border-color: #d4cfc0; -fx-border-width: 1 0 0 0;");
        return bar;
    }

    private Region sep() {
        Region r = new Region();
        r.setPrefWidth(12);
        return r;
    }

    // ── Wiring ────────────────────────────────────────────────────────

    private void wireInput() {
        input.setListener(new NoteInputListener() {
            @Override
            public void onPitchEstimate(PitchEstimate est) {
                Platform.runLater(() -> {
                    if (est.hasPitch()) {
                        pitchLabel.setText(formatPitch(est.midiFloat(), est.confidence()));
                    } else {
                        pitchLabel.setText("—");
                    }
                    paintLevelMeter(est.rms());
                });
            }
            @Override
            public void onNoteCompleted(PitchedNote n, int velocity) {
                Platform.runLater(() -> {
                    captured.add(new CapturedNote(n, velocity));
                    statusLabel.setText("Recording… " + captured.size() + " note(s)");
                });
            }
        });
    }

    // ── Actions ───────────────────────────────────────────────────────

    private void onRecord() {
        try {
            sessionStartMs = System.currentTimeMillis();
            input.start();
            updateButtonState(/*isRecording=*/ true);
            statusLabel.setText("Recording… (sing into the mic)");
        } catch (Exception ex) {
            showError("Could not open microphone", ex.getMessage());
        }
    }

    private void onStop() {
        input.stop();
        updateButtonState(false);
        long elapsedSec = (System.currentTimeMillis() - sessionStartMs) / 1000;
        statusLabel.setText("Stopped · captured " + captured.size() + " note(s) in " + elapsedSec + "s");
        paintLevelMeter(0.0);
        pitchLabel.setText("—");
    }

    private void onClear() {
        captured.clear();
        statusLabel.setText("Cleared.");
    }

    // ── MIDI keyboard recording ───────────────────────────────────────

    /** Re-populate the MIDI device dropdown from the OS. */
    private void refreshMidiDevices() {
        var devices = MidiInputBinding.listInputs();
        midiDeviceCombo.getItems().setAll(devices);
        if (!devices.isEmpty() && midiDeviceCombo.getValue() == null) {
            midiDeviceCombo.setValue(devices.get(0));
        }
        midiRecordBtn.setDisable(devices.isEmpty() || activeMidi != null);
    }

    /**
     * Open the selected MIDI device and start capturing. Notes append
     * to the same captured list as mic / audio-import sources. The
     * existing live-mic "● Record" path is disabled while MIDI is
     * running to keep the timeline coherent (both sources use
     * session-relative ticks).
     *
     * <p>If audition is checked, the JDK default synth is opened and
     * the same incoming events are routed to it via FanOutReceiver —
     * the user hears what they play (with the synth's default piano
     * voice) while the notes are captured. Toggle audition off when
     * the keyboard already has its own sound source.</p>
     */
    private void onRecordMidi() {
        var info = midiDeviceCombo.getValue();
        if (info == null) {
            showError("No MIDI device", "Select a MIDI input device first.");
            return;
        }
        if (activeMidi != null && activeMidi.isRunning()) {
            return;
        }

        javax.sound.midi.Receiver auditionRx = null;
        if (midiAuditionCheck.isSelected()) {
            try {
                auditionSynth = javax.sound.midi.MidiSystem.getSynthesizer();
                if (!auditionSynth.isOpen()) auditionSynth.open();
                auditionRx = auditionSynth.getReceiver();
            } catch (Exception ex) {
                // Audition is non-essential — log and continue without it
                // rather than aborting MIDI capture entirely.
                System.err.println("Audition synth unavailable: " + ex.getMessage());
                if (auditionSynth != null) {
                    try { auditionSynth.close(); } catch (Exception ignored) {}
                    auditionSynth = null;
                }
            }
        }

        activeMidi = new MidiKeyboardInputSource(info, auditionRx);
        activeMidi.setListener(new NoteInputListener() {
            @Override
            public void onNoteStart(long tickMs, int midi, int velocity) {
                Platform.runLater(() -> {
                    pitchLabel.setText(formatPitch(midi, /*confidence=*/ 1.0));
                });
            }
            @Override
            public void onNoteCompleted(PitchedNote n, int velocity) {
                Platform.runLater(() -> {
                    captured.add(new CapturedNote(n, velocity));
                    statusLabel.setText("MIDI recording… " + captured.size() + " note(s)");
                });
            }
        });
        try {
            activeMidi.start();
            midiRecordBtn.setDisable(true);
            midiStopBtn.setDisable(false);
            midiDeviceCombo.setDisable(true);
            midiRefreshBtn.setDisable(true);
            midiAuditionCheck.setDisable(true);   // can't toggle mid-session
            recordBtn.setDisable(true);   // suppress mic record while MIDI is live
            importBtn.setDisable(true);
            String suffix = (auditionRx != null) ? " · audition on" : "";
            statusLabel.setText("MIDI recording from " + info.getName()
                    + "… (play your keyboard)" + suffix);
        } catch (Exception ex) {
            activeMidi = null;
            closeAuditionSynth();
            showError("Could not open MIDI device", ex.getMessage());
        }
    }

    /** Close the audition synth (if any) cleanly. Safe to call when no synth open. */
    private void closeAuditionSynth() {
        if (auditionSynth != null) {
            try { auditionSynth.close(); } catch (Exception ignored) {}
            auditionSynth = null;
        }
    }

    /** Stop the MIDI capture and release the device + audition synth. */
    private void onStopMidi() {
        if (activeMidi == null) return;
        activeMidi.stop();
        activeMidi.close();
        activeMidi = null;
        closeAuditionSynth();
        midiRecordBtn.setDisable(midiDeviceCombo.getItems().isEmpty());
        midiStopBtn.setDisable(true);
        midiDeviceCombo.setDisable(false);
        midiRefreshBtn.setDisable(false);
        midiAuditionCheck.setDisable(false);
        recordBtn.setDisable(false);
        importBtn.setDisable(false);
        pitchLabel.setText("—");
        statusLabel.setText("MIDI stopped · captured " + captured.size() + " note(s)");
    }

    /**
     * Analyse an audio file offline and append its detected notes to
     * the captured list. Same YIN + segmenter pipeline as live mic
     * capture, just fed from disk. Runs on a background thread so the
     * UI stays responsive; status bar shows progress and final count.
     *
     * <p>Format support: WAV / AIFF / AU natively, and via
     * {@link music.notation.input.AudioFileLoader}'s ffmpeg fallback,
     * .m4a / .mp3 / .ogg / .flac / .aac / .opus. The ffmpeg tool
     * must be on PATH for the non-native formats; the error message
     * tells the user to install it if it's missing.</p>
     *
     * <p>Notes from the file carry the file's intrinsic timing (start at
     * tick 0). If the user also has live-recorded notes in the list,
     * the new notes overlap them — typical "clear before import"
     * applies. The Clear button is enabled accordingly.</p>
     */
    private void onImportAudio() {
        if (activeImport != null && activeImport.isRunning()) {
            showError("Import in progress", "Wait for the current import to finish.");
            return;
        }
        FileChooser fc = new FileChooser();
        fc.setTitle("Import audio file for vocal-to-notes analysis");
        // Combined filter first (most user-friendly), then per-format for advanced users.
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Audio files", "*.wav", "*.aif", "*.aiff", "*.au",
                                 "*.m4a", "*.mp3", "*.ogg", "*.flac", "*.aac", "*.opus"));
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("WAV", "*.wav"));
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("MPEG-4 audio (.m4a)", "*.m4a"));
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("MP3", "*.mp3"));
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("All files", "*.*"));
        File source = fc.showOpenDialog(stage);
        if (source == null) return;

        boolean needsFfmpeg = !music.notation.input.AudioFileLoader.isNativelySupported(source);
        statusLabel.setText(needsFfmpeg
                ? "Importing " + source.getName() + " (decoding via ffmpeg)…"
                : "Importing " + source.getName() + "…");
        importBtn.setDisable(true);
        recordBtn.setDisable(true);

        AudioFileInputSource src = new AudioFileInputSource(source);
        src.setListener(new NoteInputListener() {
            @Override
            public void onNoteCompleted(PitchedNote n, int velocity) {
                Platform.runLater(() -> captured.add(new CapturedNote(n, velocity)));
            }
            // Pitch + level visualisation is a live-only nicety; offline
            // imports run faster than realtime, so the meter just
            // flickers — we suppress it to keep the UI quiet.
        });
        final int beforeCount = captured.size();
        src.setOnFinished(() -> Platform.runLater(() -> {
            int added = captured.size() - beforeCount;
            if (added == 0) {
                statusLabel.setText("Import yielded no notes for " + source.getName()
                        + (needsFfmpeg
                            ? " — check that ffmpeg is installed and the file is valid audio."
                            : " — file may be silent or unsupported."));
            } else {
                statusLabel.setText("Imported " + source.getName()
                        + " · +" + added + " note(s), " + captured.size() + " total");
            }
            importBtn.setDisable(false);
            recordBtn.setDisable(false);
            activeImport = null;
        }));
        activeImport = src;
        try {
            src.start();
        } catch (Exception ex) {
            activeImport = null;
            importBtn.setDisable(false);
            recordBtn.setDisable(false);
            showError("Could not import " + source.getName(), ex.getMessage());
        }
    }

    /**
     * Write the capture as a standard JSON piece folder — the same shape
     * NotationApp loads via its "Load JSON folder…" action. The user
     * picks a parent directory; we create a sub-folder named after the
     * recording and populate it via {@link MxlSplitJsonWriter}.
     *
     * <p>Layout produced:</p>
     * <pre>
     *   &lt;chosen-parent&gt;/&lt;recording-name&gt;/
     *       meta.json
     *       tempo.json
     *       track-01-vocal.json
     *       velocity.json
     * </pre>
     */
    private void onSave() {
        if (captured.isEmpty()) {
            showError("Nothing to save", "Capture some notes first.");
            return;
        }
        javafx.stage.DirectoryChooser dc = new javafx.stage.DirectoryChooser();
        dc.setTitle("Choose parent folder for the JSON piece");
        File parent = dc.showDialog(stage);
        if (parent == null) return;

        // Prompt for the recording name (becomes the sub-folder + displayName).
        String defaultName = "vocal-capture-" + System.currentTimeMillis();
        javafx.scene.control.TextInputDialog nameDialog = new javafx.scene.control.TextInputDialog(defaultName);
        nameDialog.setTitle("Piece name");
        nameDialog.setHeaderText("Name this recording");
        nameDialog.setContentText("Folder name and meta.json displayName:");
        var picked = nameDialog.showAndWait();
        if (picked.isEmpty() || picked.get().isBlank()) return;
        String name = picked.get().trim();

        File pieceDir = new File(parent, name);
        if (pieceDir.exists() && pieceDir.isFile()) {
            showError("Save failed", "A file with that name already exists.");
            return;
        }
        try {
            MxlImport imp = buildMxlImport(name, captured);
            MxlSplitJsonWriter.write(imp, pieceDir.toPath());
            statusLabel.setText("Saved " + captured.size() + " note(s) → "
                    + pieceDir.getName() + "/ (load via NotationApp → Load JSON folder)");
        } catch (Exception ex) {
            showError("Save failed", ex.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private void updateButtonState(boolean isRecording) {
        recordBtn.setDisable(isRecording);
        stopBtn.setDisable(!isRecording);
        importBtn.setDisable(isRecording);
        saveBtn.setDisable(captured.isEmpty() && !isRecording);
        clearBtn.setDisable(isRecording);
        // MIDI buttons follow their own lifecycle; initial state has Stop
        // disabled because no MIDI session is running yet.
        if (midiStopBtn != null) midiStopBtn.setDisable(true);
        captured.addListener((javafx.collections.ListChangeListener<CapturedNote>) c ->
                saveBtn.setDisable(captured.isEmpty()));
    }

    private void paintLevelMeter(double rms) {
        GraphicsContext g = levelMeter.getGraphicsContext2D();
        double w = levelMeter.getWidth(), h = levelMeter.getHeight();
        g.setFill(Color.web("#eee"));
        g.fillRect(0, 0, w, h);
        double level = Math.sqrt(Math.max(0.0, Math.min(1.0, rms)));     // sqrt curve = perceptual
        double barW = w * level;
        Color barColor = level > 0.85 ? Color.web("#d04646")
                       : level > 0.5  ? Color.web("#a16826")
                       :                 Color.web("#4d7a47");
        g.setFill(barColor);
        g.fillRect(0, 0, barW, h);
        g.setStroke(Color.web("#888"));
        g.strokeRect(0.5, 0.5, w - 1, h - 1);
    }

    private static String formatPitch(double midiFloat, double confidence) {
        int midi = (int) Math.round(midiFloat);
        double cents = (midiFloat - midi) * 100.0;
        double hz = 440.0 * Math.pow(2.0, (midiFloat - 69.0) / 12.0);
        return String.format("%-3s (MIDI %d, %+.0f¢, %.1f Hz, conf %.2f)",
                noteName(midi), midi, cents, hz, confidence);
    }

    private static String noteName(int midi) {
        String[] names = {"C","C#","D","D#","E","F","F#","G","G#","A","A#","B"};
        int oct = midi / 12 - 1;
        return names[midi % 12] + oct;
    }

    private void showError(String title, String detail) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle("Vocal Recorder");
        a.setHeaderText(title);
        a.setContentText(detail);
        a.showAndWait();
    }

    // ── JSON construction ────────────────────────────────────────────

    /**
     * Wrap captured notes in a single VOCAL-style PITCHED track inside a
     * {@link Performance}, with a {@link Velocities} side-channel carrying
     * the per-note onset velocities. Reusable by both the folder writer
     * and any test that needs an in-memory representation.
     */
    static Performance buildPerformance(List<CapturedNote> captured) {
        TrackId trackId = new TrackId("vocal");
        List<music.notation.performance.ConcreteNote> notes = new ArrayList<>(captured.size());
        List<VelocityChange> velChanges = new ArrayList<>(captured.size());
        for (CapturedNote c : captured) {
            notes.add(c.note());
            velChanges.add(new VelocityChange(c.note().tickMs(), c.velocity()));
        }
        Track track = new Track(trackId, TrackKind.PITCHED, notes);
        Map<TrackId, VelocityControl> velocityMap = new LinkedHashMap<>();
        if (!velChanges.isEmpty()) {
            velocityMap.put(trackId, new VelocityControl(velChanges));
        }
        return new Performance(
                new Score(List.of(track)),
                TempoTrack.empty(),
                Instrumentation.empty(),
                music.notation.expressivity.Volume.empty(),
                Articulations.empty(),
                music.notation.expressivity.Pedaling.empty(),
                new Velocities(velocityMap));
    }

    /**
     * Wrap captured notes in an {@link MxlImport} suitable for writing
     * via {@link MxlSplitJsonWriter}. The recorder has no real time
     * signature or key context, so 4/4 and C major are used as neutral
     * defaults — downstream consumers can override after import.
     * {@code sourceXml} is empty since there's no MXL source.
     */
    static MxlImport buildMxlImport(String displayName, List<CapturedNote> captured) {
        return new MxlImport(
                displayName,
                buildPerformance(captured),
                new TimeSignature(4, 4),
                new KeySignature(NoteName.C, Mode.MAJOR),
                /* sourceXml = */ "");
    }

    /** Display record for the ListView. */
    record CapturedNote(PitchedNote note, int velocity) {}

    private static final class NoteCell extends javafx.scene.control.ListCell<CapturedNote> {
        @Override
        protected void updateItem(CapturedNote item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
            } else {
                long ms = item.note().tickMs();
                long s = ms / 1000;
                long sub = ms % 1000;
                setText(String.format("%2d. %d:%02d.%03d  %-4s (MIDI %3d)  dur %4d ms  vel %3d",
                        getIndex() + 1, s / 60, s % 60, sub,
                        noteName(item.note().midi()), item.note().midi(),
                        item.note().durationMs(), item.velocity()));
                setStyle("-fx-font-family: monospace; -fx-font-size: 12;");
            }
        }
    }
}
