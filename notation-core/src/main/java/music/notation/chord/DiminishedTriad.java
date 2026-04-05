package music.notation.chord;

import music.notation.pitch.Accidental;
import music.notation.pitch.NoteName;
import music.notation.pitch.Pitch;

import java.util.List;

/** Root-position diminished triad: root – minor 3rd – diminished 5th. */
public record DiminishedTriad(NoteName root, Accidental accidental, int octave) implements Chord {

    public DiminishedTriad(final NoteName root, final int octave) {
        this(root, Accidental.NATURAL, octave);
    }

    @Override
    public List<Pitch> pitches() {
        return List.of(
                Intervals.above(root, accidental, octave, 0, 0),   // root
                Intervals.above(root, accidental, octave, 2, 3),   // minor 3rd
                Intervals.above(root, accidental, octave, 4, 6));  // diminished 5th
    }
}
