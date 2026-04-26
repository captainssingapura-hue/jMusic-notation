package music.notation.experiments.blues.minor;

import music.notation.experiments.scale.ScaleNote;

public record BluesMinorNote(BluesMinorDegree degree, int octave) implements ScaleNote {

    public BluesMinorNote {
        if (degree == null) throw new IllegalArgumentException("degree must not be null");
    }

    public static BluesMinorNote of(BluesMinorDegree degree, int octave) {
        return new BluesMinorNote(degree, octave);
    }

    public static BluesMinorNote ofIndex(int degreeIndex, int octave) {
        return new BluesMinorNote(BluesMinorDegree.ofIndex(degreeIndex), octave);
    }

    @Override public int degreeIndex() { return degree.index(); }
    @Override public int degreeCount() { return BluesMinorDegree.values().length; }
    @Override public String scaleName() { return "Blues Minor"; }
}
