package music.notation.songs;

import music.notation.structure.Collection;
import music.notation.structure.Collection.Entry;
import music.notation.structure.MusicalPiece;
import music.notation.structure.Piece;
import music.notation.structure.PieceContentProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Registry of {@link MusicalPiece} identities and their
 * {@link PieceContentProvider}s, populated from {@link Collection}
 * implementations listed in an external JSON configuration file.
 *
 * <p>The path to the configuration file is specified via the system
 * property {@code music.collections}.  The file maps collection names
 * to fully-qualified class names:
 *
 * <pre>{@code
 * {
 *     "Built-in Songs": "music.notation.songs.DefaultCollection",
 *     "My Custom Songs": "com.example.MyCollection"
 * }
 * }</pre>
 *
 * <p>If the system property is not set, the library falls back to an
 * empty registry.</p>
 */
public final class PieceLibrary {

    private PieceLibrary() {}

    /** System property that points to the collections JSON file. */
    public static final String CONFIG_PROPERTY = "music.collections";

    /** Identity class → identity instance, insertion-ordered by title. */
    private static final Map<Class<? extends MusicalPiece>, MusicalPiece> IDENTITIES;

    /** Identity class → all discovered providers (first = default). */
    private static final Map<Class<? extends MusicalPiece>, List<PieceContentProvider<?>>> PROVIDERS;

    /** Identity class → lazily cached Piece from the default provider. */
    private static final Map<Class<? extends MusicalPiece>, Piece> CACHE = new LinkedHashMap<>();

    static {
        final var collections = loadCollections();

        final var identities = new LinkedHashMap<Class<? extends MusicalPiece>, MusicalPiece>();
        final var providers = new LinkedHashMap<Class<? extends MusicalPiece>, List<PieceContentProvider<?>>>();

        for (final Collection collection : collections) {
            for (final Entry<?> entry : collection.entries()) {
                @SuppressWarnings("unchecked")
                final var key = (Class<? extends MusicalPiece>) entry.identity().getClass();
                identities.putIfAbsent(key, entry.identity());
                providers.computeIfAbsent(key, k -> new ArrayList<>())
                        .addAll(entry.providers());
            }
        }

        // Sort by title for stable UI display
        final var sorted = new LinkedHashMap<Class<? extends MusicalPiece>, MusicalPiece>();
        identities.entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getValue().title()))
                .forEach(e -> sorted.put(e.getKey(), e.getValue()));

        final var sortedProviders = new LinkedHashMap<Class<? extends MusicalPiece>, List<PieceContentProvider<?>>>();
        for (final var key : sorted.keySet()) {
            sortedProviders.put(key, List.copyOf(providers.get(key)));
        }

        IDENTITIES = Collections.unmodifiableMap(sorted);
        PROVIDERS = Collections.unmodifiableMap(sortedProviders);
    }

    // ── Configuration loading ───────────────────────────────────────

    private static List<Collection> loadCollections() {
        final String configPath = System.getProperty(CONFIG_PROPERTY);
        if (configPath == null || configPath.isBlank()) {
            return List.of();
        }

        final String json;
        try {
            json = Files.readString(Path.of(configPath));
        } catch (final IOException e) {
            throw new RuntimeException("Failed to read collections config: " + configPath, e);
        }

        final Map<String, String> entries = parseJsonMap(json);
        final var result = new ArrayList<Collection>();
        for (final var entry : entries.entrySet()) {
            final String name = entry.getKey();
            final String className = entry.getValue();
            try {
                final Class<?> clazz = Class.forName(className);
                if (!Collection.class.isAssignableFrom(clazz)) {
                    throw new RuntimeException(
                            "Class " + className + " does not implement Collection");
                }
                result.add((Collection) clazz.getDeclaredConstructor().newInstance());
            } catch (final ReflectiveOperationException e) {
                throw new RuntimeException(
                        "Failed to load collection '" + name + "' (" + className + ")", e);
            }
        }
        return result;
    }

    /**
     * Parse a simple JSON object with string keys and string values.
     * No external library needed for this flat structure.
     */
    private static Map<String, String> parseJsonMap(final String json) {
        final var result = new LinkedHashMap<String, String>();
        final Pattern pair = Pattern.compile("\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"\\s*:\\s*\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"");
        final Matcher m = pair.matcher(json);
        while (m.find()) {
            result.put(unescape(m.group(1)), unescape(m.group(2)));
        }
        return result;
    }

    private static String unescape(final String s) {
        return s.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    // ── Public API ──────────────────────────────────────────────────

    /** All discovered piece identities, sorted by title. */
    public static List<MusicalPiece> pieces() {
        return List.copyOf(IDENTITIES.values());
    }

    /** Ordered list of titles (convenience for UI combo boxes). */
    public static List<String> titles() {
        return IDENTITIES.values().stream()
                .map(MusicalPiece::title)
                .toList();
    }

    /** Look up a {@link Piece} by its identity class (uses the default provider). */
    public static <P extends MusicalPiece> Piece get(final Class<P> pieceType) {
        return CACHE.computeIfAbsent(pieceType, k -> {
            final var list = PROVIDERS.get(k);
            if (list == null || list.isEmpty()) {
                throw new IllegalArgumentException("No provider for " + k.getSimpleName());
            }
            return list.getFirst().create();
        });
    }

    /** All providers registered for a given piece identity. */
    public static <P extends MusicalPiece> List<PieceContentProvider<?>> providers(
            final Class<P> pieceType) {
        return PROVIDERS.getOrDefault(pieceType, List.of());
    }

    /** Look up a {@link Piece} by title string (uses the default provider). */
    public static Piece get(final String title) {
        for (final var entry : IDENTITIES.entrySet()) {
            if (entry.getValue().title().equals(title)) {
                return get(entry.getKey());
            }
        }
        return null;
    }

    /** Look up the identity class for a piece title. */
    public static Class<? extends MusicalPiece> identityClass(final String title) {
        for (final var entry : IDENTITIES.entrySet()) {
            if (entry.getValue().title().equals(title)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** All providers registered for a given piece title. */
    public static List<PieceContentProvider<?>> providers(final String title) {
        final var key = identityClass(title);
        return key == null ? List.of() : providers(key);
    }
}
