package music.notation.transcribe.bench;

import javafx.animation.AnimationTimer;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
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
import music.notation.structure.Mode;
import music.notation.structure.TimeSignature;
import music.notation.transcribe.AudioClip;
import music.notation.transcribe.AudioReader;
import music.notation.transcribe.Keys;
import music.notation.transcribe.PitchTrack;
import music.notation.transcribe.RmvpeModel;
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
 * Test bench for {@code notation-transcribe}. Open an audio file (or
 * record a take), adjust the parameters on the left — every change
 * re-runs the transcription — and compare the pitch curve, the
 * segmented notes and the grid in the view. Play the original, the
 * transcription, or both together to hear where they differ.
 *
 * <p>Optional argument: an audio file or a request {@code .json} to open
 * at start.</p>
 */
public final class TranscribeBenchApp extends Application {

    public static void main(String[] args) { launch(args); }

    private static final Path TAKES = Path.of(System.getProperty("user.home"), "Music", "notation-takes");

    private Stage stage;
    private final PitchView view = new PitchView();
    private final TextArea report = new TextArea();
    private final Label status = new Label("Open an audio file or record a take.");
    private final Label fileLabel = new Label("—");

    private final TextField bpm = field("120", 60), downbeat = field("", 60),
            from = field("0", 60), to = field("0", 60),
            minNote = field("60", 60), split = field("60", 60), dip = field("0.35", 60);
    private final ComboBox<String> meter = new ComboBox<>(), key = new ComboBox<>(), grid = new ComboBox<>(),
            voice = new ComboBox<>(), detector = new ComboBox<>();
    private final Button recordBtn = new Button("● Record"), modelBtn = new Button("Model…");
    private final Label modelLabel = new Label();

    /** Where the bench remembers a located RMVPE model between runs. */
    private static final Preferences PREFS = Preferences.userRoot().node("music/notation/transcribe");
    private static final String PREF_MODEL = "rmvpeModel";

    /** The last pitch track, reused while only segmentation or timing change. Worker thread only. */
    private record TrackKey(AudioClip audio, TimeWindow window, music.notation.transcribe.PitchDetectorSpec detector) {}
    private TrackKey trackKey;
    private PitchTrack cachedTrack;

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

    private AudioClip audio;
    private Path audioPath;
    private TranscriptionRequest base = TranscriptionRequest.of("take.wav", TimingSpec.DEFAULT);
    private Transcription current;
    private boolean loadingControls;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        restoreModel();
        stage.setTitle("Notation · Transcription Bench");

        BorderPane root = new BorderPane();
        root.setTop(buildToolbar());
        root.setLeft(buildParams());
        root.setCenter(view);
        root.setBottom(buildBottom());

        view.setOnSeek(sec -> status.setText(String.format(Locale.ROOT, "Play position %.2f s", sec)));
        view.setOnDownbeat(sec -> {
            downbeat.setText(String.format(Locale.ROOT, "%.3f", sec));
            status.setText(String.format(Locale.ROOT, "Downbeat set to %.3f s", sec));
        });
        debounce.setOnFinished(e -> runTranscription());

        stage.setScene(new Scene(root, 1180, 760));
        stage.setOnCloseRequest(e -> shutdown());
        stage.show();
        loadControls(BenchSettings.from(base));

