package music.notation.experiments.chinese.jue;

import music.notation.experiments.scale.ScaleNote;

public record JueNote(JueDegree degree, int octave) implements ScaleNote {

    public JueNote {
        if (degree == null) throw new IllegalArgumentException("degree must not be null");
    }

    public static JueNote of(JueDegree degree, int octave) {
        return new JueNote(degree, octave);
    }

    public static JueNote ofIndex(int degreeIndex, int octave) {
        return new JueNote(JueDegree.ofIndex(degreeIndex), octave);
    }

    @Override public int degreeIndex() { return degree.index(); }
    @Override public int degreeCount() { return JueDegree.values().length; }
    @Override public String scaleName() { return "Jue"; }
}
