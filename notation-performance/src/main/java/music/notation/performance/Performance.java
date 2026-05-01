package music.notation.performance;

import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * The codec input/output: composer-authored {@link Score} (pure content) paired
 * with the side-channels (tempo, instruments, volume, articulations) supplied
 * at performance time.
 *
 * <p>Round-trip parity contract:
 * {@code MidiCodec.fromMidi(MidiCodec.toMidi(p)).equals(p)} holds for any
 * valid {@code p} whose {@link Articulations} is empty, whose
 * {@link Volume} is empty, and whose {@link PitchedNote#tiedToNext()}
 * flags are all false. (Articulations are dropped; volume CC #7 events
 * are written but dropped on read; tied chains coalesce on write and
 * the tie flag itself isn't recoverable.)</p>
 *
 * <p>A four-arg backwards-compat constructor exists for callers from
 * before the volume side-channel was added — it defaults volume to
 * empty.</p>
 */
public record Performance(
        Score score,
        TempoTrack tempo,
        Instrumentation instruments,
        Volume volume,
        Articulations articulations) {
    public Performance {
        Objects.requireNonNull(score, "score");
        Objects.requireNonNull(tempo, "tempo");
        Objects.requireNonNull(instruments, "instruments");
        Objects.requireNonNull(volume, "volume");
        Objects.requireNonNull(articulations, "articulations");

        Set<TrackId> scoreIds = score.trackIds();
        validateKeys("instruments", scoreIds, instruments.byTrack().keySet());
        validateKeys("volume",      scoreIds, volume.byTrack().keySet());
        validateKeys("articulations", scoreIds, articulations.byTrack().keySet());
    }

    /**
     * Backwards-compat constructor: defaults {@link Volume} to empty.
     * Existing callers (PieceConcretizer, tests, JSON deserialization
     * for older payloads) keep working unchanged.
     */
    public Performance(Score score, TempoTrack tempo,
                       Instrumentation instruments, Articulations articulations) {
        this(score, tempo, instruments, Volume.empty(), articulations);
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
                Instrumentation.empty(), Volume.empty(), Articulations.empty());
    }
}
