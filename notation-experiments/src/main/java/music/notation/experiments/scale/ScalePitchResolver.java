package music.notation.experiments.scale;

/**
 * Pitch resolution for a scale family: {@code N → int midi}.
 *
 * <p>Complements (but is <em>not</em> the same as) the
 * {@link music.notation.core.model.Concretizer Concretizer} contract. A
 * bare scale note like {@code HirajoshiNote(III, 4)} doesn't carry
 * duration / velocity / voice — its identity is purely scale-position.
 * Resolving that into a MIDI integer is a pitch-only concern, distinct
 * from producing a {@link Note} (which requires timing).</p>
 *
 * <p>Scale concretizers implement this <em>in addition to</em> being
 * {@code Concretizer<TimedNote<N>, Note>}. Chord / arpeggio /
 * melody concretizers that only need pitch information use a
 * {@code ScalePitchResolver<N>} and combine it with their own timing
 * logic to emit {@link Note}s.</p>
 */
public interface ScalePitchResolver<N extends ScaleNote> {
    int midi(N note);
}
