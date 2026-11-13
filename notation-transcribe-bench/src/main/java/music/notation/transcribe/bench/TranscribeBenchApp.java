package music.notation.transcribe.bench;

import javafx.animation.AnimationTimer;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import music.notation.event.Instrument;
import music.notation.performance.Performance;
import music.notation.play.ChannelSetup;
import music.notation.play.MidiPlayer;
import music.notation.play.SwingSetup;
import music.notation.play.TempoSetup;
import music.notation.structure.KeySignature;
import music.notation.structure.Mode;
import music.notation.structure.TimeSignature;
import music.notation.transcribe.AudioClip;
import music.notation.transcribe.AudioReader;
import music.notation.transcribe.Keys;
import music.notation.transcribe.PitchDetectorSpec;
import music.notation.transcribe.PitchTrack;
import music.notation.transcribe.RmvpeModel;
import music.notation.transcribe.SmoothingSpec;
import music.notation.transcribe.TimeWindow;
import music.notation.transcribe.TimingSpec;
import music.notation.transcribe.Transcriber;
import music.notation.transcribe.Transcription;
import music.notation.transcribe.TranscriptionJson;
import music.notation.transcribe.TranscriptionPiece;
import music.notation.transcribe.TranscriptionReport;
import music.notation.transcribe.TranscriptionRequest;
import music.notation.transcribe.TranscriptionWriter;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.prefs.Preferences;

/**
 * Test bench for {@code notation-transcribe}, in two modes.
 *
 * <ul>
 *   <li><b>Record</b> — record the default microphone with a live pitch
 *       monitor (YIN, for watching only). When the take stops it is saved
 *       and kept on screen; nothing is extracted.</li>
 *   <li><b>Extract</b> — choose the audio (the last take, a file or a
 *       request), the detector and the window, then press
 *       <i>Detect pitch</i>. That is the only step that runs pitch
 *       detection. Timing, segmentation and smoothing then re-run on their
 *       own from the detected pitch; changing the detector or window marks
 *       the result stale until <i>Detect pitch</i> is pressed again.</li>
 * </ul>
 *
 * Play the original, the transcription, or both to hear where they differ.
 *
 * <p>Optional argument: an audio file or a request {@code .json} to open
 * in Extract mode.</p>
 */
public final class TranscribeBenchApp extends Application {

    public static void main(String[] args) { launch(args); }

    private static final Path TAKES = Path.of(System.getProperty("user.home"), "Music", "notation-takes");
    private static final String ERROR_STYLE = "-fx-text-fill: #c03030;";

    private enum BenchMode { RECORD, EXTRACT }

    private Stage stage;
    private final BorderPane root = new BorderPane();
    private final ToolBar toolbar = new ToolBar();
    private final ToggleButton recordMode = new ToggleButton("● Record"), extractMode = new ToggleButton("♪ Extract");
    private final PitchView recordView = new PitchView(), extractView = new PitchView();
    private VBox recordPanel, extractPanel;
    private List<Node> recordTools, extractTools;
    private final TextArea report = new TextArea();
    private final Label status = new Label("Record a take, or switch to Extract and open an audio file.");
    private final Label fileLabel = new Label("—");

    // Record mode
    private final Button recordBtn = new Button("● Start recording"), toExtractBtn = new Button("Extract this take →");
    private final ComboBox<String> recKey = new ComboBox<>();
    private final Label lastTake = new Label("—");
    private AnimationTimer recTimer;

    // Extract mode
    private final TextField bpm = field("120", 60), downbeat = field("", 60),
            from = field("0", 60), to = field("0", 60),
            minNote = field("60", 60), split = field("60", 60), dip = field("0.35", 60),
            snap = field("30", 60), octave = field("250", 60), blip = field("100", 60);
    private final ComboBox<String> meter = new ComboBox<>(), key = new ComboBox<>(), grid = new ComboBox<>(),
            voice = new ComboBox<>(), detector = new ComboBox<>();
    private final Button modelBtn = new Button("Model…"), detectBtn = new Button("▶ Detect pitch");
    private final Label modelLabel = new Label(), detectState = new Label();
    private Button playTx, playBoth;

    /** Where the bench remembers a located RMVPE model between runs. */
    private static final Preferences PREFS = Preferences.userRoot().node("music/notation/transcribe");
    private static final String PREF_MODEL = "rmvpeModel";

