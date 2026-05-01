package music.notation.performance;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Per-track articulation intent indexed by {@link TrackId}. The codec does not
 * currently render or recover articulation; round-trip is lossless iff this is empty.
 */
public record Articulations(Map<TrackId, ArticulationControl> byTrack) {
    public Articulations {
        Objects.requireNonNull(byTrack, "byTrack");
        Map<TrackId, ArticulationControl> filtered = new LinkedHashMap<>();
        for (Map.Entry<TrackId, ArticulationControl> e : byTrack.entrySet()) {
            if (!e.getValue().changes().isEmpty()) {
                filtered.put(e.getKey(), e.getValue());
            }
        }
        byTrack = Map.copyOf(filtered);
    }

    public static Articulations empty() { return new Articulations(Map.of()); }
}
