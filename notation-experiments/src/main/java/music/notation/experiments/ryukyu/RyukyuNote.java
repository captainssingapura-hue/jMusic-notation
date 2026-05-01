package music.notation.experiments.ryukyu;

import music.notation.experiments.scale.ScaleNote;

public record RyukyuNote(RyukyuDegree degree, int octave) implements ScaleNote {

    public RyukyuNote {
        if (degree == null) throw new IllegalArgumentException("degree must not be null");
    }

    public static RyukyuNote of(RyukyuDegree degree, int octave) {
        return new RyukyuNote(degree, octave);
    }

    public static RyukyuNote ofIndex(int degreeIndex, int octave) {
        return new RyukyuNote(RyukyuDegree.ofIndex(degreeIndex), octave);
    }

    @Override public int degreeIndex() { return degree.index(); }
    @Override public int degreeCount() { return RyukyuDegree.values().length; }
    @Override public String scaleName() { return "Ryukyu"; }
}
