package music.notation.mixer;

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
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import music.notation.play.AudioMixer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Dedicated JavaFX application for combining multiple WAV streams into a
 * single mixed output, with stacked waveform visualisation to verify
 * timing alignment before the mix runs.
 *
 * <h2>The workflow it serves</h2>
 *
 * <p>Vocal synthesis is outsourced (e.g. to ACE Studio), backing
 * instrumentation is rendered internally via the existing
 * {@link music.notation.play.AudioRenderer}. The user loads the two (or
 * more) resulting WAVs into this app, verifies their alignment visually
 * (and audibly, via playback), adjusts per-track gain, and exports the
 * mixed WAV.</p>
 *
 * <h2>Visual structure</h2>
 *
 * <pre>
 *   ┌─ Mixer ──────────────────────────────────────────────────────────────┐
 *   │ [Add WAV...] [Play] [Stop] [Mix to WAV...]                            │
 *   ├──────────────┬────────────────────────────────────────────────────────┤
 *   │              │ time ruler (0s · 1s · 2s · ...)                         │
 *   │ controls     ├────────────────────────────────────────────────────────┤
 *   │ for track 1  │ waveform 1                                              │
 *   ├──────────────┼────────────────────────────────────────────────────────┤
 *   │ controls     │ waveform 2                                              │
 *   │ for track 2  │                                                         │
 *   └──────────────┴────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <p>All waveforms and the ruler share one {@link TimeAxis}, so a sample
 * at t = 1.234 s lives at the same x-pixel in every lane. Misalignment is
 * directly visible.</p>
 */
public final class MixerApp extends Application {

    /**
     * Launcher with no module deps for the shade plugin / external runners
     * that can't see the JavaFX module-info.
     */
    public static void main(String[] args) {
        Application.launch(MixerApp.class, args);
    }

    private final ObservableList<MixTrack> tracks = FXCollections.observableArrayList();
    private final TimeAxis axis = new TimeAxis();
    private final List<TrackRow> rows = new ArrayList<>();

    private VBox trackStack;
    private TimeRuler ruler;
    private StackPane waveformOverlay;   // hosts the cursor over the track stack
    private Canvas cursorCanvas;
    private Stage stage;

    private PlaybackController playback;
    private Label statusLabel;

    private static final Color[] TINTS = {
            Color.web("#3e5e85"),   // blue   — typical backing track
            Color.web("#8b3a3a"),   // red    — typical vocal
            Color.web("#345e30"),   // green
            Color.web("#6e4218"),   // orange
            Color.web("#4a2f5e"),   // purple
            Color.web("#3a7a73"),   // teal
    };

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        primaryStage.setTitle("Notation · Mixer");

        BorderPane root = new BorderPane();
        root.setTop(buildToolbar());
        root.setCenter(buildBody());
        root.setBottom(buildStatusBar());

        Scene scene = new Scene(root, 1200, 640);
        primaryStage.setScene(scene);
        primaryStage.show();

        // Initial empty viewport — 1 second wide so the ruler shows something.
        axis.setViewport(0, 1_000_000);
        axis.widthPixelsProperty().bind(
                ruler.widthProperty()); // ruler width tracks the available pane width

