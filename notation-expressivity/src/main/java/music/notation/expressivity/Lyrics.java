package music.notation.expressivity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Per-track lyric timelines indexed by {@link TrackId}. A track present
 * in a {@link music.notation.performance.Score Score} but absent from
 * this map renders with no lyrics — the explicit way to say "this track
 * has no sung text."
 *
 * <p>Wired to MIDI <code>0x05</code> (Lyric) meta events by the codec in
 * a later phase: each {@link LyricEvent} becomes a meta event at the
 * corresponding tick on the track's MIDI channel. Per the import
 * doctrine, the read path strips meta events — lyrics, like pedaling,
 * are a write-only side-channel over the codec boundary unless an
 * explicit lyric-meta importer reconstructs them.</p>
 *
 * <p>Empty lines are filtered at construction time — a track keyed to an
 * empty {@link LyricLine} is dropped so equality is canonical
 * regardless of how callers built the map.</p>
 */
public record Lyrics(Map<TrackId, LyricLine> byTrack) {
    public Lyrics {
        Objects.requireNonNull(byTrack, "byTrack");
        Map<TrackId, LyricLine> filtered = new LinkedHashMap<>();
        for (Map.Entry<TrackId, LyricLine> e : byTrack.entrySet()) {
            if (!e.getValue().events().isEmpty()) {
                filtered.put(e.getKey(), e.getValue());
            }
        }
        byTrack = Map.copyOf(filtered);
    }

    public static Lyrics empty() { return new Lyrics(Map.of()); }

    public static Lyrics single(TrackId track, LyricLine line) {
        return new Lyrics(Map.of(track, line));
    }
}
