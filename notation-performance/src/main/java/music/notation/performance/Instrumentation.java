package music.notation.performance;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Per-track instrument timelines indexed by {@link TrackId}. A track present in a
 * {@link Score} but absent from this map renders with the synth default — that is the
 * explicit way to say "any timbre is fine."
 */
public record Instrumentation(Map<TrackId, InstrumentControl> byTrack) {
    public Instrumentation {
        Objects.requireNonNull(byTrack, "byTrack");
        Map<TrackId, InstrumentControl> filtered = new LinkedHashMap<>();
        for (Map.Entry<TrackId, InstrumentControl> e : byTrack.entrySet()) {
            if (!e.getValue().changes().isEmpty()) {
                filtered.put(e.getKey(), e.getValue());
            }
        }
        byTrack = Map.copyOf(filtered);
    }

    public static Instrumentation empty() { return new Instrumentation(Map.of()); }

    public static Instrumentation single(TrackId track, int program) {
        return new Instrumentation(Map.of(track, InstrumentControl.constant(program)));
    }
}
