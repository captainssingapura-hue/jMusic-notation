package music.notation.experiments.insen;

import music.notation.experiments.scale.ScaleNote;

public record InsenNote(InsenDegree degree, int octave) implements ScaleNote {

    public InsenNote {
        if (degree == null) throw new IllegalArgumentException("degree must not be null");
    }

    public static InsenNote of(InsenDegree degree, int octave) {
        return new InsenNote(degree, octave);
    }

    public static InsenNote ofIndex(int degreeIndex, int octave) {
        return new InsenNote(InsenDegree.ofIndex(degreeIndex), octave);
    }

    @Override public int degreeIndex() { return degree.index(); }
    @Override public int degreeCount() { return InsenDegree.values().length; }
    @Override public String scaleName() { return "Insen"; }
}
