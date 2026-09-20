package music.notation.guitar;

import music.notation.mxl.MxlSplitJsonReader;
import music.notation.performance.Performance;
import music.notation.play.PieceConcretizer;
import music.notation.songs.PieceLibrary;
import music.notation.structure.PieceContentProvider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Turns a {@link PieceSource} into a concrete {@link Performance} with a display title. */
public final class SourceLoader {

    private SourceLoader() {}

    public record Loaded(String title, Performance performance) {}

    public static Loaded load(PieceSource source) {
        return switch (source) {
            case PieceSource.Library lib -> fromLibrary(lib);
            case PieceSource.Folder folder -> fromFolder(folder);
        };
    }

    private static Loaded fromLibrary(PieceSource.Library lib) {
        List<PieceContentProvider<?>> providers = PieceLibrary.providers(lib.title());
        if (providers.isEmpty()) {
            throw new IllegalArgumentException("no library piece titled '" + lib.title() + "'. Known titles:\n  "
                    + String.join("\n  ", PieceLibrary.titles()));
        }
        PieceContentProvider<?> chosen = pick(providers, lib.variant());
        String subtitle = chosen.subtitle();
        String title = subtitle == null || subtitle.isBlank() ? lib.title() : lib.title() + " — " + subtitle;
        return new Loaded(title, PieceConcretizer.concretize(chosen.create()));
    }

    private static PieceContentProvider<?> pick(List<PieceContentProvider<?>> providers, String variant) {
        if (variant.isBlank()) return providers.get(0);
        for (PieceContentProvider<?> p : providers) {
            if (variant.equalsIgnoreCase(String.valueOf(p.subtitle()))) return p;
        }
        try {
            int idx = Integer.parseInt(variant.trim());
            if (idx >= 0 && idx < providers.size()) return providers.get(idx);
        } catch (NumberFormatException ignored) { /* not an index */ }
        StringBuilder known = new StringBuilder();
        for (int i = 0; i < providers.size(); i++) known.append("\n  [").append(i).append("] ").append(providers.get(i).subtitle());
        throw new IllegalArgumentException("no variant '" + variant + "'. Known variants:" + known);
    }

    private static Loaded fromFolder(PieceSource.Folder folder) {
        Path dir = Path.of(folder.path());
        if (!Files.isDirectory(dir)) throw new IllegalArgumentException("not a piece folder: " + dir);
        var imp = MxlSplitJsonReader.read(dir);
        String title = imp.displayName() == null || imp.displayName().isBlank() ? dir.getFileName().toString() : imp.displayName();
        return new Loaded(title, imp.performance());
    }
}
