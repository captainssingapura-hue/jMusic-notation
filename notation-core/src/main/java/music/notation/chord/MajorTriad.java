package music.notation.chord;

import music.notation.pitch.Accidental;
import music.notation.pitch.NoteName;
import music.notation.pitch.Pitch;

import java.util.List;

/** Root-position major triad: root – major 3rd – perfect 5th. */
public record MajorTriad(NoteName root, Accidental accidental, int octave) implements Chord {

    public MajorTriad(final NoteName root, final int octave) {
        this(root, Accidental.NATURAL, octave);
    }

    @Override
    public List<Pitch> pitches() {
        return List.of(
                Intervals.above(root, accidental, octave, 0, 0),   // root
                Intervals.above(root, accidental, octave, 2, 4),   // major 3rd
                Intervals.above(root, accidental, octave, 4, 7));  // perfect 5th
    }
}
