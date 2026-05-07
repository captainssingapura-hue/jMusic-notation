package music.notation.mxl;

import com.fasterxml.jackson.annotation.JsonIgnore;
import music.notation.expressivity.TrackId;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-track transpose offset captured from MusicXML's {@code <transpose>}
 * element. {@link music.notation.performance.PitchedNote} stores
 * <em>sounding</em> pitch (so playback works with no further translation),
 * but the original written-vs-sounding offset must be preserved for
 * faithful score-reconstruction. This sidecar holds that offset per track.
 *
 * <p>Empty map = no transposing parts. Tracks not in the map are
 * concert-pitch (offset zero).</p>
 */
public record Transpositions(Map<TrackId, Transpose> byTrack) {

    public Transpositions {
        // Accept any iteration order; canonicalise to LinkedHashMap to preserve insertion order.
        Map<TrackId, Transpose> filtered = new LinkedHashMap<>();
        for (var e : byTrack.entrySet()) {
            if (e.getValue() != null && !e.getValue().isIdentity()) {
                filtered.put(e.getKey(), e.getValue());
            }
        }
        byTrack = Map.copyOf(filtered);
    }

    public static Transpositions empty() { return new Transpositions(Map.of()); }

    @JsonIgnore
    public boolean isEmpty() { return byTrack.isEmpty(); }
}
