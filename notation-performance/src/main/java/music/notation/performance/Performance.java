package music.notation.performance;

import music.notation.expressivity.*;

import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * The codec input/output: composer-authored {@link Score} (pure content) paired
 * with the side-channels (tempo, instruments, volume, articulations, pedaling,
 * velocities) supplied at performance time.
 *
 * <p>Round-trip parity contract:
 * {@code MidiCodec.fromMidi(MidiCodec.toMidi(p)).equals(p)} holds for any
 * valid {@code p} whose {@link Articulations} is empty, whose
 * {@link Volume} is empty, whose {@link Velocities} is empty, and whose
 * {@link PitchedNote#tiedToNext()} flags are all false. (Articulations
 * are dropped; volume CC #7 events are written but dropped on read;
 * velocities are written but reconstructed densely on read — same
 * audible result, different shape; tied chains coalesce on write and
 * the tie flag itself isn't recoverable.)</p>
 *
 * <p>Backwards-compat constructors exist for callers from before
 * volume / pedaling / velocities were added. Each defaults the
 * not-yet-supplied channel to empty.</p>
 */
public record Performance(
        Score score,
        TempoTrack tempo,
        Instrumentation instruments,
        Volume volume,
        Articulations articulations,
        Pedaling pedaling,
        Velocities velocities) {
    public Performance {
        Objects.requireNonNull(score, "score");
        Objects.requireNonNull(tempo, "tempo");
        Objects.requireNonNull(instruments, "instruments");
        Objects.requireNonNull(volume, "volume");
        Objects.requireNonNull(articulations, "articulations");
        Objects.requireNonNull(pedaling, "pedaling");
        Objects.requireNonNull(velocities, "velocities");

        Set<TrackId> scoreIds = score.trackIds();
        validateKeys("instruments",   scoreIds, instruments.byTrack().keySet());
        validateKeys("volume",        scoreIds, volume.byTrack().keySet());
        validateKeys("articulations", scoreIds, articulations.byTrack().keySet());
        validateKeys("pedaling",      scoreIds, pedaling.byTrack().keySet());
        validateKeys("velocities",    scoreIds, velocities.byTrack().keySet());
    }

    /** Backwards-compat: defaults {@link Velocities} to empty. */
    public Performance(Score score, TempoTrack tempo,
                       Instrumentation instruments, Volume volume,
                       Articulations articulations, Pedaling pedaling) {
        this(score, tempo, instruments, volume, articulations, pedaling, Velocities.empty());
    }

    /** Backwards-compat: defaults {@link Pedaling} and {@link Velocities} to empty. */
    public Performance(Score score, TempoTrack tempo,
                       Instrumentation instruments, Volume volume,
                       Articulations articulations) {
        this(score, tempo, instruments, volume, articulations,
                Pedaling.empty(), Velocities.empty());
    }

    /**
     * Backwards-compat constructor: defaults {@link Volume},
     * {@link Pedaling}, and {@link Velocities} to empty. Existing
     * callers (PieceConcretizer, tests, JSON deserialization for older
     * payloads) keep working unchanged.
     */
    public Performance(Score score, TempoTrack tempo,
                       Instrumentation instruments, Articulations articulations) {
        this(score, tempo, instruments, Volume.empty(), articulations,
                Pedaling.empty(), Velocities.empty());
    }

    private static void validateKeys(String label, Set<TrackId> scoreIds,
                                      Set<TrackId> sideChannelIds) {
        Set<TrackId> offending = new TreeSet<>(java.util.Comparator.comparing(TrackId::name));
        for (TrackId id : sideChannelIds) {
            if (!scoreIds.contains(id)) offending.add(id);
        }
        if (!offending.isEmpty()) {
            throw new IllegalArgumentException(
                    label + " references tracks not in score: "
                            + offending.stream().map(TrackId::name).collect(Collectors.toList()));
        }
    }

    public static Performance of(Score score) {
        return new Performance(score, TempoTrack.empty(),
                Instrumentation.empty(), Volume.empty(), Articulations.empty(),
                Pedaling.empty(), Velocities.empty());
    }

    /**
     * Functional copy with a different {@link Pedaling}. Used by
     * {@link AutoPedaling#augment(Performance, music.notation.structure.TimeSignature)}
     * and any other transform that produces a new pedaling timeline.
     * All other fields are reused by reference (records are immutable).
     */
    public Performance withPedaling(Pedaling newPedaling) {
        Objects.requireNonNull(newPedaling, "newPedaling");
        return new Performance(score, tempo, instruments, volume,
                articulations, newPedaling, velocities);
    }

    /**
     * Functional copy with a different {@link Score}. Used by
     * pre-codec transforms that re-shape note tracks (humanizer
     * jitter, future quantizers). The side-channels carry over —
     * but note: the new {@code Score}'s {@link TrackId}s must agree
     * with every side-channel's keys, or the canonical constructor
     * will reject the result.
     */
    public Performance withScore(Score newScore) {
        Objects.requireNonNull(newScore, "newScore");
        return new Performance(newScore, tempo, instruments, volume,
                articulations, pedaling, velocities);
    }
}
