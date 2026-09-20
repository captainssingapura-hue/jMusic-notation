package music.notation.guitar;

import music.notation.performance.PitchedNote;
import music.notation.performance.Track;
import music.notation.songs.PieceLibrary;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Command line: a request JSON in, text files out.
 *
 * <pre>
 *   guitar-arrange request.json [--out DIR]     arrange; writes DIR/&lt;track&gt;-arrangement.txt,
 *                                               DIR/&lt;track&gt;-range-NN.txt and DIR/&lt;track&gt;-arrangement.json
 *   guitar-arrange --tracks request.json        list the tracks of the request's source
 *   guitar-arrange --titles                     list library titles and their variants
 *   guitar-arrange --example FILE               write a fully populated example request
 * </pre>
 *
 * <p>With the shaded jar: {@code java -jar notation-guitar/target/guitar-arrange.jar request.json};
 * from Maven: {@code mvn -pl notation-guitar exec:java -Dexec.args="request.json"}.</p>
 */
public final class GuitarArrangeCli {

    private GuitarArrangeCli() {}

    /** The library needs {@code -Dmusic.collections=…}; default to a {@code collections.json} near the working directory. */
    static void locateCollections() {
        if (System.getProperty(PieceLibrary.CONFIG_PROPERTY) != null) return;
        for (Path candidate : List.of(Path.of("collections.json"), Path.of("..", "collections.json"))) {
            if (Files.isRegularFile(candidate)) {
                System.setProperty(PieceLibrary.CONFIG_PROPERTY, candidate.toAbsolutePath().normalize().toString());
                return;
            }
        }
    }

    public static void main(String[] args) {
        try {
            locateCollections();
            var out = new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), true, StandardCharsets.UTF_8);
            System.exit(run(args, out));
        } catch (UnplayableNotesException e) {
            System.err.println("Unplayable: " + e.getMessage());
            System.err.println("Hint: set hand.foldOutOfRange to true, raise hand.maxFret, or pick a bar range.");
            System.exit(3);
        } catch (IllegalArgumentException | java.io.UncheckedIOException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(2);
        }
    }

    static int run(String[] args, java.io.PrintStream out) {
        if (args.length == 0 || args[0].equals("--help") || args[0].equals("-h")) { usage(out); return 1; }
        switch (args[0]) {
            case "--titles" -> { listTitles(out); return 0; }
            case "--example" -> {
                if (args.length < 2) { usage(out); return 1; }
                ArrangementJson.write(Path.of(args[1]), exampleRequest());
                out.println("Wrote " + args[1]);
                return 0;
            }
            case "--tracks" -> {
                if (args.length < 2) { usage(out); return 1; }
                listTracks(ArrangementJson.readRequest(Path.of(args[1])), out);
                return 0;
            }
            default -> { }
        }

        Path requestFile = Path.of(args[0]);
        Path outDir = Path.of("guitar-out");
        for (int i = 1; i < args.length; i++) {
            if (args[i].equals("--out") && i + 1 < args.length) outDir = Path.of(args[++i]);
            else throw new IllegalArgumentException("unknown argument: " + args[i]);
        }

        ArrangementRequest request = ArrangementJson.readRequest(requestFile);
        Arrangement arrangement = Arranger.arrange(request);
        List<Path> written = writeAll(arrangement, outDir);

        out.println(arrangement.title() + " · " + arrangement.track() + " · " + arrangement.bars());
        out.println(arrangement.fingering().notes().size() + " notes, " + arrangement.ranges().size()
                + " practice ranges, " + arrangement.fingering().shifts() + " position shifts, cost "
                + String.format("%.1f", arrangement.fingering().cost()));
        for (PracticeRange r : arrangement.ranges()) out.println("  " + r.summary());
        out.println("Written:");
        for (Path p : written) out.println("  " + p);
        return 0;
    }

    /** Writes the full text, one text per range, and the arrangement JSON. Returns the paths. */
    public static List<Path> writeAll(Arrangement a, Path outDir) {
        String stem = safe(a.track());
        var written = new java.util.ArrayList<Path>();
        try {
            Files.createDirectories(outDir);
            Path full = outDir.resolve(stem + "-arrangement.txt");
            Files.writeString(full, TabRenderer.render(a), StandardCharsets.UTF_8);
            written.add(full);
            for (PracticeRange r : a.ranges()) {
                Path p = outDir.resolve(String.format("%s-range-%02d.txt", stem, r.index()));
                Files.writeString(p, TabRenderer.renderRange(a, r), StandardCharsets.UTF_8);
                written.add(p);
            }
            Path json = outDir.resolve(stem + "-arrangement.json");
            ArrangementJson.write(json, a);
            written.add(json);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException("cannot write to " + outDir + ": " + e.getMessage(), e);
        }
        return written;
    }

    private static String safe(String name) {
        String s = name.replaceAll("[^\\p{L}\\p{N}]+", "-").replaceAll("^-|-$", "");
        return s.isEmpty() ? "track" : s;
    }

    private static void listTitles(java.io.PrintStream out) {
        for (String title : PieceLibrary.titles()) {
            out.println(title);
            var providers = PieceLibrary.providers(title);
            for (int i = 0; i < providers.size(); i++) {
                out.println("    [" + i + "] " + providers.get(i).subtitle());
            }
        }
    }

    private static void listTracks(ArrangementRequest request, java.io.PrintStream out) {
        var loaded = SourceLoader.load(request.source());
        Meter meter = Meter.of(loaded.performance());
        out.println(loaded.title());
        for (Track t : loaded.performance().score().tracks()) {
            var pitched = t.notes().stream().filter(n -> n instanceof PitchedNote).map(n -> (PitchedNote) n).toList();
            if (pitched.isEmpty()) { out.println("  " + t.id().name() + "  (no pitched notes)"); continue; }
            var stats = pitched.stream().mapToInt(PitchedNote::midi).summaryStatistics();
            int firstBar = meter.barOf(pitched.get(0).at()) + 1;
            int lastBar = pitched.stream().mapToInt(n -> meter.barOf(n.at())).max().orElseThrow() + 1;
            out.printf("  %-14s %4d notes · bars %d–%d · midi %d–%d%n",
                    t.id().name(), pitched.size(), firstBar, lastBar, stats.getMin(), stats.getMax());
        }
    }

    static ArrangementRequest exampleRequest() {
        return new ArrangementRequest(
                new PieceSource.Library("春晓 / 静夜思 (Spring Dawn / Quiet Night Thoughts)", "Super Blue in B♭"),
                "Solo 1", new BarRange(17, 24), Tuning.STANDARD, HandConstraints.DEFAULT, CostWeights.DEFAULT, BreakdownSpec.DEFAULT);
    }

    private static void usage(java.io.PrintStream out) {
        out.println("""
                guitar-arrange request.json [--out DIR]   arrange a track; text + JSON into DIR (default ./guitar-out)
                guitar-arrange --tracks request.json      list the tracks of the request's source
                guitar-arrange --titles                   list library titles and their variants
                guitar-arrange --example FILE             write an example request JSON to FILE""");
    }
}
