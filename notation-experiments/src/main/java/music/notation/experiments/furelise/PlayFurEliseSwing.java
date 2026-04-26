package music.notation.experiments.furelise;

import music.notation.experiments.pi.CanonRollDisplay;
import music.notation.performance.MidiCodec;
import music.notation.performance.Performance;
import music.notation.performance.PerformanceJson;
import music.notation.performance.Swing;
import music.notation.performance.Track;

import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Loads the JSON-serialised <em>Für Elise</em> from the classpath
 * resource, applies {@link Swing}, and plays the result through the
 * system MIDI synthesiser.
 *
 * <p>This demonstrates the design stance documented in
 * {@code .docs/microtiming.md}: the JSON resource holds only the
 * structural skeleton of Beethoven's piece (notes + onsets + durations
 * + tempo + instruments — no velocities, no articulation). On top of
 * that flat substrate we apply <em>our</em> creative interpretation
 * via the {@link Swing} transformer, then render the result as MIDI.</p>
 *
 * <p>The resource was generated once by
 * {@code ExportFurEliseJson} (test-scope main) from
 * {@code notation-songs}' {@code ManualFurElise}.</p>
 *
 * <p>Usage:</p>
 * <pre>
 *   mvn -pl notation-experiments exec:java \
 *       -Dexec.mainClass=music.notation.experiments.furelise.PlayFurEliseSwing
 * </pre>
 *
 * <p>Flags:</p>
 * <ul>
 *   <li>{@code --swing[=ratio]} — override (default: triplet 0.67).</li>
 *   <li>{@code --no-swing} — play straight, no transformation.</li>
 *   <li>{@code --silent} / {@code -s} — print TUI only, skip audio.</li>
 * </ul>
 */
public final class PlayFurEliseSwing {

    private static final String RESOURCE = "/songs/fur_elise.json";

    private PlayFurEliseSwing() {}

    public static void main(String[] args) throws Exception {
        boolean silent = isSilent(args);
        Double swingOverride = parseSwing(args);
        // Default for FurElise: triplet swing — this is the demo, after all.
        double swingRatio = swingOverride != null ? swingOverride : Swing.TRIPLET;

        Performance perf = loadPerformance();
        if (swingRatio != Swing.NONE) {
            perf = Swing.apply(perf, swingRatio);
        }

        long durationMs = totalMs(perf);
        int totalNotes = perf.score().tracks().stream()
                .mapToInt(t -> t.notes().size()).sum();
        String swingLabel = swingRatio == Swing.NONE
                ? "straight"
                : String.format("swing %.2f", swingRatio);

        System.out.printf("Fur Elise (imported skeleton + %s) - %d tracks, %d notes, %.1fs%n",
                swingLabel, perf.score().tracks().size(), totalNotes, durationMs / 1000.0);

        var roll = new CanonRollDisplay("Fur Elise (" + swingLabel + ")", perf);
        byte[] midi = MidiCodec.toMidi(perf);

        if (silent) {
            // FurElise is long — print only the first ~12 seconds of the TUI for visual sample.
            roll.printHeader();
            long sample = Math.min(durationMs, 12_000);
            for (long t = 0; t <= sample; t += CanonRollDisplay.ROW_MILLIS) {
                roll.printRow(perf, t);
            }
            System.out.println("  | ... (TUI truncated; piece is " + (durationMs / 1000) + "s long)");
            roll.printFooter();
            System.out.println("  (silent: " + midi.length + " bytes of MIDI generated, "
                    + "playback skipped)");
            return;
        }

        playWithSyncedDisplay(perf, midi, roll, durationMs);
    }

    // ── helpers ─────────────────────────────────────────────────────

    private static Performance loadPerformance() throws IOException {
        try (InputStream in = PlayFurEliseSwing.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IOException("classpath resource not found: " + RESOURCE);
            }
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return PerformanceJson.fromJson(json);
        }
    }

    private static long totalMs(Performance perf) {
        long end = 0;
        for (Track t : perf.score().tracks()) {
            for (var n : t.notes()) {
                if (n.offTickMs() > end) end = n.offTickMs();
            }
        }
        return end;
    }

    private static Double parseSwing(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if ("--no-swing".equals(a)) return Swing.NONE;
            if ("--swing".equals(a)) {
                if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                    return Double.parseDouble(args[i + 1]);
                }
                return Swing.TRIPLET;
            }
            if (a.startsWith("--swing=")) {
                return Double.parseDouble(a.substring("--swing=".length()));
            }
        }
        return null;
    }

    private static boolean isSilent(String[] args) {
        for (String a : args) {
            if ("--silent".equals(a) || "-s".equals(a)) return true;
        }
        return false;
    }

    private static void playWithSyncedDisplay(
            Performance perf, byte[] midi, CanonRollDisplay roll, long durationMs)
            throws Exception {
        Sequencer sequencer = MidiSystem.getSequencer();
        sequencer.open();
        try {
            Sequence seq = MidiSystem.getSequence(new ByteArrayInputStream(midi));
            sequencer.setSequence(seq);

            roll.printHeader();
            sequencer.start();

            for (long t = 0; t <= durationMs; t += CanonRollDisplay.ROW_MILLIS) {
                roll.printRow(perf, t);
                Thread.sleep(CanonRollDisplay.ROW_MILLIS);
            }
            Thread.sleep(600);
            roll.printFooter();
        } finally {
            sequencer.stop();
            sequencer.close();
        }
    }
}
