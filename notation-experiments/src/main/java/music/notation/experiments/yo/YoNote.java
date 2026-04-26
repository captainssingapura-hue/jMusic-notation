package music.notation.experiments.yo;

import music.notation.experiments.scale.ScaleNote;

public record YoNote(YoDegree degree, int octave) implements ScaleNote {

    public YoNote {
        if (degree == null) throw new IllegalArgumentException("degree must not be null");
    }

    public static YoNote of(YoDegree degree, int octave) {
        return new YoNote(degree, octave);
    }

    public static YoNote ofIndex(int degreeIndex, int octave) {
        return new YoNote(YoDegree.ofIndex(degreeIndex), octave);
    }

    @Override public int degreeIndex() { return degree.index(); }
    @Override public int degreeCount() { return YoDegree.values().length; }
    @Override public String scaleName() { return "Yo"; }
}
