package music.notation.performance;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Per-track volume timelines indexed by {@link TrackId}. A track present
 * in a {@link Score} but absent from this map renders with the synth's
 * default volume — the explicit way to say "I don't care about mixing
 * this track."
 *
 * <p>Wired to MIDI CC #7 (Channel Volume) by {@link MidiCodec#toMidi}:
 * each {@link VolumeChange} on a track becomes a CC #7 event on that
 * track's MIDI channel at the corresponding tick. Per the import
 * doctrine, CC events are dropped on read — so volume <em>writes</em>
 * are honoured but volume does not survive a {@code toMidi → fromMidi}
 * round-trip.</p>
 */
public record Volume(Map<TrackId, VolumeControl> byTrack) {
    public Volume {
        Objects.requireNonNull(byTrack, "byTrack");
        Map<TrackId, VolumeControl> filtered = new LinkedHashMap<>();
        for (Map.Entry<TrackId, VolumeControl> e : byTrack.entrySet()) {
            if (!e.getValue().changes().isEmpty()) {
                filtered.put(e.getKey(), e.getValue());
            }
        }
        byTrack = Map.copyOf(filtered);
    }

    public static Volume empty() { return new Volume(Map.of()); }

    public static Volume single(TrackId track, int level) {
        return new Volume(Map.of(track, VolumeControl.constant(level)));
    }
}
