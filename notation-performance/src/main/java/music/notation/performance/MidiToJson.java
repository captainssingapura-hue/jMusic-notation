package music.notation.performance;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CLI: read a MIDI file, decode to {@link Performance}, emit JSON.
 *
 * <p>Usage: {@code MidiToJson <midi-path> [--out path.json]}.
 * With no {@code --out}, JSON is printed to stdout. A summary line is
 * always written to stderr.</p>
 */
public final class MidiToJson {

    private MidiToJson() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: MidiToJson <midi-path> [--out path.json]");
            System.exit(2);
            return;
        }

        Path inPath = Path.of(args[0]);
        Path outPath = null;
        for (int i = 1; i < args.length; i++) {
            if ("--out".equals(args[i]) && i + 1 < args.length) {
                outPath = Path.of(args[++i]);
            } else {
                System.err.println("Unknown argument: " + args[i]);
                System.exit(2);
                return;
            }
        }

        byte[] midiBytes = Files.readAllBytes(inPath);
        Performance perf = MidiCodec.fromMidi(midiBytes);
        String json = PerformanceJson.toJson(perf);

        int trackCount = perf.score().tracks().size();
        int totalNotes = 0;
        for (Track t : perf.score().tracks()) totalNotes += t.notes().size();

        System.err.println("MIDI: " + inPath + ", " + midiBytes.length
                + " bytes -> JSON: " + json.length() + " chars, "
                + trackCount + " tracks, " + totalNotes + " notes");

        if (outPath != null) {
            Files.write(outPath, json.getBytes(StandardCharsets.UTF_8));
        } else {
            System.out.println(json);
        }
    }
}
