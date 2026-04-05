package music.notation.phrase;

import music.notation.duration.Duration;
import music.notation.event.Articulation;
import music.notation.event.Ornament;
import music.notation.pitch.Pitch;

import java.util.List;
import java.util.Optional;

/**
 * A note event carrying one or more pitches (poly), a duration,
 * optional articulations, and an optional ornament.
 *
 * <p>Single-pitch notes are the common case — use {@link #of(Pitch, Duration)}.
 * For poly (chord-within-melody), use {@link #poly(Duration, Pitch...)}.</p>
 */
public record NoteNode(List<Pitch> pitches, Duration duration, List<Articulation> articulations, Optional<Ornament> ornament) implements PhraseNode {
    public NoteNode {
        if (pitches.isEmpty()) {
            throw new IllegalArgumentException("NoteNode must have at least one pitch");
        }
        pitches = List.copyOf(pitches);
        articulations = List.copyOf(articulations);
    }

    /** Convenience: the first (or only) pitch. */
    public Pitch pitch() { return pitches.get(0); }

    /** True when this node carries more than one pitch. */
    public boolean isPolyphonic() { return pitches.size() > 1; }

    // ── Single-pitch factories (backward compatible) ──

    public static NoteNode of(Pitch pitch, Duration duration) {
        return new NoteNode(List.of(pitch), duration, List.of(), Optional.empty());
    }

    public static NoteNode ornamented(Pitch pitch, Duration duration, Ornament ornament) {
        return new NoteNode(List.of(pitch), duration, List.of(), Optional.of(ornament));
    }

    // ── Poly-pitch factories ──

    public static NoteNode poly(Duration duration, Pitch... pitches) {
        return new NoteNode(List.of(pitches), duration, List.of(), Optional.empty());
    }

    public static NoteNode poly(Duration duration, List<Pitch> pitches) {
        return new NoteNode(pitches, duration, List.of(), Optional.empty());
    }
}
