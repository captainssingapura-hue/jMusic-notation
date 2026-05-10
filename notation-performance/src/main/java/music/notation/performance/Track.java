package music.notation.performance;

import music.notation.expressivity.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * The outer container of notes: identity, kind, and a sequence of notes. A ValueObject;
 * notes do not carry a track reference because they are owned by the Track that contains
 * them. Empty notes list is legal but typically useless.
 *
 * <p>The {@code auto} flag distinguishes synthetic tracks produced by the
 * auto-X generators (auto-drum, future auto-bass / auto-harmony) from
 * source tracks parsed out of the user's score. Phase 1 of the multi-synth
 * fan-out only stores the bit; Phase 2's channel allocator will read it
 * to route auto-X tracks to the dedicated <em>AUTO</em> synth slot,
 * letting source drums and auto-drum coexist on different channel-9
 * assignments. See {@code .docs/playback/multi-synth/Q_count.md}.</p>
 */
public record Track(TrackId id, TrackKind kind, List<ConcreteNote> notes, boolean auto)
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

    /**
     * Backwards-compat constructor: defaults {@code auto} to {@code false}.
     * Source-parsed tracks (i.e. anything not produced by an auto-X
     * generator) use this form; auto-X generators use the 4-arg
     * canonical constructor with {@code auto = true}.
     */
    public Track(TrackId id, TrackKind kind, List<ConcreteNote> notes) {
        this(id, kind, notes, false);
    }

    private static final Comparator<ConcreteNote> CANONICAL =
            Comparator.<ConcreteNote>comparingLong(ConcreteNote::tickMs)
                    .thenComparingInt(n -> n instanceof PitchedNote ? 0 : 1)
                    .thenComparingInt(n -> switch (n) {
                        case PitchedNote p -> p.midi();
                        case DrumNote d -> d.piece();
                    });

    public static Track empty(TrackId id, TrackKind kind) {
        return new Track(id, kind, List.of(), false);
    }

    /** Functional copy with a flipped {@code auto} bit. */
    public Track withAuto(boolean autoFlag) {
        return new Track(id, kind, notes, autoFlag);
    }
}
