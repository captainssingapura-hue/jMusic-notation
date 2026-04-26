package music.notation.performance;

import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * The codec input/output: composer-authored {@link Score} (pure content) paired with
 * the side-channels (tempo, instruments, articulations) supplied at performance time.
 * Round-trip parity contract: {@code MidiCodec.fromMidi(MidiCodec.toMidi(p)).equals(p)}
 * holds for any valid Performance whose articulations are empty (codec drops
 * articulation; everything else is lossless).
 */
public record Performance(
        Score score,
        TempoTrack tempo,
        Instrumentation instruments,
        Articulations articulations) {
    public Performance {
        Objects.requireNonNull(score, "score");
        Objects.requireNonNull(tempo, "tempo");
        Objects.requireNonNull(instruments, "instruments");
        Objects.requireNonNull(articulations, "articulations");

        Set<TrackId> scoreIds = score.trackIds();
        Set<TrackId> instrOffending = new TreeSet<>(java.util.Comparator.comparing(TrackId::name));
        for (TrackId id : instruments.byTrack().keySet()) {
            if (!scoreIds.contains(id)) instrOffending.add(id);
        }
        if (!instrOffending.isEmpty()) {
            throw new IllegalArgumentException(
                    "instruments references tracks not in score: "
                            + instrOffending.stream().map(TrackId::name).collect(Collectors.toList()));
        }
        Set<TrackId> artOffending = new TreeSet<>(java.util.Comparator.comparing(TrackId::name));
        for (TrackId id : articulations.byTrack().keySet()) {
            if (!scoreIds.contains(id)) artOffending.add(id);
        }
        if (!artOffending.isEmpty()) {
            throw new IllegalArgumentException(
                    "articulations references tracks not in score: "
                            + artOffending.stream().map(TrackId::name).collect(Collectors.toList()));
        }
    }

    public static Performance of(Score score) {
        return new Performance(score, TempoTrack.empty(), Instrumentation.empty(), Articulations.empty());
    }
}
