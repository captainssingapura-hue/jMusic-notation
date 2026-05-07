package music.notation.structure;

/**
 * Tonal mode label for a {@link KeySignature}.
 *
 * <p>{@link #NONE} is the "honest unknown" — used when the source (e.g.
 * a MusicXML score that omits {@code <mode>}) doesn't declare a mode.
 * Code that needs a default should pick its own; this enum stays
 * faithful to the source rather than fabricating a major/minor label.</p>
 */
public enum Mode {
    /** Mode not declared by the source — neither major nor minor was claimed. */
    NONE,
    MAJOR, MINOR,
    DORIAN, PHRYGIAN, LYDIAN, MIXOLYDIAN, AEOLIAN, LOCRIAN
}
