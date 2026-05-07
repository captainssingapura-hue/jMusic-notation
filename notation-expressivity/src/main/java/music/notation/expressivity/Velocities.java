package music.notation.expressivity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Per-track velocity timelines indexed by {@link TrackId}. A track
 * present in a {@code Score} but absent from this map renders with the
 * codec-default velocity ({@value VelocityControl#DEFAULT_VELOCITY}) —
 * the explicit way to say "I don't care about per-note dynamics on this
 * track."
 *
 * <p>Wired to the {@code NOTE_ON} velocity byte by
 * {@code MidiCodec.toMidi}: each note's velocity is looked up on its
 * track's {@link VelocityControl} via step-function semantics.
 * Round-trip through {@code toMidi → fromMidi} preserves the audible
 * result but not the sparse shape — a {@link Velocities} that was
 * empty (uniform default) round-trips to one with explicit per-note
 * entries that all read back as the default.</p>
 *
 * <p>Same shape as {@code Volume}, {@code Articulations}, {@code Pedaling}
 * — see {@link music.notation.expressivity package doc} for the
 * side-channel doctrine.</p>
 */
public record Velocities(Map<TrackId, VelocityControl> byTrack) {
    public Velocities {
        Objects.requireNonNull(byTrack, "byTrack");
        Map<TrackId, VelocityControl> filtered = new LinkedHashMap<>();
        for (Map.Entry<TrackId, VelocityControl> e : byTrack.entrySet()) {
            if (!e.getValue().changes().isEmpty()) {
                filtered.put(e.getKey(), e.getValue());
            }
        }
        byTrack = Map.copyOf(filtered);
    }

    public static Velocities empty() { return new Velocities(Map.of()); }

    public static Velocities single(TrackId track, VelocityControl control) {
        return new Velocities(Map.of(track, control));
    }
}