        List<String> args = getParameters().getRaw();
        if (!args.isEmpty()) {
            Path p = Path.of(args.getFirst());
            if (p.toString().toLowerCase(Locale.ROOT).endsWith(".json")) loadRequest(p); else openAudio(p, null);
        }
    }

    // ── layout ─────────────────────────────────────────────────────────

    private ToolBar buildToolbar() {
        Button open = new Button("Open audio…");
        open.setOnAction(e -> chooseAudio());
        recordBtn.setOnAction(e -> toggleRecord());
        recordBtn.setTooltip(new Tooltip("Record the default microphone; the take is saved to " + TAKES));
        Button loadReq = new Button("Load request…");
        loadReq.setOnAction(e -> chooseRequest());
        Button saveReq = new Button("Save request…");
        saveReq.setOnAction(e -> saveRequest());
        Button saveOut = new Button("Save outputs…");
        saveOut.setTooltip(new Tooltip("notes.txt, pitch-track.csv, transcription.json and a piece folder"));
        saveOut.setOnAction(e -> saveOutputs());
        Button fit = new Button("Fit");
        fit.setOnAction(e -> view.fit());
        Button zin = new Button("+"), zout = new Button("−");
        zin.setOnAction(e -> view.zoom(1.5));
        zout.setOnAction(e -> view.zoom(1 / 1.5));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return new ToolBar(open, recordBtn, new Separator(), loadReq, saveReq, saveOut, new Separator(),
                new Label("Zoom"), zout, zin, fit, spacer, fileLabel);
    }

    private VBox buildParams() {
        meter.getItems().addAll("4/4", "3/4", "2/4", "6/8", "12/8");
        key.getItems().add(TimingSpec.AUTO);
        for (Mode mode : new Mode[]{Mode.MAJOR, Mode.MINOR})
            for (int pc = 0; pc < 12; pc++) key.getItems().add(Keys.name(Keys.of(pc, mode)));
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

        GridPane g = new GridPane();
        g.setHgap(8);
        g.setVgap(6);
        int r = 0;
        r = section(g, r, "Pitch");
        r = row(g, r, "Detector", detector, "yin: clean solo voice · rmvpe: robust, works over backing");
        g.add(modelBtn, 1, r);
        g.add(modelLabel, 2, r++);
        r = section(g, r, "Timing");
        r = row(g, r, "Tempo ♩", bpm, "bpm");
        r = row(g, r, "Downbeat", downbeat, "s · blank = first note · double-click the view");
        r = row(g, r, "Meter", meter, null);
        r = row(g, r, "Key", key, null);
        r = row(g, r, "Grid", grid, "whole-note fraction");
        r = section(g, r, "Window");
        r = row(g, r, "From", from, "s");
        r = row(g, r, "To", to, "s · 0 = end");
        r = section(g, r, "Segmentation");
        r = row(g, r, "Min note", minNote, "ms");
        r = row(g, r, "Split at", split, "cents from the note");
        r = row(g, r, "Dip ratio", dip, "0 = never split on loudness");
        r = section(g, r, "Playback");
        row(g, r, "Sound", voice, null);

        for (TextField f : List.of(bpm, downbeat, from, to, minNote, split, dip)) {
            f.textProperty().addListener((o, a, b) -> changed());
        }
        for (ComboBox<String> c : List.of(meter, key, grid, detector)) c.valueProperty().addListener((o, a, b) -> changed());

        Label legend = new Label("""
                dots — detected pitch per 10 ms
                filled boxes — notes as sung
                dashed boxes — notes on the grid
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
        Button playOrig = new Button("▶ Original"), playTx = new Button("▶ Transcription"),
                playBoth = new Button("▶ Both"), stop = new Button("■ Stop");
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

    private static int row(GridPane g, int r, String label, javafx.scene.Node control, String hint) {
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
                Double.parseDouble(dip.getText().trim()), detector.getValue());
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

    // ── transcription ──────────────────────────────────────────────────

    private void runTranscription() {
        if (audio == null) return;
        TranscriptionRequest req;
        try {
            req = currentRequest();
        } catch (RuntimeException ex) {
            status.setText("⚠ " + ex.getMessage());
            status.setStyle("-fx-text-fill: #c03030;");
            return;
        }
        status.setStyle("");
        status.setText("Transcribing…");
        int gen = generation.incrementAndGet();
        AudioClip a = audio;
        worker.submit(() -> {
            if (gen != generation.get()) return;                   // superseded while queued
            try {
                long t0 = System.nanoTime();
                AudioClip cut = a.slice(req.window());
                var k = new TrackKey(a, req.window(), req.detector());
                boolean reuse = k.equals(trackKey);
                if (!reuse) {
                    Platform.runLater(() -> { if (gen == generation.get()) status.setText("Tracking pitch (" + detector.getValue() + ")…"); });
                    cachedTrack = Transcriber.track(req.detector(), cut);
                    trackKey = k;
                }
                Transcription t = Transcriber.fromTrack(req, cut, cachedTrack);
                long ms = (System.nanoTime() - t0) / 1_000_000;
                Platform.runLater(() -> {
                    if (gen != generation.get()) return;
                    current = t;
                    view.setTranscription(t);
                    report.setText(TranscriptionReport.render(t));
                    status.setText(String.format(Locale.ROOT, "%d notes · key %s · %d ms%s",
                            t.quantized().size(), Keys.name(t.key()), ms, reuse ? " (pitch track reused)" : ""));
                });
            } catch (RmvpeModel.ModelNotFoundException ex) {
                Platform.runLater(() -> {
                    if (gen != generation.get()) return;
                    status.setText("⚠ RMVPE model not found — click Model… to locate rmvpe.onnx");
                    status.setStyle("-fx-text-fill: #c03030;");
                });
            } catch (RuntimeException ex) {
                Platform.runLater(() -> {
                    if (gen != generation.get()) return;
                    status.setText("⚠ " + ex.getMessage());
                    status.setStyle("-fx-text-fill: #c03030;");
                });
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
        Path current = RmvpeModel.locate(null);
        if (current.getParent() != null && Files.isDirectory(current.getParent()))
            fc.setInitialDirectory(current.getParent().toFile());
        File f = fc.showOpenDialog(stage);
        if (f == null) return;
        System.setProperty(RmvpeModel.PROPERTY, f.getAbsolutePath());
        PREFS.put(PREF_MODEL, f.getAbsolutePath());
        updateModelLabel();
        if (!BenchSettings.RMVPE.equals(detector.getValue())) detector.setValue(BenchSettings.RMVPE);
        else changed();
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
        status.setText("Reading " + path.getFileName() + "…");
        worker.submit(() -> {
            try {
                AudioClip clip = AudioReader.read(path);
                Platform.runLater(() -> audioLoaded(path, clip, settings));
            } catch (RuntimeException ex) {
                Platform.runLater(() -> status.setText("⚠ " + ex.getMessage()));
            }
        });
    }

    private void audioLoaded(Path path, AudioClip clip, BenchSettings settings) {
        audio = clip;
        audioPath = path.toAbsolutePath();
        fileLabel.setText(String.format(Locale.ROOT, "%s · %.1f s · %d Hz", path.getFileName(), clip.durationSec(), clip.sampleRate()));
        if (settings != null) loadControls(settings);
        view.setAudioExtent(clip.offsetSec(), clip.durationSec());
        view.fit();
        runTranscription();
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
                status.setText("⚠ audio not found: " + a);
                return;
            }
            openAudio(a, BenchSettings.from(r));
        } catch (RuntimeException ex) {
            status.setText("⚠ " + ex.getMessage());
        }
    }

    private void saveRequest() {
        if (audioPath == null) { status.setText("Open audio first."); return; }
        FileChooser fc = new FileChooser();
        fc.setTitle("Save transcription request");
        fc.setInitialFileName(TranscriptionWriter.slug(title()) + "-request.json");
        fc.setInitialDirectory(audioPath.getParent().toFile());
        File f = fc.showSaveDialog(stage);
        if (f == null) return;
        try {
            TranscriptionJson.write(f.toPath(), currentRequest());
            status.setText("Saved request → " + f);
        } catch (RuntimeException ex) {
            status.setText("⚠ " + ex.getMessage());
        }
    }

    private void saveOutputs() {
        if (current == null) { status.setText("Nothing transcribed yet."); return; }
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Choose a parent folder for the outputs");
        if (audioPath != null) dc.setInitialDirectory(audioPath.getParent().toFile());
        File parent = dc.showDialog(stage);
        if (parent == null) return;
        Path out = parent.toPath().resolve(TranscriptionWriter.slug(current.title()) + "-transcription");
        try {
            TranscriptionWriter.write(current, out);
            status.setText("Saved outputs → " + out);
        } catch (RuntimeException ex) {
            status.setText("⚠ " + ex.getMessage());
        }
    }

    // ── recording ──────────────────────────────────────────────────────

    private AnimationTimer recTimer;

    private void toggleRecord() {
        if (recorder.isRecording()) {
            recTimer.stop();
            AudioClip take = recorder.stop();
            recordBtn.setText("● Record");
            Path file = TAKES.resolve("take-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".wav");
            try {
                Pcm.writeWav(file, take);
            } catch (Exception ex) {
                status.setText("⚠ could not save take: " + ex.getMessage());
                return;
            }
            status.setText("Saved take → " + file);
            audioLoaded(file, take, null);
            return;
        }
        stopPlayback();
        try {
            double[] level = {0};
            recorder.start(rms -> level[0] = rms);
            recordBtn.setText("■ Stop recording");
            recTimer = new AnimationTimer() {
                @Override public void handle(long now) {
                    int bars = (int) Math.min(30, Math.sqrt(level[0]) * 60);
                    status.setText(String.format(Locale.ROOT, "Recording %.1f s  %s", recorder.seconds(), "▮".repeat(bars)));
                }
            };
            recTimer.start();
        } catch (Exception ex) {
            status.setText("⚠ microphone unavailable: " + ex.getMessage());
        }
    }

    // ── playback ───────────────────────────────────────────────────────

    private void playOriginal() {
        if (audio == null) return;
        stopPlayback();
        double fromSec = Double.isNaN(view.seekSec()) ? audio.offsetSec() : view.seekSec();
        try {
            clipPlayer.prepare(audio, fromSec);
            clipPlayer.start();
            startTimer();
        } catch (Exception ex) {
            status.setText("⚠ " + ex.getMessage());
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
            status.setText("⚠ playback failed: " + ex.getMessage());
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
        playTimer = new AnimationTimer() {
            @Override public void handle(long now) {
                double sec;
                if (clipPlayer.isPlaying()) sec = clipPlayer.positionSec();
                else if (!Double.isNaN(midiStartSec) && midi.isPlaying()) sec = midiStartSec + (now - midiStartNanos) / 1e9;
                else { stopPlayback(); return; }
                view.setPlayPosition(sec);
                view.follow(sec);
            }
        };
        playTimer.start();
    }

    private void stopPlayback() {
        if (playTimer != null) { playTimer.stop(); playTimer = null; }
        clipPlayer.stop();
        try { midi.stop(); } catch (RuntimeException ignored) { }
        midiStartSec = Double.NaN;
        view.setPlayPosition(Double.NaN);
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
