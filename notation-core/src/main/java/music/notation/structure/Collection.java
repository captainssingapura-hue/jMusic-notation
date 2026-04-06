package music.notation.structure;

import java.util.List;

/**
 * A named group of {@link MusicalPiece} identities and their
 * {@link PieceContentProvider}s.
 *
 * <p>Implementations declare which pieces they contain and, for each
 * piece, the list of providers that can produce a {@link Piece}.  The
 * first provider in each entry's list is considered the default.</p>
 *
 * <p>Collections are discovered at runtime via {@link java.util.ServiceLoader}.
 * To register a collection, add the fully-qualified class name to
 * {@code META-INF/services/music.notation.structure.Collection}.</p>
 *
 * <p>Example:
 * <pre>{@code
 *     public final class MyCollection implements Collection {
 *         @Override public String name() { return "My Songs"; }
 *         @Override public List<Entry<?>> entries() {
 *             return List.of(
 *                 Entry.of(new TwinkleStar(), new DefaultTwinkleStar()),
 *                 Entry.of(new OdeToJoy(), new DefaultOdeToJoy(), new JazzOdeToJoy())
 *             );
 *         }
 *     }
 * }</pre>
 */
public interface Collection {

    /** Human-readable name of this collection. */
    String name();

    /** The pieces and their providers in this collection. */
    List<Entry<?>> entries();

    /**
     * Groups a {@link MusicalPiece} identity with one or more providers.
     *
     * @param <P> the piece identity type
     */
    record Entry<P extends MusicalPiece>(P identity, List<PieceContentProvider<P>> providers) {

        public Entry {
            if (providers.isEmpty()) {
                throw new IllegalArgumentException(
                        "Entry for " + identity.title() + " must have at least one provider");
            }
            providers = List.copyOf(providers);
        }

        /** Convenience factory for one or more providers. */
        @SafeVarargs
        public static <P extends MusicalPiece> Entry<P> of(
                final P identity, final PieceContentProvider<P>... providers) {
            return new Entry<>(identity, List.of(providers));
        }
    }
}
