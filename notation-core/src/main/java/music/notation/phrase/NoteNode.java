package music.notation.phrase;

import music.notation.duration.Duration;
import music.notation.event.Articulation;
import music.notation.event.Ornament;
import music.notation.pitch.Pitch;

import java.util.List;
import java.util.Optional;

/**
 * A note event carrying one or more pitches (poly), a duration,
 * optional articulations, an optional ornament, and optional grace notes.
 *
 * <p>Single-pitch notes are the common case — use {@link #of(Pitch, Duration)}.
 * For poly (chord-within-melody), use {@link #poly(Duration, Pitch...)}.</p>
 *
 * <p>Grace notes precede the main note and steal time from its duration
 * during playback. They are embedded here rather than as separate
 * {@code PhraseNode}s so the total bar duration is preserved.</p>
 *
 * <p>When {@code equalDivision} is {@code true}, the graces and main note
 * each take {@code duration / (graceCount + 1)} — i.e. a tuplet
 * (2 graces + main = triplet, 3 graces + main = quadruplet, etc.).
 * When {@code false} (default), each grace plays for a fixed short
 * duration and the main note keeps the remainder.</p>
 */
public record NoteNode(List<Pitch> pitches, Duration duration, List<Articulation> articulations,
                       Optional<Ornament> ornament, List<GraceNote> graceNotes,
                       boolean equalDivision) implements PhraseNode {
    public NoteNode {
        if (pitches.isEmpty()) {
            throw new IllegalArgumentException("NoteNode must have at least one pitch");
        }
        pitches = List.copyOf(pitches);
        articulations = List.copyOf(articulations);
        graceNotes = List.copyOf(graceNotes);
    }

    /** Convenience: the first (or only) pitch. */
    public Pitch pitch() { return pitches.get(0); }

    /** True when this node carries more than one pitch. */
    public boolean isPolyphonic() { return pitches.size() > 1; }

    // ── Single-pitch factories (backward compatible) ──

    public static NoteNode of(Pitch pitch, Duration duration) {
        return new NoteNode(List.of(pitch), duration, List.of(), Optional.empty(), List.of(), false);
    }

    public static NoteNode ornamented(Pitch pitch, Duration duration, Ornament ornament) {
        return new NoteNode(List.of(pitch), duration, List.of(), Optional.of(ornament), List.of(), false);
    }

    // ── Poly-pitch factories ──

    public static NoteNode poly(Duration duration, Pitch... pitches) {
        return new NoteNode(List.of(pitches), duration, List.of(), Optional.empty(), List.of(), false);
    }

    public static NoteNode poly(Duration duration, List<Pitch> pitches) {
        return new NoteNode(pitches, duration, List.of(), Optional.empty(), List.of(), false);
    }

    // ── Grace-note factories ──

    /** Create a note preceded by grace notes that play briefly and steal time from its duration. */
    public static NoteNode graced(List<GraceNote> graces, Duration duration, List<Pitch> pitches) {
        return new NoteNode(pitches, duration, List.of(), Optional.empty(), graces, false);
    }

    /**
     * Create a tuplet: graces and main note each take an equal share of the duration.
     * For example, 2 graces + main note with QUARTER = 3 notes of 1/3 quarter each (triplet).
     */
    public static NoteNode tuplet(List<GraceNote> graces, Duration duration, List<Pitch> pitches) {
        return new NoteNode(pitches, duration, List.of(), Optional.empty(), graces, true);
    }
}
