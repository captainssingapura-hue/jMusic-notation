package music.notation.performance;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A roster of tracks — the outer container of all musical content. Tracks within a
 * Score have unique {@link TrackId}s; at most one track is DRUM (MIDI 1.0 limitation).
 * The single drum track, if present, sinks to the end of the track list during
 * canonicalisation.
 */
public record Score(List<Track> tracks) implements music.notation.core.model.ConcreteNote {
    public Score {
        Objects.requireNonNull(tracks, "tracks");
        Set<TrackId> seen = new HashSet<>();
        int drumCount = 0;
        for (Track t : tracks) {
            if (!seen.add(t.id())) {
                throw new IllegalArgumentException("duplicate TrackId: " + t.id().name());
            }
            if (t.kind() == TrackKind.DRUM) drumCount++;
        }
        if (drumCount > 1) {
            throw new IllegalArgumentException("at most one DRUM track is allowed, got " + drumCount);
        }
        List<Track> ordered = new ArrayList<>(tracks.size());
        Track drum = null;
        for (Track t : tracks) {
            if (t.kind() == TrackKind.DRUM) drum = t;
            else ordered.add(t);
        }
        if (drum != null) ordered.add(drum);
        tracks = List.copyOf(ordered);
    }

    public Set<TrackId> trackIds() {
        Set<TrackId> ids = new LinkedHashSet<>();
        for (Track t : tracks) ids.add(t.id());
        return ids;
    }

    public static Score empty() { return new Score(List.of()); }

    public static Score of(Track... tracks) { return new Score(List.of(tracks)); }
}
