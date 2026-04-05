package music.notation.chord;

import music.notation.pitch.Accidental;
import music.notation.pitch.NoteName;
import music.notation.pitch.Pitch;

import java.util.List;

/** Root-position dominant seventh: root – major 3rd – perfect 5th – minor 7th. */
public record DominantSeventh(NoteName root, Accidental accidental, int octave) implements Chord {

    public DominantSeventh(final NoteName root, final int octave) {
        this(root, Accidental.NATURAL, octave);
    }

    @Override
    public List<Pitch> pitches() {
        return List.of(
                Intervals.above(root, accidental, octave, 0,  0),  // root
                Intervals.above(root, accidental, octave, 2,  4),  // major 3rd
                Intervals.above(root, accidental, octave, 4,  7),  // perfect 5th
                Intervals.above(root, accidental, octave, 6, 10)); // minor 7th
    }
}
