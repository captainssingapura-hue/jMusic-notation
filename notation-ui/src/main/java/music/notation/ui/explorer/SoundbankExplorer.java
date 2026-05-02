package music.notation.ui.explorer;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import music.notation.play.PatchRef;

import javax.sound.midi.Instrument;
import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Patch;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Soundbank;
import javax.sound.midi.Synthesizer;
import javax.sound.midi.Transmitter;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Standalone JavaFX exploration tool for SoundFont (.sf2) and DLS files.
 * Open a soundbank, browse instruments grouped by bank, audition any
 * patch via the auto-pattern button or play freely on the mini keyboard.
 *
 * <p>Run via the {@code explorer} Maven profile:</p>
 * <pre>{@code mvn -pl notation-ui -Pexplorer javafx:run}</pre>
 */
public class SoundbankExplorer extends Application {

    private Synthesizer synth;
    private Soundbank loadedBank;
    private Instrument selectedInstrument;
    private PatchRef selectedPatch;          // shared selection shape with the main player
    private MidiDevice openedInput;
    private Transmitter openedTransmitter;

    private final Label fileMetaLabel = new Label("No soundbank loaded");
    private final Label detailLabel = new Label("Select an instrument");
    private final ListView<String> col1 = new ListView<>();        // family or bank
    private final ListView<String> col2 = new ListView<>();        // voice or patch
    private final ListView<Instrument> col3 = new ListView<>();    // variant (by-family mode only)
    private final TextField search = new TextField();
    private final ToggleGroup patternGroup = new ToggleGroup();

    private final Map<Integer, Rectangle> keyByMidi = new LinkedHashMap<>();

    private enum GroupMode { BY_BANK, BY_FAMILY }
    private GroupMode groupMode = GroupMode.BY_FAMILY;

    /** GM family ranges (program-number based) — same shape as InstrumentPickerDialog. */
    private record Family(String name, int lo, int hi) {}
    private static final List<Family> FAMILIES = List.of(
            new Family("Piano",             0,   7),
            new Family("Chromatic Perc.",   8,  15),
            new Family("Organ",            16,  23),
            new Family("Guitar",           24,  31),
            new Family("Bass",             32,  39),
            new Family("Strings",          40,  47),
            new Family("Ensemble",         48,  55),
            new Family("Brass",            56,  63),
            new Family("Reed",             64,  71),
            new Family("Pipe",             72,  79),
            new Family("Synth Lead",       80,  87),
            new Family("Synth Pad",        88,  95),
            new Family("Synth FX",         96, 103),
            new Family("Ethnic",          104, 111),
            new Family("Percussive",      112, 119),
            new Family("Sound FX",        120, 127),
            new Family("Drum Kit",         -1,  -1));

    @Override
    public void start(Stage stage) throws Exception {
        synth = MidiSystem.getSynthesizer();
        synth.open();

        Button openBtn = button("Open soundbank…");
        openBtn.setOnAction(e -> chooseAndLoad(stage));

        Label inLabel = new Label("MIDI in:");
        inLabel.setStyle("-fx-text-fill: #cdd6f4;");
        ComboBox<MidiDevice.Info> inputCombo = new ComboBox<>();
        inputCombo.setStyle("-fx-background-color: #313244; -fx-text-fill: #cdd6f4;");
        inputCombo.setPromptText("(none)");
        inputCombo.setCellFactory(lv -> midiInfoCell());
        inputCombo.setButtonCell(midiInfoCell());
        Button refreshBtn = button("↻");
        refreshBtn.setOnAction(e -> refreshInputs(inputCombo));
        inputCombo.setOnAction(e -> bindMidiInput(inputCombo.getValue()));
        refreshInputs(inputCombo);

        HBox topBar = new HBox(10, openBtn, fileMetaLabel,
                spacer(), inLabel, inputCombo, refreshBtn);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(8));
        topBar.setStyle("-fx-background-color: #181825;");
        fileMetaLabel.setStyle("-fx-text-fill: #a6adc8; -fx-font-size: 12;");

