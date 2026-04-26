package music.notation.experiments.chinese.zhi;

import music.notation.experiments.scale.ScaleNote;

public record ZhiNote(ZhiDegree degree, int octave) implements ScaleNote {

    public ZhiNote {
        if (degree == null) throw new IllegalArgumentException("degree must not be null");
    }

    public static ZhiNote of(ZhiDegree degree, int octave) {
        return new ZhiNote(degree, octave);
    }

    public static ZhiNote ofIndex(int degreeIndex, int octave) {
        return new ZhiNote(ZhiDegree.ofIndex(degreeIndex), octave);
    }

    @Override public int degreeIndex() { return degree.index(); }
    @Override public int degreeCount() { return ZhiDegree.values().length; }
    @Override public String scaleName() { return "Zhi"; }
}
