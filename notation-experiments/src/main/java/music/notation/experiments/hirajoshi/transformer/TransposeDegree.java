package music.notation.experiments.hirajoshi.transformer;

import music.notation.core.model.transformation.Transformer;
import music.notation.experiments.hirajoshi.HirajoshiDegree;
import music.notation.experiments.hirajoshi.HirajoshiNote;

/**
 * Shift every note up by {@code steps} scale degrees (cyclic). Wrap-around
 * into the next octave carries the octave counter with it.
 *
 * <p>Example: {@code TransposeDegree(1)} applied to {@code (III, octave=4)}
 * yields {@code (V, octave=4)}. Applied to {@code (VI, octave=4)} it yields
 * {@code (I, octave=5)} — wrapping past the top of the scale bumps the
 * octave.</p>
 *
 * <p>Reversible: the inverse is {@code TransposeDegree(-steps)}, which the
 * {@code reverse} method implements directly (without constructing a new
 * object) — saves allocation on every call.</p>
 */
public record TransposeDegree(int steps) implements Transformer<HirajoshiNote, HirajoshiNote> {

    private static final int DEGREE_COUNT = HirajoshiDegree.values().length;

    @Override
    public HirajoshiNote forward(HirajoshiNote note) {
        return shift(note, steps);
    }

    @Override
    public HirajoshiNote reverse(HirajoshiNote note) {
        return shift(note, -steps);
    }

    private static HirajoshiNote shift(HirajoshiNote note, int delta) {
        final int rawIndex = note.degree().index() + delta;
        final int octaveShift = Math.floorDiv(rawIndex, DEGREE_COUNT);
        final HirajoshiDegree newDegree = HirajoshiDegree.ofIndex(rawIndex);
        return new HirajoshiNote(newDegree, note.octave() + octaveShift);
    }
}
