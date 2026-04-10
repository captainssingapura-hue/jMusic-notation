package music.notation.structure;

/**
 * Produces a concrete {@link Piece} for a given {@link MusicalPiece} identity.
 *
 * <p>The generic parameter {@code P} ties every provider to exactly one
 * piece identity, making it impossible to accidentally register a
 * provider against the wrong piece.  Multiple providers for the same
 * {@code P} can coexist — e.g. a simplified arrangement, a jazz
 * arrangement, or a transposition — each producing a different
 * {@link Piece} while sharing the same logical identity.</p>
 *
 * <p>Example:
 * <pre>{@code
 *     public final class DefaultTwinkleStar implements PieceContentProvider<TwinkleStar> {
 *         @Override public Piece create() { ... }
 *     }
 * }</pre>
 *
 * @param <P> the piece identity this provider serves
 */
public interface PieceContentProvider<P extends MusicalPiece> {

    /** Build the full {@link Piece} (tracks, notes, tempo, etc.). */
    Piece create();

    /**
     * Short label distinguishing this provider from others for the same piece
     * (e.g. "Default", "Manual", "Jazz arrangement").
     *
     * <p>Falls back to a cleaned-up version of the class name.</p>
     */
    default String subtitle() {
        return getClass().getSimpleName();
    }
}
