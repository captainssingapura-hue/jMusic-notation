package music.notation.expressivity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Per-track sustain-pedal timelines indexed by {@link TrackId}. A track
 * present in a {@link Score} but absent from this map renders with the
 * synth's default (pedal up throughout) — the explicit way to say
 * "this track doesn't pedal."
 *
 * <p>Wired to MIDI CC #64 (Damper / Sustain Pedal) by
 * {@link MidiCodec#toMidi}: each {@link PedalChange} becomes a CC #64
 * event on the track's MIDI channel at the corresponding tick.
 * Per the import doctrine, CC events are dropped on read — pedal
 * <em>writes</em> are honoured but pedal does not survive a
 * {@code toMidi → fromMidi} round-trip.</p>
 */
public record Pedaling(Map<TrackId, PedalControl> byTrack) {
    public Pedaling {
        Objects.requireNonNull(byTrack, "byTrack");
        Map<TrackId, PedalControl> filtered = new LinkedHashMap<>();
        for (Map.Entry<TrackId, PedalControl> e : byTrack.entrySet()) {
            if (!e.getValue().changes().isEmpty()) {
                filtered.put(e.getKey(), e.getValue());
            }
        }
        byTrack = Map.copyOf(filtered);
    }

    public static Pedaling empty() { return new Pedaling(Map.of()); }

    public static Pedaling single(TrackId track, PedalControl control) {
        return new Pedaling(Map.of(track, control));
    }
}
