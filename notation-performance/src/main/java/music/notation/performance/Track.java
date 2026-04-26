package music.notation.performance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * The outer container of notes: identity, kind, and a sequence of notes. A ValueObject;
 * notes do not carry a track reference because they are owned by the Track that contains
 * them. Empty notes list is legal but typically useless.
 */
public record Track(TrackId id, TrackKind kind, List<ConcreteNote> notes)
        implements music.notation.core.model.ConcreteNote {
    public Track {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(notes, "notes");
        for (ConcreteNote n : notes) {
            if (kind == TrackKind.PITCHED && !(n instanceof PitchedNote)) {
                throw new IllegalArgumentException(
                        "PITCHED track " + id.name() + " contains non-pitched note: " + n);
            }
            if (kind == TrackKind.DRUM && !(n instanceof DrumNote)) {
                throw new IllegalArgumentException(
                        "DRUM track " + id.name() + " contains non-drum note: " + n);
            }
        }
        List<ConcreteNote> sorted = new ArrayList<>(notes);
        sorted.sort(CANONICAL);
        notes = List.copyOf(sorted);
    }

    private static final Comparator<ConcreteNote> CANONICAL =
            Comparator.<ConcreteNote>comparingLong(ConcreteNote::tickMs)
                    .thenComparingInt(n -> n instanceof PitchedNote ? 0 : 1)
                    .thenComparingInt(n -> switch (n) {
                        case PitchedNote p -> p.midi();
                        case DrumNote d -> d.piece();
                    });

    public static Track empty(TrackId id, TrackKind kind) {
        return new Track(id, kind, List.of());
    }
}
