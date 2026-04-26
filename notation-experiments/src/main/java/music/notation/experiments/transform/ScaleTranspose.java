package music.notation.experiments.transform;

import music.notation.core.model.transformation.Transformer;
import music.notation.experiments.scale.ScaleFactory;
import music.notation.experiments.scale.ScaleNote;

/**
 * Reversible transposition <em>between</em> two scales — preserves the
 * melodic contour (degree index, octave) while swapping the scale's
 * interval pattern, and therefore its colour.
 *
 * <p>Example: a phrase written in {@link music.notation.experiments.hirajoshi.HirajoshiNote}
 * can be moved into {@link music.notation.experiments.yo.YoNote} without
 * writing new notes — the same (degree, octave) pairs render through a
 * different scale's intervals after concretization.</p>
 *
 * <p>Reversibility is trivial: degree index and octave are preserved on
 * the trip, so {@code reverse(forward(x)) == x} for any note. A runtime
 * check in the constructor rejects mixing scales with different degree
 * counts (all Japanese pentatonic scales in this module share 5 degrees,
 * so the check is a safety net, not a common failure).</p>
 *
 * <p>Typical construction uses method references as factories:</p>
 * <pre>{@code
 *   new ScaleTranspose<>(HirajoshiNote::ofIndex, YoNote::ofIndex);
 * }</pre>
 */
public record ScaleTranspose<From extends ScaleNote, To extends ScaleNote>(
        ScaleFactory<From> fromFactory,
        ScaleFactory<To> toFactory
) implements Transformer<From, To> {

    public ScaleTranspose {
        final var sampleFrom = fromFactory.create(0, 0);
        final var sampleTo = toFactory.create(0, 0);
        if (sampleFrom.degreeCount() != sampleTo.degreeCount()) {
            throw new IllegalArgumentException(
                    "ScaleTranspose requires matching degree counts: "
                            + sampleFrom.scaleName() + " has "
                            + sampleFrom.degreeCount() + ", "
                            + sampleTo.scaleName() + " has "
                            + sampleTo.degreeCount());
        }
    }

    @Override
    public To forward(From note) {
        return toFactory.create(note.degreeIndex(), note.octave());
    }

    @Override
    public From reverse(To note) {
        return fromFactory.create(note.degreeIndex(), note.octave());
    }
}
