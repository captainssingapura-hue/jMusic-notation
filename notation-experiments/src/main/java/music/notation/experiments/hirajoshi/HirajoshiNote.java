package music.notation.experiments.hirajoshi;

import music.notation.experiments.scale.ScaleNote;

/**
 * A note expressed in Hirajoshi-scale terms: {@link HirajoshiDegree degree}
 * and {@code octave}, no bound tonic.
 *
 * <p>Implements {@link ScaleNote} so it participates in cross-scale
 * operations (transpose into Yo, Insen, Iwato, Ryukyu) purely by
 * degree-index mapping.</p>
 */
public record HirajoshiNote(HirajoshiDegree degree, int octave) implements ScaleNote {

    public HirajoshiNote {
        if (degree == null) {
            throw new IllegalArgumentException("degree must not be null");
        }
    }

    public static HirajoshiNote of(HirajoshiDegree degree, int octave) {
        return new HirajoshiNote(degree, octave);
    }

    /** Reconstruction-by-index factory. Suitable as a {@code ScaleFactory} reference. */
    public static HirajoshiNote ofIndex(int degreeIndex, int octave) {
        return new HirajoshiNote(HirajoshiDegree.ofIndex(degreeIndex), octave);
    }

    @Override public int degreeIndex() { return degree.index(); }
    @Override public int degreeCount() { return HirajoshiDegree.values().length; }
    @Override public String scaleName() { return "Hirajoshi"; }
}
