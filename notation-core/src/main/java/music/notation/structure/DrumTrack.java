package music.notation.structure;

import music.notation.phrase.Bar;
import music.notation.phrase.Phrase;

import java.util.List;

/**
 * A named percussion track holding a {@link Phrase} tree. Like
 * {@link MelodicTrack}, the underlying storage is a phrase tree
 * preserving authored elision boundaries; {@link #bars()} is a lazy
 * accessor that resolves the tree on each call.
 *
 * <p>The kit (e.g. Standard, Jazz, Brush, Power) is implicit at MIDI
 * render time — drum tracks always route to MIDI channel 9 where the
 * GM percussion map applies.</p>
 */
public record DrumTrack(
        String name,
        Phrase phrase,
        List<DrumTrack> auxTracks
) implements Track {
    public DrumTrack {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("DrumTrack name must be non-blank");
        }
        if (phrase == null) {
            throw new IllegalArgumentException("DrumTrack phrase must not be null");
        }
        auxTracks = List.copyOf(auxTracks);
    }

    /** Resolved bar list. Lazy: {@code phrase.bars()} runs on each call. */
    public List<Bar> bars() {
        return phrase.bars();
    }

    // ── Backwards-compat factories ──────────────────────────────────

    /** Wrap a bar list as an anonymous {@link Phrase} on the track. */
    public static DrumTrack of(String name, List<Bar> bars) {
        return new DrumTrack(name, Phrase.of(bars), List.of());
    }

    /** Wrap bar varargs as an anonymous {@link Phrase} on the track. */
    public static DrumTrack of(String name, Bar... bars) {
        return new DrumTrack(name, Phrase.of(bars), List.of());
    }
}
