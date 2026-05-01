package music.notation.event;

/**
 * Ornament marker — sealed ADT of empty record subtypes.
 *
 * <p>Each ornament is a real type that may grow parameters later
 * (trill speed, tremolo subdivision, etc.) without touching consumers.
 * Backward-compat singleton constants ({@code Ornament.TRILL} etc.)
 * preserve existing call sites; records have value equality, so each
 * singleton is {@code .equals()}-equivalent to a freshly constructed
 * instance of its subtype.</p>
 */
public sealed interface Ornament
        permits Trill, Mordent, LowerMordent, Turn, Tremolo, Appoggiatura, Acciaccatura {

    /** Backward-compat singleton: rapid alternation with note above. */
    Ornament TRILL         = new Trill();
    /** Backward-compat singleton: quick: note, note above, note. */
    Ornament MORDENT       = new Mordent();
    /** Backward-compat singleton: quick: note, note below, note. */
    Ornament LOWER_MORDENT = new LowerMordent();
    /** Backward-compat singleton: note above, note, note below, note. */
    Ornament TURN          = new Turn();
    /** Backward-compat singleton: rapid repetition of the same note. */
    Ornament TREMOLO       = new Tremolo();
    /** Backward-compat singleton: leaning note — takes time from main. */
    Ornament APPOGGIATURA  = new Appoggiatura();
    /** Backward-compat singleton: crushed note — very short, before the beat. */
    Ornament ACCIACCATURA  = new Acciaccatura();
}