        // ── Left: mode toggle + search + tree
        ToggleGroup modeGroup = new ToggleGroup();
        RadioButton modeFamily = new RadioButton("By family");
        RadioButton modeBank   = new RadioButton("By bank");
        modeFamily.setToggleGroup(modeGroup);
        modeBank.setToggleGroup(modeGroup);
        modeFamily.setSelected(true);
        for (var rb : new RadioButton[] { modeFamily, modeBank }) {
            rb.setStyle("-fx-text-fill: #cdd6f4; -fx-font-size: 11;");
        }
        modeGroup.selectedToggleProperty().addListener((obs, o, n) -> {
            groupMode = (n == modeBank) ? GroupMode.BY_BANK : GroupMode.BY_FAMILY;
            rebuildColumns();
        });
        HBox modeRow = new HBox(10, modeFamily, modeBank);

        search.setPromptText("Search instruments…");
        search.setStyle("-fx-background-color: #313244; -fx-text-fill: #cdd6f4;");
        search.textProperty().addListener((obs, o, n) -> rebuildColumns());

        styleListView(col1);
        styleListView(col2);
        styleListView(col3);
        col1.setPlaceholder(placeholder("Open a soundbank"));
        col2.setPlaceholder(placeholder("—"));
        col3.setPlaceholder(placeholder("—"));
        col1.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> onCol1Select(n));
        col2.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> onCol2Select(n));
        col3.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> onCol3Select(n));
        col3.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(Instrument inst, boolean empty) {
                super.updateItem(inst, empty);
                setText(empty || inst == null ? null : leafText(inst));
                setStyle("-fx-text-fill: #cdd6f4; -fx-background-color: #1e1e2e;");
            }
        });

        HBox columns = new HBox(2, col1, col2, col3);
        HBox.setHgrow(col1, Priority.ALWAYS);
        HBox.setHgrow(col2, Priority.ALWAYS);
        HBox.setHgrow(col3, Priority.ALWAYS);

        VBox left = new VBox(6, modeRow, search, columns);
        left.setPadding(new Insets(8));
        VBox.setVgrow(columns, Priority.ALWAYS);
        left.setStyle("-fx-background-color: #1e1e2e;");
        left.setPrefWidth(540);

        // ── Right: detail + audition controls
        detailLabel.setStyle("-fx-text-fill: #cdd6f4; -fx-font-size: 13;");
        detailLabel.setWrapText(true);

        RadioButton p1 = new RadioButton("Single C4");
        RadioButton p2 = new RadioButton("C major arpeggio");
        RadioButton p3 = new RadioButton("Sustained C major chord");
        p1.setToggleGroup(patternGroup);
        p2.setToggleGroup(patternGroup);
        p3.setToggleGroup(patternGroup);
        p2.setSelected(true);
        for (var rb : new RadioButton[] { p1, p2, p3 }) {
            rb.setStyle("-fx-text-fill: #cdd6f4;");
        }

        Button auditionBtn = button("▶ Audition");
        auditionBtn.setOnAction(e -> auditionSelected());

        VBox right = new VBox(8, detailLabel, p1, p2, p3, auditionBtn);
        right.setPadding(new Insets(12));
        right.setStyle("-fx-background-color: #181825;");

        // ── Bottom: full 88-key piano, stretches to fill window width
        Pane keyboard = buildKeyboard();
        VBox kbBox = new VBox(keyboard);
        kbBox.setPadding(new Insets(8));
        kbBox.setFillWidth(true);
        kbBox.setStyle("-fx-background-color: #1e1e2e;");
        keyboard.prefWidthProperty().bind(kbBox.widthProperty().subtract(16));
        keyboard.minWidthProperty().bind(kbBox.widthProperty().subtract(16));

        BorderPane root = new BorderPane();
        root.setTop(topBar);
        root.setLeft(left);
        root.setCenter(right);
        root.setBottom(kbBox);
        root.setStyle("-fx-background-color: #1e1e2e;");

        Scene scene = new Scene(root, 1280, 680);
        stage.setTitle("Soundbank Explorer");
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> {
            if (openedTransmitter != null) try { openedTransmitter.close(); } catch (Exception ignored) {}
            if (openedInput != null)       try { openedInput.close();       } catch (Exception ignored) {}
            if (synth != null) synth.close();
            Platform.exit();
        });
        stage.show();
    }

    // ── Soundbank loading ──────────────────────────────────────────────

    private void chooseAndLoad(Stage stage) {
        var chooser = new FileChooser();
        chooser.setTitle("Open soundbank");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Soundbanks", "*.sf2", "*.dls", "*.sbk"));
        File f = chooser.showOpenDialog(stage);
        if (f == null) return;
        loadSoundbank(f);
    }

    private void loadSoundbank(File f) {
        try {
            // Replace any previously-loaded patches.
            if (loadedBank != null) synth.unloadAllInstruments(loadedBank);
            loadedBank = MidiSystem.getSoundbank(f);
            if (!synth.isSoundbankSupported(loadedBank)) {
                fileMetaLabel.setText("Unsupported by Java synth: " + f.getName());
                loadedBank = null;
                return;
            }
            // Load every patch so drum kits land on bank 128 — Gervill's
            // channel-9 routing needs the drum bank pre-populated; per-
            // instrument loading isn't enough.
            synth.loadAllInstruments(loadedBank);
            int count = loadedBank.getInstruments().length;
            long size = f.length() / 1024;
            fileMetaLabel.setText(String.format(
                    "%s  ·  %d patches  ·  %d KB",
                    f.getName(), count, size));
            rebuildColumns();
        } catch (Exception ex) {
            fileMetaLabel.setText("Failed: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    // ── Miller-column drill-down ───────────────────────────────────────
    //
    // by-family mode: col1 = family, col2 = GM voice, col3 = variant.
    // by-bank mode:   col1 = bank,   col2 = patch (final),  col3 hidden.
    // Search overrides hierarchy: any text in the box dumps matching
    // patches into col3 (or col2 in by-bank mode) regardless of left-
    // column selection.
    //
    // State: the currently-displayed contents of cols 2/3 are derived
    // from selections in cols 1/2 plus the active groupMode.

    /** Index keyed by (family, gmVoice) → variants; computed once per soundbank load. */
    private final Map<String, Map<music.notation.event.Instrument, List<Instrument>>>
            byFamilyByGm = new LinkedHashMap<>();
    /** Index keyed by bank → patches. */
    private final Map<Integer, List<Instrument>> byBank = new TreeMap<>();

    private void rebuildColumns() {
        byFamilyByGm.clear();
        byBank.clear();
        col1.getItems().clear();
        col2.getItems().clear();
        col3.getItems().clear();
        if (loadedBank == null) return;

        // Build full indices (no search filter — search only affects display).
        for (Family f : FAMILIES) byFamilyByGm.put(f.name(), new LinkedHashMap<>());
        for (Instrument inst : loadedBank.getInstruments()) {
            byFamilyByGm.get(familyOf(inst))
                    .computeIfAbsent(closestGm(inst), k -> new ArrayList<>()).add(inst);
            byBank.computeIfAbsent(inst.getPatch().getBank(), k -> new ArrayList<>()).add(inst);
        }

        boolean familyMode = (groupMode == GroupMode.BY_FAMILY);
        col3.setVisible(familyMode);
        col3.setManaged(familyMode);

        String q = searchQuery();
        if (!q.isEmpty()) {
            populateSearchResults(q, familyMode);
            return;
        }

        if (familyMode) {
            for (Family f : FAMILIES) {
                var byGm = byFamilyByGm.get(f.name());
                if (byGm.isEmpty()) continue;
                int n = byGm.values().stream().mapToInt(List::size).sum();
                col1.getItems().add(f.name() + "  (" + n + ")");
            }
        } else {
            for (var e : byBank.entrySet()) {
                col1.getItems().add("Bank " + e.getKey() + "  (" + e.getValue().size() + ")");
            }
        }
    }

    private String searchQuery() {
        String s = search.getText();
        return s == null ? "" : s.trim().toLowerCase();
    }

    /** When search is active, dump matching variants flat into the rightmost visible column. */
    private void populateSearchResults(String q, boolean familyMode) {
        var matches = new ArrayList<Instrument>();
        for (Instrument inst : loadedBank.getInstruments()) {
            if (inst.getName().toLowerCase().contains(q)) matches.add(inst);
        }
        matches.sort(Comparator
                .comparingInt((Instrument i) -> i.getPatch().getBank())
                .thenComparingInt(i -> i.getPatch().getProgram()));
        col1.getItems().setAll(List.of("Search results  (" + matches.size() + ")"));
        col2.getItems().setAll(matches.stream().map(SoundbankExplorer::leafText).toList());
        if (familyMode) col3.getItems().clear();
    }

    private void onCol1Select(String value) {
        col2.getItems().clear();
        col3.getItems().clear();
        if (value == null || loadedBank == null) return;
        if (!searchQuery().isEmpty()) return;   // search-result rows already populated
        if (groupMode == GroupMode.BY_FAMILY) {
            String famName = stripCount(value);
            var byGm = byFamilyByGm.get(famName);
            if (byGm == null) return;
            var voices = new ArrayList<>(byGm.entrySet());
            voices.sort(Comparator.comparingInt(e -> e.getKey().program()));
            for (var e : voices) {
                col2.getItems().add(prettyVoice(e.getKey()) + "  (" + e.getValue().size() + ")");
            }
        } else {
            int bank = Integer.parseInt(stripCount(value).substring("Bank ".length()));
            var patches = byBank.getOrDefault(bank, List.of());
            patches.sort(Comparator.comparingInt(i -> i.getPatch().getProgram()));
            for (Instrument inst : patches) col2.getItems().add(leafText(inst));
        }
    }

    private void onCol2Select(String value) {
        col3.getItems().clear();
        if (value == null || loadedBank == null) return;
        if (groupMode == GroupMode.BY_FAMILY && searchQuery().isEmpty()) {
            String famName = stripCount(col1.getSelectionModel().getSelectedItem());
            String voiceName = stripCount(value);
            var byGm = byFamilyByGm.get(famName);
            if (byGm == null) return;
            for (var e : byGm.entrySet()) {
                if (prettyVoice(e.getKey()).equals(voiceName)) {
                    var variants = new ArrayList<>(e.getValue());
                    variants.sort(Comparator
                            .comparingInt((Instrument i) -> i.getPatch().getBank())
                            .thenComparingInt(i -> i.getPatch().getProgram()));
                    col3.getItems().setAll(variants);
                    if (variants.size() == 1) col3.getSelectionModel().selectFirst();
                    break;
                }
            }
        } else {
            // by-bank mode OR search results → col2 holds final leaf strings
            selectInstrumentFromLeafText(value);
        }
    }

    private void onCol3Select(Instrument inst) {
        if (inst == null) {
            return;
        }
        chooseInstrument(inst);
    }

    private void selectInstrumentFromLeafText(String text) {
        // text: "<bank>·<program>  <name>"
        int dot = text.indexOf('·');
        int sep = text.indexOf("  ", dot);
        if (dot < 0 || sep < 0) return;
        int bank = Integer.parseInt(text.substring(0, dot));
        int program = Integer.parseInt(text.substring(dot + 1, sep));
        Instrument inst = findInstrument(bank, program);
        if (inst != null) chooseInstrument(inst);
    }

    private void chooseInstrument(Instrument inst) {
        selectedInstrument = inst;
        int bank = inst.getPatch().getBank();
        int program = inst.getPatch().getProgram();
        String name = inst.getName();
        var gmVoice = closestGm(inst);
        selectedPatch = (bank == 0 && program == gmVoice.program() && !isDrumPatch(inst))
                ? PatchRef.gm(gmVoice)
                : PatchRef.custom(gmVoice, bank, program, name);
        detailLabel.setText(String.format(
                "%s\nBank %d · Program %d\nGM voice: %s%s\nClass: %s",
                name, bank, program,
                prettyVoice(gmVoice),
                isDrumPatch(inst) ? "  (drum kit → channel 9)" : "",
                inst.getClass().getSimpleName()));
        bindChannelToSelected();
    }

    /** Strip "  (N)" count suffix from a column label. */
    private static String stripCount(String s) {
        if (s == null) return "";
        int p = s.lastIndexOf("  (");
        return p < 0 ? s : s.substring(0, p);
    }

    private static void styleListView(ListView<?> lv) {
        lv.setStyle("-fx-control-inner-background: #1e1e2e; -fx-text-fill: #cdd6f4;"
                + " -fx-background-color: #1e1e2e;");
        lv.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
    }

    private static Label placeholder(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: #6c7086; -fx-font-size: 11;");
        return l;
    }

    /** Map a soundbank instrument to the closest matching GM voice in our core set. */
    private static music.notation.event.Instrument closestGm(Instrument inst) {
        if (isDrumPatch(inst)) return music.notation.event.Instrument.DRUM_KIT;
        int program = inst.getPatch().getProgram() & 0x7f;
        var values = music.notation.event.Instrument.values();
        // The enum is declared in GM program order — values()[program] matches
        // for programs 0..127. DRUM_KIT comes after; skip when indexing.
        for (var v : values) {
            if (v != music.notation.event.Instrument.DRUM_KIT && v.program() == program) return v;
        }
        return music.notation.event.Instrument.ACOUSTIC_GRAND_PIANO;
    }

    private static String prettyVoice(music.notation.event.Instrument voice) {
        // Title-case "ACOUSTIC_GRAND_PIANO" → "Acoustic Grand Piano".
        String[] parts = voice.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
        }
        return sb.toString();
    }

    private static String leafText(Instrument inst) {
        // "<bank>·<program>  <name>" — single deterministic leaf format used
        // across both grouping modes; parsed in onTreeSelect().
        return inst.getPatch().getBank() + "·" + inst.getPatch().getProgram()
                + "  " + inst.getName();
    }

    private static String familyOf(Instrument inst) {
        if (isDrumPatch(inst)) return "Drum Kit";
        int p = inst.getPatch().getProgram();
        for (Family f : FAMILIES) {
            if (f.lo() < 0) continue;
            if (p >= f.lo() && p <= f.hi()) return f.name();
        }
        return "Sound FX";
    }

    private Instrument findInstrument(int bank, int program) {
        for (Instrument inst : loadedBank.getInstruments()) {
            Patch p = inst.getPatch();
            if (p.getBank() == bank && p.getProgram() == program) return inst;
        }
        return null;
    }

    // ── Audition ───────────────────────────────────────────────────────

    private void bindChannelToSelected() {
        if (selectedPatch == null) return;
        boolean drum = selectedPatch.instrument() == music.notation.event.Instrument.DRUM_KIT;
        int chIdx = drum ? 9 : 0;
        MidiChannel ch = synth.getChannels()[chIdx];
        if (drum) {
            ch.programChange(selectedPatch.effectiveProgram());
        } else {
            int bank = selectedPatch.effectiveBank();
            int msb = bank > 127 ? (bank >> 7) & 0x7f : bank & 0x7f;
            int lsb = bank > 127 ? bank & 0x7f : 0;
            ch.controlChange(0, msb);
            ch.controlChange(32, lsb);
            ch.programChange(selectedPatch.effectiveProgram());
        }
    }

    private void auditionSelected() {
        if (selectedPatch == null) return;
        bindChannelToSelected();
        boolean drum = selectedPatch.instrument() == music.notation.event.Instrument.DRUM_KIT;
        int chIdx = drum ? 9 : 0;
        MidiChannel ch = synth.getChannels()[chIdx];

        int[] pattern = patternForRadio(drum);
        boolean sustained = ((RadioButton) patternGroup.getSelectedToggle())
                .getText().contains("chord");

        Thread t = new Thread(() -> {
            try {
                if (sustained) {
                    for (int n : pattern) ch.noteOn(n, 100);
                    Thread.sleep(1200);
                    for (int n : pattern) ch.noteOff(n);
                } else {
                    for (int n : pattern) {
                        ch.noteOn(n, 100);
                        Thread.sleep(280);
                        ch.noteOff(n);
                    }
                }
            } catch (InterruptedException ignored) { }
        }, "audition");
        t.setDaemon(true);
        t.start();
    }

    private int[] patternForRadio(boolean drum) {
        if (drum) return new int[] { 36, 38, 42, 46 };  // kick / snare / hat-closed / hat-open
        String label = ((RadioButton) patternGroup.getSelectedToggle()).getText();
        if (label.startsWith("Single")) return new int[] { 60 };
        if (label.startsWith("Sustained")) return new int[] { 60, 64, 67 };
        return new int[] { 60, 64, 67, 72 };  // arpeggio
    }

    private static boolean isDrumPatch(Instrument inst) {
        if (inst.getPatch().getBank() >= 128) return true;
        // Fallback for non-standard SF2s that don't use bank 128 for drums.
        String n = inst.getName().toLowerCase();
        return n.contains("kit") || n.contains("drum") || n.contains("perc");
    }

    // ── Full 88-key keyboard ──────────────────────────────────────────

    private static final int FIRST_MIDI = 21;   // A0
    private static final int LAST_MIDI  = 108;  // C8
    private static final double KEYBOARD_HEIGHT = 110;

    private static final boolean[] BLACK_KEY_PATTERN = { false, true, false, true, false,
            false, true, false, true, false, true, false }; // C C# D D# E F F# G G# A A# B

    /** White-keyed rectangles in MIDI order (used for layoutKeys). */
    private final List<int[]> whiteKeys = new ArrayList<>();
    private final List<int[]> blackKeys = new ArrayList<>();

    private Pane buildKeyboard() {
        Pane pane = new Pane();
        pane.setPrefHeight(KEYBOARD_HEIGHT + 8);
        pane.setMinHeight(KEYBOARD_HEIGHT + 8);

        // Pass 1: white keys.
        int whiteIdx = 0;
        for (int midi = FIRST_MIDI; midi <= LAST_MIDI; midi++) {
            if (BLACK_KEY_PATTERN[midi % 12]) continue;
            Rectangle r = new Rectangle();
            r.setFill(Color.web("#f5f5f5"));
            r.setStroke(Color.web("#1e1e2e"));
            wireKey(r, midi, true);
            keyByMidi.put(midi, r);
            pane.getChildren().add(r);
            whiteKeys.add(new int[] { midi, whiteIdx++ });
        }
        // Pass 2: black keys (overlaid).
        int wIdx = 0;
        for (int midi = FIRST_MIDI; midi <= LAST_MIDI; midi++) {
            int pc = midi % 12;
            if (!BLACK_KEY_PATTERN[pc]) { wIdx++; continue; }
            Rectangle r = new Rectangle();
            r.setFill(Color.web("#1e1e2e"));
            r.setStroke(Color.web("#1e1e2e"));
            wireKey(r, midi, false);
            keyByMidi.put(midi, r);
            pane.getChildren().add(r);
            blackKeys.add(new int[] { midi, wIdx - 1 });  // sits between (wIdx-1) and wIdx
        }

        // Re-layout on width change so the keyboard always spans the full pane.
        Runnable relayout = () -> layoutKeys(pane.getWidth(), pane.getHeight());
        pane.widthProperty().addListener((obs, o, n) -> relayout.run());
        pane.heightProperty().addListener((obs, o, n) -> relayout.run());
        return pane;
    }

    private void layoutKeys(double paneWidth, double paneHeight) {
        if (paneWidth <= 0 || paneHeight <= 0) return;
        int whiteCount = whiteKeys.size();
        if (whiteCount == 0) return;
        double ww = paneWidth / whiteCount;
        double wh = Math.max(40, Math.min(paneHeight - 4, KEYBOARD_HEIGHT));
        double bw = ww * 0.62;
        double bh = wh * 0.62;
        for (int[] e : whiteKeys) {
            Rectangle r = (Rectangle) keyByMidi.get(e[0]);
            r.setX(e[1] * ww);
            r.setY(0);
            r.setWidth(Math.max(1, ww - 1));
            r.setHeight(wh);
        }
        for (int[] e : blackKeys) {
            Rectangle r = (Rectangle) keyByMidi.get(e[0]);
            // Centre the black key on the boundary between whites e[1] and e[1]+1.
            double centre = (e[1] + 1) * ww;
            r.setX(centre - bw / 2.0);
            r.setY(0);
            r.setWidth(bw);
            r.setHeight(bh);
        }
    }

    private void wireKey(Rectangle key, int midi, boolean isWhite) {
        Color rest = isWhite ? Color.web("#f5f5f5") : Color.web("#1e1e2e");
        Color pressed = isWhite ? Color.web("#a6e3a1") : Color.web("#89b4fa");
        key.setOnMousePressed(e -> {
            key.setFill(pressed);
            playNote(midi, true);
        });
        key.setOnMouseReleased(e -> {
            key.setFill(rest);
            playNote(midi, false);
        });
        // Drag-off should still release.
        key.setOnMouseExited(e -> {
            if (e.isPrimaryButtonDown()) {
                key.setFill(rest);
                playNote(midi, false);
            }
        });
    }

    private void playNote(int midi, boolean on) {
        if (synth == null) return;
        int chIdx = (selectedPatch != null
                && selectedPatch.instrument() == music.notation.event.Instrument.DRUM_KIT)
                ? 9 : 0;
        MidiChannel ch = synth.getChannels()[chIdx];
        if (on) ch.noteOn(midi, 100);
        else ch.noteOff(midi);
    }

    // ── MIDI input device ──────────────────────────────────────────────

    private void refreshInputs(ComboBox<MidiDevice.Info> combo) {
        var items = FXCollections.<MidiDevice.Info>observableArrayList();
        for (var info : MidiSystem.getMidiDeviceInfo()) {
            try {
                MidiDevice dev = MidiSystem.getMidiDevice(info);
                // Devices that can transmit (have a Transmitter) are inputs.
                if (dev.getMaxTransmitters() != 0) items.add(info);
            } catch (Exception ignored) { }
        }
        combo.setItems(items);
    }

    private void bindMidiInput(MidiDevice.Info info) {
        // Close previous binding cleanly.
        if (openedTransmitter != null) try { openedTransmitter.close(); } catch (Exception ignored) {}
        if (openedInput != null) try { openedInput.close(); } catch (Exception ignored) {}
        openedTransmitter = null;
        openedInput = null;
        if (info == null) return;
        try {
            MidiDevice dev = MidiSystem.getMidiDevice(info);
            if (!dev.isOpen()) dev.open();
            Transmitter tx = dev.getTransmitter();
            tx.setReceiver(new ControllerReceiver());
            openedInput = dev;
            openedTransmitter = tx;
        } catch (Exception ex) {
            ex.printStackTrace();
            fileMetaLabel.setText("MIDI in failed: " + ex.getMessage());
        }
    }

    /** Inbound-MIDI handler: forwards NOTE_ON/OFF to the synth and highlights keys. */
    private final class ControllerReceiver implements Receiver {
        @Override public void send(MidiMessage msg, long timeStamp) {
            if (!(msg instanceof ShortMessage sm)) return;
            int cmd = sm.getCommand();
            int data1 = sm.getData1();
            int data2 = sm.getData2();
            switch (cmd) {
                case ShortMessage.NOTE_ON -> {
                    if (data2 > 0) handleNote(data1, data2, true);
                    else handleNote(data1, 0, false);
                }
                case ShortMessage.NOTE_OFF -> handleNote(data1, 0, false);
                default -> { /* ignore CC, pitch bend, aftertouch, etc. for v1 */ }
            }
        }
        @Override public void close() {}
    }

    private void handleNote(int midi, int velocity, boolean on) {
        if (synth == null) return;
        int chIdx = (selectedPatch != null
                && selectedPatch.instrument() == music.notation.event.Instrument.DRUM_KIT)
                ? 9 : 0;
        MidiChannel ch = synth.getChannels()[chIdx];
        if (on) ch.noteOn(midi, Math.max(1, velocity));
        else ch.noteOff(midi);
        // Highlight the on-screen key when in range.
        Platform.runLater(() -> {
            var key = keyByMidi.get(midi);
            if (key == null) return;
            boolean isWhite = !BLACK_KEY_PATTERN[midi % 12];
            Color rest = isWhite ? Color.web("#f5f5f5") : Color.web("#1e1e2e");
            Color pressed = isWhite ? Color.web("#a6e3a1") : Color.web("#89b4fa");
            key.setFill(on ? pressed : rest);
        });
    }

    private static Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    private static ListCell<MidiDevice.Info> midiInfoCell() {
        return new ListCell<>() {
            @Override protected void updateItem(MidiDevice.Info item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
                setStyle("-fx-text-fill: #cdd6f4; -fx-background-color: #313244;");
            }
        };
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private static Button button(String text) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color: #45475a; -fx-text-fill: #cdd6f4; "
                + "-fx-padding: 6 14; -fx-background-radius: 4;");
        return b;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