        rebuildOverlaySize();
    }

    // ── UI construction ───────────────────────────────────────────────

    private HBox buildToolbar() {
        Button addBtn = new Button("Add WAV…");
        addBtn.setOnAction(e -> onAddWav());

        Button playBtn = new Button("▶ Play");
        playBtn.setOnAction(e -> onPlay());

        Button stopBtn = new Button("■ Stop");
        stopBtn.setOnAction(e -> onStop());

        Button mixBtn = new Button("Mix to WAV…");
        mixBtn.setStyle("-fx-font-weight: bold;");
        mixBtn.setOnAction(e -> onMixExport());

        Label title = new Label("Notation Mixer");
        title.setFont(Font.font("System", FontWeight.BOLD, 14));

        HBox bar = new HBox(10, title, sep(), addBtn, sep(), playBtn, stopBtn, sep(), mixBtn);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10, 14, 10, 14));
        bar.setStyle("-fx-background-color: #f4f1e8; -fx-border-color: #d4cfc0; -fx-border-width: 0 0 1 0;");
        return bar;
    }

    private Region sep() {
        Region r = new Region();
        r.setPrefWidth(12);
        return r;
    }

    private BorderPane buildBody() {
        // The body has two columns: a fixed-width left gutter and the
        // shared waveform area on the right. The ruler sits at the top
        // of the right column; track rows align under it.

        ruler = new TimeRuler(axis);

        trackStack = new VBox();
        trackStack.setFillWidth(true);

        ScrollPane scroll = new ScrollPane(trackStack);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent;");

        cursorCanvas = new Canvas();
        // Mouse-transparent: clicks fall through to the underlying TrackRow's
        // waveform pane, where each row owns its own seek-on-click handler.
        // The cursor canvas only paints the moving playhead line.
        cursorCanvas.setMouseTransparent(true);

        waveformOverlay = new StackPane(scroll, cursorCanvas);
        StackPane.setAlignment(cursorCanvas, Pos.TOP_LEFT);

        // Layout: ruler on top, track stack (with overlay) below.
        VBox rightCol = new VBox(rulerWithLeftSpacer(), waveformOverlay);
        VBox.setVgrow(waveformOverlay, Priority.ALWAYS);

        BorderPane body = new BorderPane();
        body.setCenter(rightCol);
        return body;
    }

    /**
     * The ruler needs to align with the waveform area, which sits to the
     * right of each TrackRow's control panel. We prepend a fixed-width
     * spacer matching the control panel width so the ruler's 0s tick lines
     * up with each row's waveform-area left edge.
     */
    private HBox rulerWithLeftSpacer() {
        Region spacer = new Region();
        spacer.setPrefWidth(280);   // matches TrackRow.CONTROLS_WIDTH
        spacer.setMinWidth(280);
        spacer.setMaxWidth(280);
        spacer.setStyle("-fx-background-color: #f4f1e8; -fx-border-color: #d4cfc0; -fx-border-width: 0 1 1 0;");
        HBox row = new HBox(spacer, ruler);
        HBox.setHgrow(ruler, Priority.ALWAYS);
        row.setMinHeight(28);
        return row;
    }

    private HBox buildStatusBar() {
        statusLabel = new Label("Add WAV files to begin.");
        statusLabel.setStyle("-fx-text-fill: #555;");
        HBox bar = new HBox(statusLabel);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(6, 14, 6, 14));
        bar.setStyle("-fx-background-color: #f4f1e8; -fx-border-color: #d4cfc0; -fx-border-width: 1 0 0 0;");
        return bar;
    }

    private void rebuildOverlaySize() {
        cursorCanvas.widthProperty().bind(waveformOverlay.widthProperty());
        cursorCanvas.heightProperty().bind(waveformOverlay.heightProperty());
    }

    // ── Track add / remove ────────────────────────────────────────────

    private void onAddWav() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Add WAV");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("WAV files", "*.wav"));
        List<File> picked = fc.showOpenMultipleDialog(stage);
        if (picked == null || picked.isEmpty()) return;
        for (File f : picked) loadOne(f);
    }

    private void loadOne(File f) {
        try {
            WaveformData wd = WaveformData.load(f);
            MixTrack track = new MixTrack(f, wd);
            tracks.add(track);
            int idx = tracks.size() - 1;
            Color tint = TINTS[idx % TINTS.length];
            TrackRow row = new TrackRow(track, axis, tint, this::onRemoveTrack, this::onSeekTo);
            rows.add(row);
            trackStack.getChildren().add(row);
            recomputeAxisFromTracks();
            disposePlaybackIfAny();   // next Play will pick up the new track
            updateStatus("Added " + f.getName() + " (" + formatDuration(wd.durationMicros()) + ")");
        } catch (Exception ex) {
            showError("Failed to load " + f.getName(), ex.getMessage());
        }
    }

    private void onRemoveTrack(MixTrack t) {
        int idx = tracks.indexOf(t);
        if (idx < 0) return;
        tracks.remove(idx);
        TrackRow row = rows.remove(idx);
        trackStack.getChildren().remove(row);
        // Recolour remaining rows so tints follow a stable order.
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).waveformView().setWaveformColor(TINTS[i % TINTS.length]);
        }
        recomputeAxisFromTracks();
        disposePlaybackIfAny();   // next Play creates a fresh controller
        updateStatus("Removed " + t.displayName());
    }

    private void disposePlaybackIfAny() {
        if (playback != null) {
            playback.dispose();
            playback = null;
        }
    }

    /**
     * Resize the viewport to cover the longest track. With more than one
     * track loaded, the viewport spans 0 → max duration.
     */
    private void recomputeAxisFromTracks() {
        long maxMicros = 0;
        for (MixTrack t : tracks) {
            if (t.waveform().durationMicros() > maxMicros) {
                maxMicros = t.waveform().durationMicros();
            }
        }
        if (maxMicros <= 0) maxMicros = 1_000_000;
        axis.setViewport(0, maxMicros);
    }

    // ── Mix & export ──────────────────────────────────────────────────

    private void onMixExport() {
        if (tracks.isEmpty()) {
            showError("No tracks", "Add at least one WAV before mixing.");
            return;
        }
        FileChooser fc = new FileChooser();
        fc.setTitle("Save mixed WAV as");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("WAV file", "*.wav"));
        fc.setInitialFileName("mix.wav");
        File out = fc.showSaveDialog(stage);
        if (out == null) return;

        List<AudioMixer.MixSource> sources = new ArrayList<>(tracks.size());
        for (MixTrack t : tracks) {
            sources.add(new AudioMixer.MixSource(t.file(), t.effectiveGain(), 0L));
        }
        updateStatus("Mixing " + tracks.size() + " sources → " + out.getName() + "…");
        new Thread(() -> {
            try {
                AudioMixer.mix(sources, out);
                Platform.runLater(() -> updateStatus("Mix complete · " + out.getAbsolutePath()));
            } catch (Exception ex) {
                Platform.runLater(() -> showError("Mix failed", ex.getMessage()));
            }
        }, "mixer-export").start();
    }

    // ── Playback (Slice C) ────────────────────────────────────────────

    private void onPlay() {
        if (tracks.isEmpty()) {
            showError("No tracks", "Add at least one WAV before playing.");
            return;
        }
        if (playback == null) {
            try {
                playback = new PlaybackController(tracks, axis, cursorCanvas);
            } catch (Exception ex) {
                showError("Playback init failed", ex.getMessage());
                return;
            }
        }
        playback.refreshGainBindings();
        playback.play();
        updateStatus("Playing");
    }

    private void onStop() {
        if (playback != null) {
            playback.stop();
            updateStatus("Stopped");
        }
    }

    /** Seek callback shared by every TrackRow's waveform pane. */
    private void onSeekTo(long micros) {
        if (playback != null) playback.seek(micros);
    }

    // ── Utility ───────────────────────────────────────────────────────

    private void updateStatus(String msg) {
        statusLabel.setText(msg);
    }

    private void showError(String title, String detail) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle("Mixer");
        a.setHeaderText(title);
        a.setContentText(detail);
        a.showAndWait();
    }

    private static String formatDuration(long micros) {
        long totalSeconds = micros / 1_000_000L;
        long m = totalSeconds / 60;
        long s = totalSeconds % 60;
        return String.format("%d:%02d", m, s);
    }

    @Override
    public void stop() {
        disposePlaybackIfAny();
    }
}
