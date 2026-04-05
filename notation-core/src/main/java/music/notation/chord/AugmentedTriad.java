package music.notation.chord;

import music.notation.pitch.Accidental;
import music.notation.pitch.NoteName;
import music.notation.pitch.Pitch;

import java.util.List;

/** Root-position augmented triad: root – major 3rd – augmented 5th. */
public record AugmentedTriad(NoteName root, Accidental accidental, int octave) implements Chord {

    public AugmentedTriad(final NoteName root, final int octave) {
        this(root, Accidental.NATURAL, octave);
    }

    @Override
    public List<Pitch> pitches() {
        return List.of(
                Intervals.above(root, accidental, octave, 0, 0),   // root
                Intervals.above(root, accidental, octave, 2, 4),   // major 3rd
                Intervals.above(root, accidental, octave, 4, 8));  // augmented 5th
    }
}
