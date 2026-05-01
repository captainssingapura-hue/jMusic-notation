package music.notation.experiments.furelise;

import music.notation.performance.MidiCodec;
import music.notation.performance.Performance;
import music.notation.performance.PerformanceJson;
import music.notation.play.MidiPlayer;
import music.notation.songs.classical.furelise.ManualFurElise;

import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * One-shot generator that imports {@link ManualFurElise} via
 * {@link MidiPlayer#buildSequence}, decodes the MIDI bytes through
 * {@link MidiCodec}, and writes the resulting {@link Performance} as JSON
 * to {@code notation-experiments/src/main/resources/songs/fur_elise.json}.
 *
 * <p>Lives in the test source tree because it depends on
 * {@code notation-songs} and {@code notation-play}, both test-scoped.
 * The output JSON is checked into resources and loaded by
 * {@code PlayFurEliseSwing} at runtime — so {@code main} doesn't need
 * those test deps.</p>
 *
 * <p>Run via:</p>
 * <pre>
 *   mvn -pl notation-experiments exec:java \
 *       -Dexec.classpathScope=test \
 *       -Dexec.mainClass=music.notation.experiments.furelise.ExportFurEliseJson
 * </pre>
 */
public final class ExportFurEliseJson {

    private static final Path DEFAULT_OUT =
            Path.of("notation-experiments/src/main/resources/songs/fur_elise.json");

    private ExportFurEliseJson() {}

    public static void main(String[] args) throws Exception {
        Path out = args.length > 0 ? Path.of(args[0]) : DEFAULT_OUT;

        var piece = new ManualFurElise().create();
        Sequence seq = MidiPlayer.buildSequence(piece);

        // Serialise the Sequence as standard MIDI bytes.
        var baos = new ByteArrayOutputStream();
        int[] types = MidiSystem.getMidiFileTypes(seq);
        int fileType = (types.length > 1) ? 1 : types[0];
        MidiSystem.write(seq, fileType, baos);
        byte[] midi = baos.toByteArray();

        // MIDI -> Performance -> JSON.
        Performance perf = MidiCodec.fromMidi(midi);
        String json = PerformanceJson.toJson(perf);

        Files.createDirectories(out.getParent());
        Files.writeString(out, json);

        int totalNotes = perf.score().tracks().stream()
                .mapToInt(t -> t.notes().size()).sum();
        System.out.printf(
                "wrote %s (%d chars) — %d tracks, %d notes from %d MIDI bytes%n",
                out, json.length(), perf.score().tracks().size(),
                totalNotes, midi.length);
    }
}
