package music.notation.performance;

import music.notation.event.PercussionSound;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Reverse lookup from a MIDI drum-channel note number (0–127) to the
 * corresponding {@link PercussionSound} enum value.
 *
 * <p>By GM convention, drum tracks live on MIDI channel 9 and the
 * note number selects the percussion piece (e.g. note 36 = bass drum,
 * 38 = acoustic snare). This map exists because authored
 * {@code PercussionNote}s carry a {@link PercussionSound} enum, not
 * a raw note number — the reverse direction is needed when reading
 * MIDI back in.</p>
 *
 * <p>Stateless and thread-safe.</p>
 */
public final class PercussionMap {

    private PercussionMap() {}

    private static final Map<Integer, PercussionSound> BY_NOTE;
    static {
        var m = new HashMap<Integer, PercussionSound>();
        for (PercussionSound ps : PercussionSound.values()) {
            // First-wins so the order in the enum is canonical when
            // two sounds ever share a MIDI note (none do today).
            m.putIfAbsent(ps.midiNote(), ps);
        }
        BY_NOTE = Map.copyOf(m);
    }

    /**
     * Resolve a GM percussion note number to its {@link PercussionSound}.
     *
     * @param midiNote channel-9 note number (typically 27–87 in GM).
     * @return the matching enum, or empty if {@code midiNote} is not
     *         covered by this build's {@link PercussionSound} set.
     */
    public static Optional<PercussionSound> forNote(int midiNote) {
        return Optional.ofNullable(BY_NOTE.get(midiNote));
    }

    /** Whether {@code midiNote} maps to a known {@link PercussionSound}. */
    public static boolean contains(int midiNote) {
        return BY_NOTE.containsKey(midiNote);
    }
}
