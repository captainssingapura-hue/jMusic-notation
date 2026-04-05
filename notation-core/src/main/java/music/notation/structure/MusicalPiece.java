package music.notation.structure;

/**
 * Stateless identity marker for a musical piece.
 *
 * <p>Each composition is modelled as a singleton record that implements
 * this interface.  The record's <em>class</em> serves as the unique
 * identity — two records of the same type always represent the same
 * piece regardless of provider or arrangement.</p>
 *
 * <p>Example:
 * <pre>{@code
 *     public record TwinkleStar() implements MusicalPiece {
 *         @Override public String title()    { return "Twinkle, Twinkle, Little Star"; }
 *         @Override public String composer() { return "Traditional"; }
 *     }
 * }</pre>
 */
public interface MusicalPiece {

    /** Human-readable title of the piece. */
    String title();

    /** Composer or attribution. */
    String composer();
}
