package music.notation.launcher;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tile-based launcher for the notation app suite. Each tile spawns the
 * target app in a fresh JVM via plain {@code java -cp …} — no Maven
 * needed at runtime, no reactor lookup, no working-directory assumptions.
 *
 * <h2>How sub-launching works</h2>
 *
 * <p>The launcher depends on every app module at compile time, so
 * <em>all</em> app classes are on its own classpath when it runs. To
 * spawn an app:</p>
 *
 * <ol>
 *   <li>Resolve the path to the current JVM ({@code java.home/bin/java}).</li>
 *   <li>Read the launcher's own classpath ({@code System.getProperty("java.class.path")}).</li>
 *   <li>Read the launcher's own JVM args via
 *       {@link ManagementFactory#getRuntimeMXBean()}{@code .getInputArguments()}.
 *       These carry whatever JavaFX module-path / add-exports /
 *       system properties the launcher was started with — sub-apps need
 *       the same setup.</li>
 *   <li>Combine into a {@link ProcessBuilder} invocation: the launcher's
 *       JVM args + its classpath + the sub-app's main class.</li>
 *   <li>Inherit stdio so any startup error shows up in the launcher's
 *       terminal.</li>
 * </ol>
 *
 * <p>The sub-process is fully independent — separate JVM, separate
 * heap, separate window. Closing the launcher does not close any
 * spawned app.</p>
 *
 * <h2>Crash isolation</h2>
 *
 * <p>An exception in one spawned app is contained in its own JVM. The
 * launcher just sees the process exit (typically with a non-zero exit
 * code) and updates the Activity list accordingly.</p>
 */
public final class LauncherApp extends Application {

    public static void main(String[] args) {
        Application.launch(LauncherApp.class, args);
    }

    /** Catalog of apps shown as tiles. Add a new one by appending here. */
    private static final List<AppEntry> APPS = List.of(
            new AppEntry("Notation", "🎼",
                    "Compose, arrange, play. Main piano roll + transport + export.",
                    "music.notation.ui.NotationApp"),
            new AppEntry("Mixer", "🎚",
                    "Combine WAV stems and verify timing alignment before export.",
                    "music.notation.mixer.MixerApp"),
            new AppEntry("Recorder", "🎤",
                    "Capture mic / MIDI keyboard / audio file → JSON piece folder.",
                    "music.notation.recorder.RecorderApp"),
            new AppEntry("Lyrics", "📝",
                    "Attach lyrics to a monophonic melody, one glyph per note.",
                    "music.notation.lyrics.LyricsEditorApp"),
            new AppEntry("Soundbanks", "🎻",
                    "Browse GM instruments across loaded SF2 banks; audition via MIDI keyboard.",
                    "music.notation.ui.explorer.SoundbankExplorer")
    );

    /** Definition of one tile / launchable app. */
    record AppEntry(String title, String icon, String description, String mainClass) {}

    /** Running subprocess tracked in the activity log. */
    record RunningProcess(AppEntry app, Process process, String startedAt) {}

    private final ObservableList<RunningProcess> running = FXCollections.observableArrayList();
    private Label statusLabel;

    @Override
    public void start(Stage stage) {
        stage.setTitle("Notation · Launcher");

        BorderPane root = new BorderPane();
        root.setTop(buildHeader());
        root.setCenter(buildTileGrid());
        root.setBottom(buildActivityPanel());

        Scene scene = new Scene(root, 760, 560);
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> {
            // Don't kill spawned apps when the launcher closes — they
            // run in their own JVMs and should survive independently.
        });
        stage.show();

        statusLabel.setText("Ready · classpath has " + APPS.size() + " app(s).");
    }

    // ── UI ────────────────────────────────────────────────────────────

    private VBox buildHeader() {
        Label title = new Label("Notation Workbench");
        title.setFont(Font.font("System", FontWeight.BOLD, 18));

        Label subtitle = new Label("Click a tile to open that tool in its own window.");
        subtitle.setStyle("-fx-text-fill: #555; -fx-font-style: italic;");

        VBox header = new VBox(2, title, subtitle);
        header.setPadding(new Insets(16, 18, 12, 18));
        header.setStyle("-fx-background-color: #f4f1e8; -fx-border-color: #d4cfc0; -fx-border-width: 0 0 1 0;");
        return header;
    }

    private ScrollPane buildTileGrid() {
        TilePane grid = new TilePane();
        grid.setPadding(new Insets(18));
        grid.setHgap(14);
        grid.setVgap(14);
        grid.setPrefColumns(3);
        for (AppEntry app : APPS) {
            grid.getChildren().add(buildTile(app));
        }
        ScrollPane sp = new ScrollPane(grid);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color: #fffef9;");
        return sp;
    }

    private VBox buildTile(AppEntry app) {
        Label icon = new Label(app.icon());
        icon.setFont(Font.font(28));

        Label title = new Label(app.title());
        title.setFont(Font.font("System", FontWeight.BOLD, 14));

        Label desc = new Label(app.description());
        desc.setStyle("-fx-text-fill: #555; -fx-font-size: 11;");
        desc.setWrapText(true);
        desc.setMaxWidth(200);

        Label classHint = new Label(shortClassName(app.mainClass()));
        classHint.setStyle("-fx-text-fill: #999; -fx-font-family: monospace; -fx-font-size: 10;");

        VBox tile = new VBox(6, icon, title, desc, classHint);
        tile.setPadding(new Insets(16));
        tile.setPrefSize(220, 160);
        tile.setStyle(
                "-fx-background-color: #fffef9;"
                + "-fx-border-color: #d4cfc0;"
                + "-fx-border-width: 1;"
                + "-fx-border-radius: 4;"
                + "-fx-background-radius: 4;");

        tile.setOnMouseEntered(e -> tile.setStyle(
                "-fx-background-color: #f4f1e8;"
                + "-fx-border-color: #8b3a3a;"
                + "-fx-border-width: 1;"
                + "-fx-border-radius: 4;"
                + "-fx-background-radius: 4;"
                + "-fx-cursor: hand;"));
        tile.setOnMouseExited(e -> tile.setStyle(
                "-fx-background-color: #fffef9;"
                + "-fx-border-color: #d4cfc0;"
                + "-fx-border-width: 1;"
                + "-fx-border-radius: 4;"
                + "-fx-background-radius: 4;"));
        tile.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) launch(app);
        });
        return tile;
    }

    private VBox buildActivityPanel() {
        Label header = new Label("Activity");
        header.setFont(Font.font("System", FontWeight.BOLD, 11));
        header.setStyle("-fx-text-fill: #8b3a3a; -fx-padding: 0 0 4 0;");

        ListView<RunningProcess> listView = new ListView<>(running);
        listView.setPrefHeight(120);
        listView.setPlaceholder(new Label("No apps launched yet."));
        listView.setCellFactory(lv -> new ProcessCell());

        statusLabel = new Label();
        statusLabel.setStyle("-fx-text-fill: #555; -fx-font-size: 10;");

        VBox box = new VBox(4, header, listView, statusLabel);
        box.setPadding(new Insets(10, 14, 10, 14));
        box.setStyle("-fx-background-color: #f4f1e8; -fx-border-color: #d4cfc0; -fx-border-width: 1 0 0 0;");
        return box;
    }

    // ── Launch ────────────────────────────────────────────────────────

    /**
     * Spawn the target app in a fresh JVM using the launcher's own
     * classpath + JVM args. The subprocess inherits stdio so any
     * startup error is visible in the launcher's terminal.
     */
    private void launch(AppEntry app) {
        List<String> cmd = new ArrayList<>();
        cmd.add(currentJavaExecutable());
        // Inherit JavaFX module path, --add-modules, --add-exports, system
        // properties — whatever the launcher itself was started with.
        cmd.addAll(inheritableJvmArgs());
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        // Always go through SubAppRunner — a non-Application wrapper so
        // JavaFX's "main class must extend Application from module path"
        // check doesn't fire when the launcher is itself running from a
        // shaded uber-jar (where there's no module path at all). In
        // mvn-javafx:run mode the indirection is harmless — one extra
        // reflective stack frame.
        cmd.add(SubAppRunner.class.getName());
        cmd.add(app.mainClass());

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.inheritIO();

        try {
            Process p = pb.start();
            RunningProcess rp = new RunningProcess(app, p, LocalTime.now().format(TIME_FMT));
            running.add(0, rp);
            statusLabel.setText("Launched " + app.title() + " (pid " + p.pid() + ").");

            // Track exit so the activity list can update.
            p.onExit().thenAccept(done -> Platform.runLater(() -> {
                running.remove(rp);
                statusLabel.setText(app.title() + " exited (code " + done.exitValue() + ").");
            }));
        } catch (IOException ex) {
            statusLabel.setText("Failed to launch " + app.title() + ": " + ex.getMessage());
        }
    }

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    // ── JVM resolution ────────────────────────────────────────────────

    /**
     * Path to the {@code java} (or {@code java.exe}) binary of the
     * currently-running JVM. Sub-apps inherit the same JDK so versions
     * stay consistent.
     */
    private static String currentJavaExecutable() {
        String javaHome = System.getProperty("java.home");
        String sep = java.io.File.separator;
        String exe = isWindows() ? "java.exe" : "java";
        return javaHome + sep + "bin" + sep + exe;
    }

    /**
     * The launcher's own JVM args (everything between {@code java} and
     * the main class — module path, add-modules, add-exports,
     * {@code -D} system properties, {@code -X} flags). Excludes
     * agent-style args that don't make sense to propagate.
     */
    private static List<String> inheritableJvmArgs() {
        List<String> result = new ArrayList<>();
        for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            // Skip agent attach paths, jdwp debugger flags, etc. — the
            // sub-app shouldn't inherit those.
            if (arg.startsWith("-agentlib:")) continue;
            if (arg.startsWith("-agentpath:")) continue;
            if (arg.startsWith("-javaagent:")) continue;
            if (arg.startsWith("-Xrunjdwp:")) continue;
            result.add(arg);
        }
        return result;
    }

    private static String shortClassName(String fqcn) {
        int dot = fqcn.lastIndexOf('.');
        return dot < 0 ? fqcn : fqcn.substring(dot + 1);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    // ── Activity row cell ─────────────────────────────────────────────

    private static final class ProcessCell extends ListCell<RunningProcess> {
        @Override
        protected void updateItem(RunningProcess item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            Label icon = new Label(item.app().icon());
            Label name = new Label(item.app().title());
            name.setStyle("-fx-font-weight: bold;");
            Label pid = new Label("pid " + item.process().pid());
            pid.setStyle("-fx-text-fill: #777; -fx-font-family: monospace; -fx-font-size: 10;");
            Label started = new Label("started " + item.startedAt());
            started.setStyle("-fx-text-fill: #999; -fx-font-size: 10;");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            HBox row = new HBox(10, icon, name, pid, spacer, started);
            row.setAlignment(Pos.CENTER_LEFT);
            setText(null);
            setGraphic(row);
        }
    }
}
