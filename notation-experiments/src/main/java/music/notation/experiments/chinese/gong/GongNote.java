package music.notation.experiments.chinese.gong;

import music.notation.experiments.scale.ScaleNote;

public record GongNote(GongDegree degree, int octave) implements ScaleNote {

    public GongNote {
        if (degree == null) throw new IllegalArgumentException("degree must not be null");
    }

    public static GongNote of(GongDegree degree, int octave) {
        return new GongNote(degree, octave);
    }

    public static GongNote ofIndex(int degreeIndex, int octave) {
        return new GongNote(GongDegree.ofIndex(degreeIndex), octave);
    }

    @Override public int degreeIndex() { return degree.index(); }
    @Override public int degreeCount() { return GongDegree.values().length; }
    @Override public String scaleName() { return "Gong"; }
}