    /** What a pitch track was detected from. A track is reused only while all three are unchanged. */
    private record TrackKey(AudioClip audio, TimeWindow window, PitchDetectorSpec detector) {}

    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "transcribe");
        t.setDaemon(true);
        return t;
    });
    private final AtomicInteger generation = new AtomicInteger();
    private final PauseTransition debounce = new PauseTransition(Duration.millis(250));

    private final TakeRecorder recorder = new TakeRecorder();
    private final ClipPlayer clipPlayer = new ClipPlayer();
    private final MidiPlayer midi = new MidiPlayer();
    private AnimationTimer playTimer;
    private long midiStartNanos;
    private double midiStartSec = Double.NaN;

    // State (FX thread)
    private AudioClip audio;
    private Path audioPath;
    private TranscriptionRequest base = TranscriptionRequest.of("take.wav", TimingSpec.DEFAULT);
    private PitchTrack detected;
    private TrackKey detectedFrom;
    private boolean detecting;
    private Transcription current;
    private boolean loadingControls;
    private BenchMode mode;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        restoreModel();
        stage.setTitle("Notation · Transcription Bench");

        recordPanel = buildRecordPanel();
        extractPanel = buildExtractPanel();
        buildToolbars();
        root.setTop(toolbar);
        root.setBottom(buildBottom());

        extractView.setOnSeek(sec -> status.setText(String.format(Locale.ROOT, "Play position %.2f s", sec)));
        recordView.setOnSeek(sec -> status.setText(String.format(Locale.ROOT, "Play position %.2f s", sec)));
        extractView.setOnDownbeat(sec -> {
            downbeat.setText(String.format(Locale.ROOT, "%.3f", sec));
            status.setText(String.format(Locale.ROOT, "Downbeat set to %.3f s", sec));
        });
        debounce.setOnFinished(e -> refresh());

        stage.setScene(new Scene(root, 1180, 760));
        stage.setOnCloseRequest(e -> shutdown());
        loadControls(BenchSettings.from(base));
        setMode(BenchMode.RECORD);
        stage.show();

        List<String> args = getParameters().getRaw();
        if (!args.isEmpty()) {
            setMode(BenchMode.EXTRACT);
            Path p = Path.of(args.getFirst());
            if (p.toString().toLowerCase(Locale.ROOT).endsWith(".json")) loadRequest(p); else openAudio(p, null);
        }
    }

    // ── modes ──────────────────────────────────────────────────────────

    private void setMode(BenchMode m) {
        mode = m;
        boolean rec = m == BenchMode.RECORD;
        (rec ? recordMode : extractMode).setSelected(true);
        toolbar.getItems().setAll(rec ? recordTools : extractTools);
        root.setLeft(rec ? recordPanel : extractPanel);
        root.setCenter(rec ? recordView : extractView);
        for (Node n : List.of(report, playTx, playBoth)) { n.setVisible(!rec); n.setManaged(!rec); }
        if (!rec) showDetectState();
    }

    // ── layout ─────────────────────────────────────────────────────────

    private void buildToolbars() {
        ToggleGroup group = new ToggleGroup();
        recordMode.setToggleGroup(group);
        extractMode.setToggleGroup(group);
        recordMode.setTooltip(new Tooltip("Record a take with a live pitch monitor"));
        extractMode.setTooltip(new Tooltip("Choose the detector, detect pitch, and turn it into notes"));
        group.selectedToggleProperty().addListener((o, was, now) -> {
            if (now == null) { was.setSelected(true); return; }              // one mode is always selected
            BenchMode m = now == recordMode ? BenchMode.RECORD : BenchMode.EXTRACT;
            if (m != mode) setMode(m);
        });

        Button open = new Button("Open audio…");
        open.setOnAction(e -> chooseAudio());
        Button loadReq = new Button("Load request…");
        loadReq.setOnAction(e -> chooseRequest());
        Button saveReq = new Button("Save request…");
        saveReq.setOnAction(e -> saveRequest());
        Button saveOut = new Button("Save outputs…");
        saveOut.setTooltip(new Tooltip("notes.txt, pitch-track.csv, transcription.json and a piece folder"));
        saveOut.setOnAction(e -> saveOutputs());
        Button fit = new Button("Fit");
        fit.setOnAction(e -> extractView.fit());
        Button zin = new Button("+"), zout = new Button("−");
        zin.setOnAction(e -> extractView.zoom(1.5));
        zout.setOnAction(e -> extractView.zoom(1 / 1.5));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        extractTools = List.of(recordMode, extractMode, new Separator(), open, loadReq, saveReq, saveOut,
                new Separator(), new Label("Zoom"), zout, zin, fit, spacer, fileLabel);

        Region spacer2 = new Region();
        HBox.setHgrow(spacer2, Priority.ALWAYS);
        Label where = new Label("Takes are saved to " + TAKES);
        where.setStyle("-fx-text-fill: #777;");
        recordTools = List.of(recordMode, extractMode, new Separator(), spacer2, where);
    }

    private VBox buildRecordPanel() {
        recKey.getItems().add("sharps");
        for (Mode m : new Mode[]{Mode.MAJOR, Mode.MINOR})
            for (int pc = 0; pc < 12; pc++) recKey.getItems().add(Keys.name(Keys.of(pc, m)));
        recKey.setValue("sharps");
        recKey.setOnAction(e -> { if (!recorder.isRecording()) recordView.setSpelling(recordKey()); });

        recordBtn.setStyle("-fx-font-size: 15px; -fx-padding: 8 18 8 18;");
        recordBtn.setMaxWidth(Double.MAX_VALUE);
        recordBtn.setOnAction(e -> toggleRecord());
        toExtractBtn.setMaxWidth(Double.MAX_VALUE);
        toExtractBtn.setDisable(true);
        toExtractBtn.setOnAction(e -> setMode(BenchMode.EXTRACT));

        GridPane g = new GridPane();
        g.setHgap(8);
        g.setVgap(6);
        int r = 0;
        r = section(g, r, "Recording");
        r = row(g, r, "Input", new Label("default microphone"), "44.1 kHz mono");
        r = row(g, r, "Note names", recKey, "spelling for the monitor");
        r = section(g, r, "Last take");
        g.add(lastTake, 0, r++, 3, 1);
        lastTake.setWrapText(true);
        lastTake.setMaxWidth(260);

        Label about = new Label("""
                The monitor shows pitch as you sing (YIN,
                every 10 ms) — for watching only.
                Nothing is extracted while recording: when
                the take stops, go to Extract, choose the
                detector and press Detect pitch.""");
        about.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");
        VBox box = new VBox(12, recordBtn, g, toExtractBtn, about);
        box.setPadding(new Insets(10));
        box.setPrefWidth(290);
        return box;
    }

    private VBox buildExtractPanel() {
        meter.getItems().addAll("4/4", "3/4", "2/4", "6/8", "12/8");
        key.getItems().add(TimingSpec.AUTO);
        for (Mode m : new Mode[]{Mode.MAJOR, Mode.MINOR})
            for (int pc = 0; pc < 12; pc++) key.getItems().add(Keys.name(Keys.of(pc, m)));
        key.setEditable(true);
        grid.getItems().addAll("1/4", "1/8", "1/12", "1/16", "1/24", "1/32");
        voice.getItems().addAll("Piano", "Flute", "Voice oohs", "Square lead");
        voice.setValue("Piano");
        detector.getItems().addAll(BenchSettings.YIN, BenchSettings.RMVPE);
        detector.setValue(BenchSettings.YIN);
        modelBtn.setOnAction(e -> chooseModel());
        modelBtn.setTooltip(new Tooltip("Locate rmvpe.onnx (remembered for next time)"));
        modelLabel.setStyle("-fx-text-fill: #777; -fx-font-size: 11px;");
        modelLabel.setWrapText(true);
        modelLabel.setMaxWidth(150);
        updateModelLabel();
        detectBtn.setOnAction(e -> detect());
        detectBtn.setMaxWidth(Double.MAX_VALUE);
        detectState.setWrapText(true);
        detectState.setMaxWidth(150);
        detectState.setStyle("-fx-font-size: 11px;");

        GridPane g = new GridPane();
        g.setHgap(8);
        g.setVgap(6);
        int r = 0;
        r = section(g, r, "Pitch detection");
        r = row(g, r, "Detector", detector, "yin: clean solo voice · rmvpe: robust, works over backing");
        g.add(modelBtn, 1, r);
        g.add(modelLabel, 2, r++);
        r = row(g, r, "From", from, "s");
        r = row(g, r, "To", to, "s · 0 = end");
        g.add(detectBtn, 0, r, 2, 1);
        g.add(detectState, 2, r++);
        r = section(g, r, "Timing");
        r = row(g, r, "Tempo ♩", bpm, "bpm");
        r = row(g, r, "Downbeat", downbeat, "s · blank = first note · double-click the view");
        r = row(g, r, "Meter", meter, null);
        r = row(g, r, "Key", key, null);
        r = row(g, r, "Grid", grid, "whole-note fraction");
        r = section(g, r, "Segmentation");
        r = row(g, r, "Min note", minNote, "ms");
        r = row(g, r, "Split at", split, "cents from the note");
        r = row(g, r, "Dip ratio", dip, "0 = never split on loudness");
        r = section(g, r, "Smoothing");
        r = row(g, r, "Snap to key", snap, "¢ off-pitch towards an in-key note · 0 = off");
        r = row(g, r, "Octave fix", octave, "ms, notes up to · 0 = off");
        r = row(g, r, "Blips", blip, "ms, slide / scoop fragments up to · 0 = off");
        r = section(g, r, "Playback");
        row(g, r, "Sound", voice, null);

        for (TextField f : List.of(bpm, downbeat, from, to, minNote, split, dip, snap, octave, blip)) {
            f.textProperty().addListener((o, a, b) -> changed());
        }
        for (ComboBox<String> c : List.of(meter, key, grid, detector)) c.valueProperty().addListener((o, a, b) -> changed());

        Label legend = new Label("""
                dots — detected pitch per 10 ms
                filled boxes — notes as sung
                dashed boxes — notes on the grid
                purple — smoothing: sung pitch → written
                red line — downbeat · green — play from
                wheel scrolls · Ctrl+wheel zooms
                click: play position · double-click: downbeat""");
        legend.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");
        VBox box = new VBox(12, g, legend);
        box.setPadding(new Insets(10));
        box.setPrefWidth(290);
        return box;
    }

    private VBox buildBottom() {
        Button playOrig = new Button("▶ Original"), stop = new Button("■ Stop");
        playTx = new Button("▶ Transcription");
        playBoth = new Button("▶ Both");
        playOrig.setOnAction(e -> playOriginal());
        playTx.setOnAction(e -> playTranscription(false));
        playBoth.setOnAction(e -> playTranscription(true));
        stop.setOnAction(e -> stopPlayback());
        HBox transport = new HBox(8, playOrig, playTx, playBoth, stop, new Separator(), status);
        transport.setAlignment(Pos.CENTER_LEFT);
        transport.setPadding(new Insets(6, 10, 6, 10));
        report.setEditable(false);
        report.setFont(Font.font("Consolas", 12));
        report.setPrefRowCount(10);
        return new VBox(transport, report);
    }

    private static int section(GridPane g, int r, String title) {
        Label l = new Label(title);
        l.setStyle("-fx-font-weight: bold; -fx-padding: 6 0 0 0;");
        g.add(l, 0, r, 3, 1);
        return r + 1;
    }

    private static int row(GridPane g, int r, String label, Node control, String hint) {
        g.add(new Label(label), 0, r);
        g.add(control, 1, r);
        if (hint != null) {
            Label h = new Label(hint);
            h.setStyle("-fx-text-fill: #777; -fx-font-size: 11px;");
            h.setWrapText(true);
            h.setMaxWidth(150);
            g.add(h, 2, r);
        }
        return r + 1;
    }

    private static TextField field(String text, double width) {
        TextField f = new TextField(text);
        f.setPrefWidth(width);
        return f;
    }

    private void info(String text) {
        status.setStyle("");
        status.setText(text);
    }

    private void error(String text) {
        status.setStyle(ERROR_STYLE);
        status.setText("⚠ " + text);
    }

    // ── settings ↔ controls ────────────────────────────────────────────

    private void loadControls(BenchSettings s) {
        loadingControls = true;
        try {
            bpm.setText(Integer.toString(s.bpm()));
            downbeat.setText(s.downbeatSec() == null ? "" : fmt(s.downbeatSec()));
            meter.setValue(s.timeSignature().beats() + "/" + s.timeSignature().beatValue());
            key.setValue(s.key().equalsIgnoreCase(TimingSpec.AUTO) ? TimingSpec.AUTO : Keys.name(Keys.parse(s.key())));
            grid.setValue(s.grid().numerator() + "/" + s.grid().denominator());
            from.setText(fmt(s.fromSec()));
            to.setText(fmt(s.toSec()));
            minNote.setText(fmt(s.minNoteMs()));
            split.setText(fmt(s.splitCents()));
            dip.setText(fmt(s.dipRatio()));
            detector.setValue(s.detector());
            snap.setText(fmt(s.smoothing().snapCents()));
            octave.setText(fmt(s.smoothing().octaveMaxMs()));
            blip.setText(fmt(s.smoothing().blipMaxMs()));
        } finally {
            loadingControls = false;
        }
    }

    private BenchSettings readControls() {
        String[] ts = meter.getValue().split("/");
        String[] gr = grid.getValue().split("/");
        String db = downbeat.getText().trim();
        return new BenchSettings(Integer.parseInt(bpm.getText().trim()), db.isEmpty() ? null : Double.parseDouble(db),
                new TimeSignature(Integer.parseInt(ts[0]), Integer.parseInt(ts[1])),
                key.getValue() == null ? TimingSpec.AUTO : key.getValue().trim(),
                music.notation.duration.Duration.of(Long.parseLong(gr[0]), Long.parseLong(gr[1])),
                Double.parseDouble(from.getText().trim()), Double.parseDouble(to.getText().trim()),
                Double.parseDouble(minNote.getText().trim()), Double.parseDouble(split.getText().trim()),
                Double.parseDouble(dip.getText().trim()), detector.getValue(),
                new SmoothingSpec(Double.parseDouble(snap.getText().trim()), Double.parseDouble(octave.getText().trim()),
                        Double.parseDouble(blip.getText().trim()), base.smoothing().neighbourGapMs()));
    }

    private void changed() {
        if (loadingControls || audio == null) return;
        debounce.playFromStart();
    }

    private String title() {
        if (audioPath == null) return "take";
        String n = audioPath.getFileName().toString();
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }

    private TranscriptionRequest currentRequest() {
        return readControls().apply(base, audioPath.toString(), title());
    }

    /** The current request, or {@code null} after reporting why the controls do not make one. */
    private TranscriptionRequest validRequest() {
        try {
            return currentRequest();
        } catch (RuntimeException ex) {
            error(ex.getMessage() == null ? ex.toString() : ex.getMessage());
            return null;
        }
    }

    // ── pitch detection (explicit) ─────────────────────────────────────

    /** Run the chosen detector over the window — the only place pitch detection happens in Extract. */
    private void detect() {
        if (audio == null) { error("Record a take or open an audio file first."); return; }
        if (detecting) return;
        TranscriptionRequest req = validRequest();
        if (req == null) return;
        detecting = true;
        int gen = generation.incrementAndGet();
        detectBtn.setDisable(true);
        info("Detecting pitch (" + detector.getValue() + ") over "
                + String.format(Locale.ROOT, "%.1f s", audio.slice(req.window()).durationSec()) + "…");
        showDetectState();
        AudioClip a = audio;
        worker.submit(() -> {
            try {
                long t0 = System.nanoTime();
                PitchTrack track = Transcriber.track(req.detector(), a.slice(req.window()));
                long ms = (System.nanoTime() - t0) / 1_000_000;
                Platform.runLater(() -> {
                    detecting = false;
                    detectBtn.setDisable(false);
                    if (a != audio) return;                               // audio replaced meanwhile
                    detected = track;
                    detectedFrom = new TrackKey(a, req.window(), req.detector());
                    showDetectState();
                    info(String.format(Locale.ROOT, "Pitch detected (%s) in %d ms.", req.detector().describe(), ms));
                    refresh();                                            // notes from the current controls
                });
            } catch (RmvpeModel.ModelNotFoundException ex) {
                Platform.runLater(() -> {
                    detecting = false;
                    detectBtn.setDisable(false);
                    showDetectState();
                    if (gen == generation.get()) error("RMVPE model not found — click Model… to locate rmvpe.onnx");
                });
            } catch (RuntimeException ex) {
                Platform.runLater(() -> {
                    detecting = false;
                    detectBtn.setDisable(false);
                    showDetectState();
                    if (gen == generation.get()) error(ex.getMessage());
                });
            }
        });
    }

    /** Is the detected pitch track still the one the controls ask for? */
    private boolean upToDate(TranscriptionRequest req) {
        return detected != null && new TrackKey(audio, req.window(), req.detector()).equals(detectedFrom);
    }

    /** Small status next to the Detect pitch button. */
    private void showDetectState() {
        String text, style;
        TranscriptionRequest req = audio == null ? null : quietRequest();
        if (detecting) { text = "running…"; style = "-fx-text-fill: #777;"; }
        else if (audio == null) { text = "no audio yet"; style = "-fx-text-fill: #777;"; }
        else if (detected == null) { text = "not run yet"; style = "-fx-text-fill: #d08a00;"; }
        else if (req != null && !upToDate(req)) { text = "out of date — detector or window changed"; style = "-fx-text-fill: #d08a00;"; }
        else { text = "✓ up to date"; style = "-fx-text-fill: #2e8b57;"; }
        detectState.setText(text);
        detectState.setStyle("-fx-font-size: 11px; " + style);
    }

    private TranscriptionRequest quietRequest() {
        try { return currentRequest(); } catch (RuntimeException ex) { return null; }
    }

    /** Notes, key and grid from the detected pitch track — cheap; re-runs on every control change. */
    private void refresh() {
        if (audio == null) return;
        TranscriptionRequest req = validRequest();
        showDetectState();
        if (req == null || detecting) return;
        if (detected == null) { info("Choose the detector and press Detect pitch."); return; }
        if (!upToDate(req)) { info("Detector or window changed — press Detect pitch to run it again."); return; }
        info("Transcribing…");
        int gen = generation.incrementAndGet();
        AudioClip cut = audio.slice(req.window());
        PitchTrack track = detected;
        worker.submit(() -> {
            if (gen != generation.get()) return;                       // superseded while queued
            try {
                long t0 = System.nanoTime();
                Transcription t = Transcriber.fromTrack(req, cut, track);
                long ms = (System.nanoTime() - t0) / 1_000_000;
                Platform.runLater(() -> {
                    if (gen != generation.get()) return;
                    current = t;
                    extractView.setTranscription(t);
                    report.setText(TranscriptionReport.render(t));
                    info(String.format(Locale.ROOT, "%d notes · key %s · %d ms", t.quantized().size(), Keys.name(t.key()), ms));
                });
            } catch (RuntimeException ex) {
                Platform.runLater(() -> { if (gen == generation.get()) error(ex.getMessage()); });
            }
        });
    }

    // ── RMVPE model ────────────────────────────────────────────────────

    /** Use a model remembered from an earlier run, unless one is configured already. */
    private static void restoreModel() {
        String saved = PREFS.get(PREF_MODEL, null);
        if (saved != null && System.getProperty(RmvpeModel.PROPERTY) == null && !RmvpeModel.available(null)
                && Files.isRegularFile(Path.of(saved))) {
            System.setProperty(RmvpeModel.PROPERTY, saved);
        }
    }

    private void chooseModel() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Locate the RMVPE model");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("ONNX model", "*.onnx"));
        Path where = RmvpeModel.locate(null);
        if (where.getParent() != null && Files.isDirectory(where.getParent()))
            fc.setInitialDirectory(where.getParent().toFile());
        File f = fc.showOpenDialog(stage);
        if (f == null) return;
        System.setProperty(RmvpeModel.PROPERTY, f.getAbsolutePath());
        PREFS.put(PREF_MODEL, f.getAbsolutePath());
        updateModelLabel();
        detector.setValue(BenchSettings.RMVPE);
        showDetectState();
    }

    private void updateModelLabel() {
        Path p = RmvpeModel.locate(null);
        modelLabel.setText(RmvpeModel.available(null) ? p.getFileName() + " ✓" : "rmvpe.onnx not found");
        modelLabel.setTooltip(new Tooltip(p.toString()));
    }

    // ── files ──────────────────────────────────────────────────────────

    private void chooseAudio() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Open audio");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Audio", "*.wav", "*.aif", "*.aiff", "*.mp3", "*.m4a", "*.flac", "*.ogg"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
        if (audioPath != null && audioPath.getParent() != null) fc.setInitialDirectory(audioPath.getParent().toFile());
        File f = fc.showOpenDialog(stage);
        if (f != null) openAudio(f.toPath(), null);
    }

    /** Load audio in the background; {@code settings} (from a request) replaces the controls when given. */
    private void openAudio(Path path, BenchSettings settings) {
        stopPlayback();
        info("Reading " + path.getFileName() + "…");
        worker.submit(() -> {
            try {
                AudioClip clip = AudioReader.read(path);
                Platform.runLater(() -> {
                    audioLoaded(path, clip, settings);
                    info("Loaded " + path.getFileName() + " — choose the detector and press Detect pitch.");
                });
            } catch (RuntimeException ex) {
                Platform.runLater(() -> error(ex.getMessage()));
            }
        });
    }

    /** Make {@code clip} the Extract source. Nothing is detected until Detect pitch is pressed. */
    private void audioLoaded(Path path, AudioClip clip, BenchSettings settings) {
        generation.incrementAndGet();                                    // drop results for the old audio
        audio = clip;
        audioPath = path.toAbsolutePath();
        detected = null;
        detectedFrom = null;
        current = null;
        fileLabel.setText(String.format(Locale.ROOT, "%s · %.1f s · %d Hz", path.getFileName(), clip.durationSec(), clip.sampleRate()));
        if (settings != null) loadControls(settings);
        extractView.setTranscription(null);
        extractView.setAudioExtent(clip.offsetSec(), clip.durationSec());
        extractView.fit();
        report.clear();
        showDetectState();
    }

    private void chooseRequest() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Load transcription request");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Request JSON", "*.json"));
        File f = fc.showOpenDialog(stage);
        if (f != null) loadRequest(f.toPath());
    }

    private void loadRequest(Path file) {
        try {
            TranscriptionRequest r = TranscriptionJson.readRequest(file);
            Path a = Path.of(r.audio());
            if (!a.isAbsolute()) a = file.toAbsolutePath().getParent().resolve(a).normalize();
            base = r;
            if (!Files.isRegularFile(a)) {
                loadControls(BenchSettings.from(r));
                error("audio not found: " + a);
                return;
            }
            openAudio(a, BenchSettings.from(r));
        } catch (RuntimeException ex) {
            error(ex.getMessage());
        }
    }

    private void saveRequest() {
        if (audioPath == null) { error("Open audio first."); return; }
        FileChooser fc = new FileChooser();
        fc.setTitle("Save transcription request");
        fc.setInitialFileName(TranscriptionWriter.slug(title()) + "-request.json");
        fc.setInitialDirectory(audioPath.getParent().toFile());
        File f = fc.showSaveDialog(stage);
        if (f == null) return;
        try {
            TranscriptionJson.write(f.toPath(), currentRequest());
            info("Saved request → " + f);
        } catch (RuntimeException ex) {
            error(ex.getMessage());
        }
    }

    private void saveOutputs() {
        if (current == null) { error("Nothing transcribed yet — press Detect pitch."); return; }
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Choose a parent folder for the outputs");
        if (audioPath != null) dc.setInitialDirectory(audioPath.getParent().toFile());
        File parent = dc.showDialog(stage);
        if (parent == null) return;
        Path out = parent.toPath().resolve(TranscriptionWriter.slug(current.title()) + "-transcription");
        try {
            TranscriptionWriter.write(current, out);
            info("Saved outputs → " + out);
        } catch (RuntimeException ex) {
            error(ex.getMessage());
        }
    }

    // ── recording ──────────────────────────────────────────────────────

    private LivePitchTracker tracker;

    private void toggleRecord() {
        if (recorder.isRecording()) stopRecording(); else startRecording();
    }

    private void startRecording() {
        stopPlayback();
        try {
            // Monitor only: YIN with the request's settings when it uses YIN, else the defaults.
            var yin = base.detector() instanceof PitchDetectorSpec.Yin y ? y : PitchDetectorSpec.Yin.DEFAULT;
            tracker = new LivePitchTracker(TakeRecorder.RATE, yin);
            recorder.start(tracker::accept);
        } catch (Exception ex) {
            error("microphone unavailable: " + ex.getMessage());
            return;
        }
        recordView.startLive(tracker, recordKey());
        recordBtn.setText("■ Stop recording");
        for (Node n : List.of(extractMode, toExtractBtn, recKey)) n.setDisable(true);
        LivePitchTracker t = tracker;
        recTimer = new AnimationTimer() {
            @Override public void handle(long now) {
                recordView.liveTick(recorder.seconds());
                int bars = (int) Math.min(30, Math.sqrt(t.level()) * 60);
                info(String.format(Locale.ROOT, "Recording %.1f s  %s", recorder.seconds(), "▮".repeat(bars)));
            }
        };
        recTimer.start();
    }

    private void stopRecording() {
        recTimer.stop();
        AudioClip take = recorder.stop();
        recordView.stopLive();
        recordBtn.setText("● Start recording");
        for (Node n : List.of(extractMode, recKey)) n.setDisable(false);
        recordView.showTrack(tracker.toTrack(), recordKey());

        Path file = TAKES.resolve("take-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".wav");
        try {
            Pcm.writeWav(file, take);
        } catch (Exception ex) {
            error("could not save take: " + ex.getMessage());
            return;
        }
        audioLoaded(file, take, null);
        lastTake.setText(String.format(Locale.ROOT, "%s · %.1f s", file.getFileName(), take.durationSec()));
        toExtractBtn.setDisable(false);
        info("Saved take → " + file + " · switch to Extract to detect pitch.");
    }

    /** Spelling for the monitor's note names: the chosen key, or none (sharps). */
    private KeySignature recordKey() {
        String k = recKey.getValue();
        return k == null || k.equals("sharps") ? null : Keys.parse(k);
    }

    // ── playback ───────────────────────────────────────────────────────

    private PitchView activeView() { return mode == BenchMode.RECORD ? recordView : extractView; }

    private void playOriginal() {
        if (audio == null || recorder.isRecording()) return;
        stopPlayback();
        double seek = activeView().seekSec();
        double fromSec = Double.isNaN(seek) ? audio.offsetSec() : seek;
        try {
            clipPlayer.prepare(audio, fromSec);
            clipPlayer.start();
            startTimer();
        } catch (Exception ex) {
            error(ex.getMessage());
        }
    }

    /** Transcription as MIDI from bar 1; with {@code withOriginal} the audio plays alongside from the same point. */
    private void playTranscription(boolean withOriginal) {
        if (current == null || current.quantized().isEmpty()) return;
        stopPlayback();
        try {
            double barOne = BarClock.of(current).barOneSec();
            if (withOriginal) clipPlayer.prepare(audio, barOne);
            Performance perf = TranscriptionPiece.performance(current);
            var channels = ChannelSetup.fromPerformanceTracks(perf.score().tracks(),
                    List.of(instrument()), List.of(110), List.of(ChannelSetup.PAN_CENTER));
            midi.start(perf, channels, TempoSetup.unity(), SwingSetup.OFF);
            midiStartNanos = System.nanoTime();
            midiStartSec = barOne;
            if (withOriginal) clipPlayer.start();
            startTimer();
        } catch (Exception ex) {
            error("playback failed: " + ex.getMessage());
        }
    }

    private Instrument instrument() {
        return switch (voice.getValue()) {
            case "Flute" -> Instrument.FLUTE;
            case "Voice oohs" -> Instrument.VOICE_OOHS;
            case "Square lead" -> Instrument.SYNTH_LEAD_SQUARE;
            default -> Instrument.ACOUSTIC_GRAND_PIANO;
        };
    }

    private void startTimer() {
        if (playTimer != null) playTimer.stop();
        PitchView v = activeView();
        playTimer = new AnimationTimer() {
            @Override public void handle(long now) {
                double sec;
                if (clipPlayer.isPlaying()) sec = clipPlayer.positionSec();
                else if (!Double.isNaN(midiStartSec) && midi.isPlaying()) sec = midiStartSec + (now - midiStartNanos) / 1e9;
                else { stopPlayback(); return; }
                v.setPlayPosition(sec);
                v.follow(sec);
            }
        };
        playTimer.start();
    }

    private void stopPlayback() {
        if (playTimer != null) { playTimer.stop(); playTimer = null; }
        clipPlayer.stop();
        try { midi.stop(); } catch (RuntimeException ignored) { }
        midiStartSec = Double.NaN;
        recordView.setPlayPosition(Double.NaN);
        extractView.setPlayPosition(Double.NaN);
    }

    private void shutdown() {
        stopPlayback();
        if (recorder.isRecording()) recorder.stop();
        try { midi.dispose(); } catch (RuntimeException ignored) { }
        worker.shutdownNow();
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? Long.toString((long) v) : String.format(Locale.ROOT, "%.3f", v).replaceAll("0+$", "");
    }
}
