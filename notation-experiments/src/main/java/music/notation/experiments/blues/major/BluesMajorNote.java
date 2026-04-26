package music.notation.experiments.blues.major;

import music.notation.experiments.scale.ScaleNote;

public record BluesMajorNote(BluesMajorDegree degree, int octave) implements ScaleNote {

    public BluesMajorNote {
        if (degree == null) throw new IllegalArgumentException("degree must not be null");
    }

    public static BluesMajorNote of(BluesMajorDegree degree, int octave) {
        return new BluesMajorNote(degree, octave);
    }

    public static BluesMajorNote ofIndex(int degreeIndex, int octave) {
        return new BluesMajorNote(BluesMajorDegree.ofIndex(degreeIndex), octave);
    }

    @Override public int degreeIndex() { return degree.index(); }
    @Override public int degreeCount() { return BluesMajorDegree.values().length; }
    @Override public String scaleName() { return "Blues Major"; }
}
