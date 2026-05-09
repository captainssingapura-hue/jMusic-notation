package music.notation.mxl;

import music.notation.expressivity.Articulations;
import music.notation.performance.Instrumentation;
import music.notation.expressivity.Pedaling;
import music.notation.performance.Performance;
import music.notation.performance.PerformanceJson;
import music.notation.performance.Score;
import music.notation.performance.TempoTrack;
import music.notation.performance.Track;
import music.notation.expressivity.Velocities;
import music.notation.expressivity.Volume;
import music.notation.structure.KeySignature;
import music.notation.structure.TimeSignature;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Inverse of {@link MxlSplitJsonWriter}: reassembles an {@link MxlImport} from
 * a per-piece folder of split JSON files. Tracks are loaded in filename order
 * — the writer's zero-padded {@code track-NN-} prefix preserves Score order
 * for typical pieces (≤ 99 tracks).
 *
 * <p>{@code sourceXml} is not preserved in the split JSON layout (the
 * decompressed XML lives separately under {@code xml/MXL_<base>.xml}), so the
 * reconstructed import carries an empty {@code sourceXml} string.</p>
 */
public final class MxlSplitJsonReader {

    private MxlSplitJsonReader() {}

    public static MxlImport read(Path pieceDir) {
        if (!Files.isDirectory(pieceDir)) {
            throw new IllegalArgumentException("not a piece dir: " + pieceDir);
        }

        MxlSplitJsonWriter.Meta meta = readJson(
                pieceDir.resolve("meta.json"), MxlSplitJsonWriter.Meta.class);
        TempoTrack tempo = readJson(
                pieceDir.resolve("tempo.json"), TempoTrack.class);

        List<Track> tracks = listTrackFiles(pieceDir).stream()
                .map(p -> readJson(p, Track.class))
                .toList();

        Instrumentation instrumentation = Files.exists(pieceDir.resolve("instruments.json"))
                ? readJson(pieceDir.resolve("instruments.json"), Instrumentation.class)
                : Instrumentation.empty();
        Volume volume = Files.exists(pieceDir.resolve("volume.json"))
                ? readJson(pieceDir.resolve("volume.json"), Volume.class)
                : Volume.empty();
        Articulations articulations = Files.exists(pieceDir.resolve("articulations.json"))
                ? readJson(pieceDir.resolve("articulations.json"), Articulations.class)
                : Articulations.empty();
        Pedaling pedaling = Files.exists(pieceDir.resolve("pedaling.json"))
                ? readJson(pieceDir.resolve("pedaling.json"), Pedaling.class)
                : Pedaling.empty();
        Velocities velocities = Files.exists(pieceDir.resolve("velocity.json"))
                ? readJson(pieceDir.resolve("velocity.json"), Velocities.class)
                : Velocities.empty();
        RepeatStructure repeats = Files.exists(pieceDir.resolve("repeats.json"))
                ? readJson(pieceDir.resolve("repeats.json"), RepeatStructure.class)
                : RepeatStructure.empty();
        Transpositions transpositions = Files.exists(pieceDir.resolve("transpositions.json"))
                ? readJson(pieceDir.resolve("transpositions.json"), Transpositions.class)
                : Transpositions.empty();

        Performance perf = new Performance(
                new Score(tracks),
                tempo,
                instrumentation,
                volume,
                articulations,
                pedaling,
                velocities);

        TimeSignature ts = new TimeSignature(meta.timeSig().beats(),
                                             meta.timeSig().beatValue());
        KeySignature key = new KeySignature(meta.key().tonic(),
                                            meta.key().accidental(),
                                            meta.key().mode());
        return new MxlImport(meta.displayName(), perf, ts, key, "", repeats, transpositions);
    }

    private static <T> T readJson(Path file, Class<T> type) {
        if (!Files.exists(file)) {
            throw new IllegalArgumentException("missing required file: " + file);
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            return PerformanceJson.fromJsonAny(json, type);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read " + file, e);
        }
    }

    private static List<Path> listTrackFiles(Path pieceDir) {
        try (Stream<Path> stream = Files.list(pieceDir)) {
            return stream
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith("track-") && name.endsWith(".json");
                    })
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("failed to list track files in " + pieceDir, e);
        }
    }
}
