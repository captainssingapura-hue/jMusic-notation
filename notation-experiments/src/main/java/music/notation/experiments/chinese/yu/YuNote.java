package music.notation.experiments.chinese.yu;

import music.notation.experiments.scale.ScaleNote;

public record YuNote(YuDegree degree, int octave) implements ScaleNote {

    public YuNote {
        if (degree == null) throw new IllegalArgumentException("degree must not be null");
    }

    public static YuNote of(YuDegree degree, int octave) {
        return new YuNote(degree, octave);
    }

    public static YuNote ofIndex(int degreeIndex, int octave) {
        return new YuNote(YuDegree.ofIndex(degreeIndex), octave);
    }

    @Override public int degreeIndex() { return degree.index(); }
    @Override public int degreeCount() { return YuDegree.values().length; }
    @Override public String scaleName() { return "Yu"; }
}
