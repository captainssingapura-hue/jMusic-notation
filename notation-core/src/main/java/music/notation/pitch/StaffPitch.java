package music.notation.pitch;

/**
 * A pitch in Western staff notation: note name + accidental + octave.
 */
public record StaffPitch(NoteName noteName, Accidental accidental, Octave octave) implements Pitch {

    public static StaffPitch of(NoteName noteName, int octave) {
        return new StaffPitch(noteName, Accidental.NATURAL, new Octave(octave));
    }

    public static StaffPitch of(NoteName noteName, Accidental accidental, int octave) {
        return new StaffPitch(noteName, accidental, new Octave(octave));
    }
}
