package music.notation.pitch;

/**
 * A pitch in any notation system.
 *
 * <ul>
 *   <li>{@link StaffPitch} — Western staff notation (note name + accidental + octave)</li>
 *   <li>{@link NumberedPitch} — Numbered notation / 简谱 (tonic + scale degree + octave)</li>
 * </ul>
 */
public sealed interface Pitch permits StaffPitch, NumberedPitch {

    /** Convenience factory for a natural staff pitch. */
    static Pitch of(NoteName noteName, int octave) {
        return StaffPitch.of(noteName, octave);
    }

    /** Convenience factory for a staff pitch with accidental. */
    static Pitch of(NoteName noteName, Accidental accidental, int octave) {
        return StaffPitch.of(noteName, accidental, octave);
    }
}
