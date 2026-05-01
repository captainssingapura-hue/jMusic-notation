package music.notation.pitch;

/**
 * A pitch in any notation system.
 *
 * <ul>
 *   <li>{@link StaffPitch} — Western staff notation (note name + accidental + octave)</li>
 * </ul>
 *
 * <p>Numbered notation (简谱) is preserved as an authoring DSL on
 * {@code NumberedPhraseBuilder}, but it translates degrees → {@code StaffPitch}
 * directly at the builder boundary; there is no separate numbered pitch type
 * in the model.</p>
 */
public sealed interface Pitch permits StaffPitch {

    /** Convenience factory for a natural staff pitch. */
    static Pitch of(NoteName noteName, int octave) {
        return StaffPitch.of(noteName, octave);
    }

    /** Convenience factory for a staff pitch with accidental. */
    static Pitch of(NoteName noteName, Accidental accidental, int octave) {
        return StaffPitch.of(noteName, accidental, octave);
    }
}
