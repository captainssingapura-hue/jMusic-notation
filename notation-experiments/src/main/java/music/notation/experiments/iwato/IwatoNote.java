package music.notation.experiments.iwato;

import music.notation.experiments.scale.ScaleNote;

public record IwatoNote(IwatoDegree degree, int octave) implements ScaleNote {

    public IwatoNote {
        if (degree == null) throw new IllegalArgumentException("degree must not be null");
    }

    public static IwatoNote of(IwatoDegree degree, int octave) {
        return new IwatoNote(degree, octave);
    }

    public static IwatoNote ofIndex(int degreeIndex, int octave) {
        return new IwatoNote(IwatoDegree.ofIndex(degreeIndex), octave);
    }

    @Override public int degreeIndex() { return degree.index(); }
    @Override public int degreeCount() { return IwatoDegree.values().length; }
    @Override public String scaleName() { return "Iwato"; }
}
