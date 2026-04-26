package music.notation.experiments.chord;

import music.notation.core.model.AbstractNote;
import music.notation.experiments.scale.ScaleNote;

import java.util.List;

/**
 * A chord in abstract scale-degree terms: an ordered list of
 * {@link ScaleNote voices}, a total duration, and a {@link ChordShape} that
 * describes how the voices are laid out in time when concretized.
 *
 * <p>Type-parameterised by the scale family ({@code N}): a
 * {@code ScaleChord<HirajoshiNote>} and a {@code ScaleChord<GongNote>} are
 * distinct types, and a {@code ChordConcretizer<HirajoshiNote>} won't
 * accept a {@code ScaleChord<GongNote>}.</p>
 *
 * <p>Shape is a rendering concern — it does not affect the pitch content.
 * Switching shape ({@link ChangeChordShape}) is a reversible transform
 * that doesn't touch the voices or duration, only changes how they're
 * realised in time.</p>
 */
public record ScaleChord<N extends ScaleNote>(
        List<N> voices,
        int durationMs,
        ChordShape shape
) implements AbstractNote {

    public ScaleChord {
        if (voices == null || voices.isEmpty()) {
            throw new IllegalArgumentException("a chord needs at least one voice");
        }
        if (durationMs <= 0) {
            throw new IllegalArgumentException("durationMs must be positive: " + durationMs);
        }
        if (shape == null) {
            throw new IllegalArgumentException("shape must not be null");
        }
        voices = List.copyOf(voices);
    }

    public int voiceCount() {
        return voices.size();
    }

    /** Short factory — block chord with the given voices. */
    @SafeVarargs
    public static <N extends ScaleNote> ScaleChord<N> block(int durationMs, N... voices) {
        return new ScaleChord<>(List.of(voices), durationMs, ChordShape.BLOCK);
    }
}
