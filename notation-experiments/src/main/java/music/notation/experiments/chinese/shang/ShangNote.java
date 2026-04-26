package music.notation.experiments.chinese.shang;

import music.notation.experiments.scale.ScaleNote;

public record ShangNote(ShangDegree degree, int octave) implements ScaleNote {

    public ShangNote {
        if (degree == null) throw new IllegalArgumentException("degree must not be null");
    }

    public static ShangNote of(ShangDegree degree, int octave) {
        return new ShangNote(degree, octave);
    }

    public static ShangNote ofIndex(int degreeIndex, int octave) {
        return new ShangNote(ShangDegree.ofIndex(degreeIndex), octave);
    }

    @Override public int degreeIndex() { return degree.index(); }
    @Override public int degreeCount() { return ShangDegree.values().length; }
    @Override public String scaleName() { return "Shang"; }
}
