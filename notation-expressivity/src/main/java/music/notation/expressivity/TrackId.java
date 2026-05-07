package music.notation.expressivity;

import java.util.Objects;

/**
 * Typed identity for a track. The string expresses a track's musical role
 * ("alto", "lead", "drums") and is the serialisation form (MIDI Track Name meta event).
 * The record wrapper keeps the API surface free of raw strings in memory.
 */
public record TrackId(String name) {
    public TrackId {
        Objects.requireNonNull(name);
        if (name.isBlank()) throw new IllegalArgumentException("TrackId name must be non-blank");
    }
}
