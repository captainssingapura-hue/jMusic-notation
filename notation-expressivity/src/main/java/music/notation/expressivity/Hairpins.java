package music.notation.expressivity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Per-track hairpin timelines indexed by {@link TrackId}. A track
 * present in a {@code Score} but absent from this map plays flat
 * between its discrete {@link VelocityChange}/{@link VolumeChange}
 * set-points (no cresc./decresc. shaping).
 *
 * <p>Hairpins are pure shape overlays — they carry no level
 * information. At codec/render time, each {@link HairpinSpan} reads
 * the bracketing discrete set-points on the same track to derive its
 * start and end levels. The discrete loudness timeline is the single
 * source of truth; hairpins fill the gaps between discrete marks.
 * Where they coexist at a position, the discrete mark wins.</p>
 *
 * <p>Same shape as {@code Velocities}, {@code Volume},
 * {@code Articulations}, {@code Pedaling} — see
 * {@link music.notation.expressivity package doc} for the
 * side-channel doctrine.</p>
 */
public record Hairpins(Map<TrackId, HairpinControl> byTrack) {
    public Hairpins {
        Objects.requireNonNull(byTrack, "byTrack");
        Map<TrackId, HairpinControl> filtered = new LinkedHashMap<>();
        for (Map.Entry<TrackId, HairpinControl> e : byTrack.entrySet()) {
            if (!e.getValue().spans().isEmpty()) {
                filtered.put(e.getKey(), e.getValue());
            }
        }
        byTrack = Map.copyOf(filtered);
    }

    public static Hairpins empty() { return new Hairpins(Map.of()); }

    public static Hairpins single(TrackId track, HairpinControl control) {
        return new Hairpins(Map.of(track, control));
    }
}
