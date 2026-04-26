package music.notation.experiments.chord;

import music.notation.core.model.transformation.Transformer;
import music.notation.experiments.scale.ScaleNote;

/**
 * Reversible shape transform: reshape a chord from one layout to another
 * without touching its voices or duration.
 *
 * <p>Example: block → arpeggio-up as a {@code Transformer<ScaleChord<N>,
 * ScaleChord<N>>}. The voices and duration are preserved exactly, so the
 * inverse is constructor-trivial — a {@code new ChangeChordShape<>(to,
 * from)} reverses the move.</p>
 *
 * <p>The transformer verifies (at each forward/reverse call) that the
 * input chord's current shape matches the expected {@code from} —
 * catching misapplied chains early.</p>
 */
public record ChangeChordShape<N extends ScaleNote>(ChordShape from, ChordShape to)
        implements Transformer<ScaleChord<N>, ScaleChord<N>> {

    public ChangeChordShape {
        if (from == null || to == null) {
            throw new IllegalArgumentException("from and to must not be null");
        }
    }

    @Override
    public ScaleChord<N> forward(ScaleChord<N> chord) {
        requireShape(chord, from);
        return new ScaleChord<>(chord.voices(), chord.durationMs(), to);
    }

    @Override
    public ScaleChord<N> reverse(ScaleChord<N> chord) {
        requireShape(chord, to);
        return new ScaleChord<>(chord.voices(), chord.durationMs(), from);
    }

    private static void requireShape(ScaleChord<?> chord, ChordShape expected) {
        if (chord.shape() != expected) {
            throw new IllegalStateException(
                    "ChangeChordShape expected shape " + expected
                            + " but received " + chord.shape());
        }
    }

    /** Convenience: the common "block → up arpeggio" move. */
    public static <N extends ScaleNote> ChangeChordShape<N> blockToArpeggioUp() {
        return new ChangeChordShape<>(ChordShape.BLOCK, ChordShape.ARPEGGIO_UP);
    }

    /** Convenience: "block → down arpeggio". */
    public static <N extends ScaleNote> ChangeChordShape<N> blockToArpeggioDown() {
        return new ChangeChordShape<>(ChordShape.BLOCK, ChordShape.ARPEGGIO_DOWN);
    }
}
