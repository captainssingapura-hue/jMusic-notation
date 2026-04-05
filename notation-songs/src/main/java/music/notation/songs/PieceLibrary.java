package music.notation.songs;

import com.google.common.reflect.ClassPath;
import music.notation.structure.MusicalPiece;
import music.notation.structure.Piece;
import music.notation.structure.PieceContentProvider;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

/**
 * Auto-discovered registry of {@link MusicalPiece} identities and their
 * {@link PieceContentProvider}s.
 *
 * <p>At class-load time, the library scans the classpath (via Guava
 * {@link ClassPath}) for all concrete implementations of
 * {@code MusicalPiece} and {@code PieceContentProvider}.  Each provider's
 * generic type signature ({@code PieceContentProvider<P>}) is inspected
 * to link it to the {@code MusicalPiece} it serves.  Adding a new song
 * requires only writing the identity record and a provider — no
 * registration boilerplate.</p>
 */
public final class PieceLibrary {

    private PieceLibrary() {}

    private static final String SCAN_PACKAGE = "music.notation";

    /** Identity class → identity instance, insertion-ordered by title. */
    private static final Map<Class<? extends MusicalPiece>, MusicalPiece> IDENTITIES;

    /** Identity class → all discovered providers (first = default). */
    private static final Map<Class<? extends MusicalPiece>, List<PieceContentProvider<?>>> PROVIDERS;

    /** Identity class → lazily cached Piece from the default provider. */
    private static final Map<Class<? extends MusicalPiece>, Piece> CACHE = new LinkedHashMap<>();

    static {
        try {
            final var result = scan();
            IDENTITIES = Collections.unmodifiableMap(result.identities);
            PROVIDERS = Collections.unmodifiableMap(result.providers);
        } catch (final IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    // ── Classpath scanning ──────────────────────────────────────────

    private record ScanResult(
            LinkedHashMap<Class<? extends MusicalPiece>, MusicalPiece> identities,
            LinkedHashMap<Class<? extends MusicalPiece>, List<PieceContentProvider<?>>> providers) {}

    private static ScanResult scan() throws IOException {
        final var classPath = ClassPath.from(PieceLibrary.class.getClassLoader());
        final var topLevelClasses = classPath.getTopLevelClassesRecursive(SCAN_PACKAGE);

        // Pass 1: discover all concrete MusicalPiece implementations
        final var pieceInstances = new HashMap<Class<? extends MusicalPiece>, MusicalPiece>();
        for (final var info : topLevelClasses) {
            final Class<?> clazz = tryLoad(info);
            if (clazz != null && isConcrete(clazz) && MusicalPiece.class.isAssignableFrom(clazz)) {
                @SuppressWarnings("unchecked")
                final var pieceClass = (Class<? extends MusicalPiece>) clazz;
                final var instance = (MusicalPiece) instantiate(pieceClass);
                if (instance != null) {
                    pieceInstances.put(pieceClass, instance);
                }
            }
        }

        // Pass 2: discover all concrete PieceContentProvider implementations,
        //         resolve their generic <P> to link to a MusicalPiece class
        final var providerMap = new HashMap<Class<? extends MusicalPiece>, List<PieceContentProvider<?>>>();
        for (final var info : topLevelClasses) {
            final Class<?> clazz = tryLoad(info);
            if (clazz != null && isConcrete(clazz) && PieceContentProvider.class.isAssignableFrom(clazz)) {
                final var pieceType = resolvePieceType(clazz);
                if (pieceType != null && pieceInstances.containsKey(pieceType)) {
                    @SuppressWarnings("unchecked")
                    final var provider = (PieceContentProvider<?>) instantiate(clazz);
                    if (provider != null) {
                        providerMap.computeIfAbsent(pieceType, k -> new ArrayList<>()).add(provider);
                    }
                }
            }
        }

        // Sort identities alphabetically by title for stable UI display
        final var sorted = new LinkedHashMap<Class<? extends MusicalPiece>, MusicalPiece>();
        pieceInstances.entrySet().stream()
                .filter(e -> providerMap.containsKey(e.getKey()))   // only pieces that have ≥1 provider
                .sorted(Comparator.comparing(e -> e.getValue().title()))
                .forEach(e -> sorted.put(e.getKey(), e.getValue()));

        final var sortedProviders = new LinkedHashMap<Class<? extends MusicalPiece>, List<PieceContentProvider<?>>>();
        for (final var key : sorted.keySet()) {
            sortedProviders.put(key, List.copyOf(providerMap.get(key)));
        }

        return new ScanResult(sorted, sortedProviders);
    }

    /**
     * Walk the class's generic interfaces to find
     * {@code PieceContentProvider<P>} and extract {@code P}.
     */
    @SuppressWarnings("unchecked")
    private static Class<? extends MusicalPiece> resolvePieceType(final Class<?> providerClass) {
        for (final Type iface : providerClass.getGenericInterfaces()) {
            if (iface instanceof ParameterizedType pt
                    && pt.getRawType() == PieceContentProvider.class) {
                final Type arg = pt.getActualTypeArguments()[0];
                if (arg instanceof Class<?> c && MusicalPiece.class.isAssignableFrom(c)) {
                    return (Class<? extends MusicalPiece>) c;
                }
            }
        }
        return null;
    }

    private static boolean isConcrete(final Class<?> clazz) {
        final int mod = clazz.getModifiers();
        return !Modifier.isAbstract(mod) && !Modifier.isInterface(mod);
    }

    private static Class<?> tryLoad(final ClassPath.ClassInfo info) {
        try {
            return info.load();
        } catch (final Throwable t) {
            return null;     // skip unloadable classes (e.g. missing optional deps)
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T instantiate(final Class<?> clazz) {
        try {
            final var ctor = clazz.getDeclaredConstructor();
            ctor.setAccessible(true);
            return (T) ctor.newInstance();
        } catch (final ReflectiveOperationException e) {
            return null;     // skip classes we can't instantiate
        }
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

    /** Look up a {@link Piece} by title string (backward-compatible). */
    public static Piece get(final String title) {
        for (final var entry : IDENTITIES.entrySet()) {
            if (entry.getValue().title().equals(title)) {
                return get(entry.getKey());
            }
        }
        return null;
    }
}
