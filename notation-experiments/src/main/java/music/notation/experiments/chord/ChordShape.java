package music.notation.experiments.chord;

/**
 * How a chord's voices are laid out in time.
 *
 * <ul>
 *   <li>{@link #BLOCK} — all voices attack simultaneously, sustain for the
 *       chord's full duration. A "block" / "bar" chord.</li>
 *   <li>{@link #ARPEGGIO_UP} — voices play sequentially in the order they
 *       are listed in the chord, each occupying one slot of the chord's
 *       duration. (List voices low→high for a rising arpeggio.)</li>
 *   <li>{@link #ARPEGGIO_DOWN} — voices play sequentially in reverse list
 *       order. (List voices low→high for a descending arpeggio.)</li>
 * </ul>
 *
 * <p>Shape is orthogonal to pitch identity — the same voices + duration
 * can be rendered in any shape, and {@code ChangeChordShape} moves between
 * them reversibly.</p>
 */
public enum ChordShape {
    BLOCK,
    ARPEGGIO_UP,
    ARPEGGIO_DOWN
}
