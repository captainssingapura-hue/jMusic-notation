package music.notation.experiments.scale;

import music.notation.core.model.AbstractNote;

/**
 * An abstract note paired with how long it should sound. Lives at the
 * abstract layer — used when timing needs to be carried alongside scale
 * notes that don't themselves have a duration (a scale-degree position is
 * an identity, not a time in a piece).
 *
 * <p>Itself an {@link AbstractNote} so it fits framework contracts like
 * {@code Concretizer<TimedNote<N>, Note>}. {@code T} is bound to
 * {@code AbstractNote} for the same reason.</p>
 */
public record TimedNote<T extends AbstractNote>(T note, int durationMillis)
        implements AbstractNote {

    public TimedNote {
        if (note == null) {
            throw new IllegalArgumentException("note must not be null");
        }
        if (durationMillis <= 0) {
            throw new IllegalArgumentException(
                    "durationMillis must be positive: " + durationMillis);
        }
    }

    public static <T extends AbstractNote> TimedNote<T> of(T note, int durationMillis) {
        return new TimedNote<>(note, durationMillis);
    }
}
