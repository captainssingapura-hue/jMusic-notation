package music.notation.phrase;

import music.notation.duration.Duration;
import music.notation.event.Ornament;
import music.notation.pitch.Pitch;

import java.util.List;
import java.util.Optional;

/**
 * A note event carrying one or more pitches, a duration, optional ornament
 * (single-pitch only), optional grace notes, and a tie marker.
 *
 * <p>Sealed split:</p>
 * <ul>
 *   <li>{@link SimplePitchNode} — exactly one pitch; may carry an ornament.</li>
 *   <li>{@link PolyPitchNode}   — two or more pitches (chord); no ornament slot.</li>
 * </ul>
 *
 * <p>Implements {@link Tieable}: {@link #tiedToNext()} is intrinsic and
 * survives concretization.</p>
 *
 * <p>Static factories preserve the legacy {@code NoteNode} surface so callers
 * just rename the type:</p>
 * <pre>{@code
 *   PitchNode.of(C4, QUARTER)
 *   PitchNode.poly(QUARTER, C4, E4, G4)
 *   PitchNode.ornamented(C4, QUARTER, TRILL)
 *   PitchNode.graced(graces, QUARTER, List.of(C4))
 *   PitchNode.tuplet(graces, QUARTER, List.of(C4))
 * }</pre>
 */
public sealed interface PitchNode extends PhraseNode, Tieable
        permits SimplePitchNode, PolyPitchNode {

    Duration duration();
    List<Pitch> pitches();
    List<GraceNote> graceNotes();
    boolean equalDivision();
    @Override boolean tiedToNext();

    /** Convenience: the first (or only) pitch. */
    default Pitch pitch() { return pitches().get(0); }

    /** True when this node carries more than one pitch. */
    default boolean isPolyphonic() { return pitches().size() > 1; }

    /** True when this note is tied into the next same-pitch note. */
    default boolean hasTie() { return tiedToNext(); }

    @Override PitchNode withTiedToNext();

    // ── Static factories (mirror the legacy NoteNode API) ──

    static SimplePitchNode of(Pitch pitch, Duration duration) {
        return new SimplePitchNode(pitch, duration, Optional.empty(), List.of(), false, false);
    }

    static SimplePitchNode ornamented(Pitch pitch, Duration duration, Ornament ornament) {
        return new SimplePitchNode(pitch, duration, Optional.of(ornament), List.of(), false, false);
    }

    /** Single-pitch goes to {@link SimplePitchNode}; multi-pitch to {@link PolyPitchNode}. */
    static PitchNode poly(Duration duration, Pitch... pitches) {
        if (pitches.length == 1) {
            return of(pitches[0], duration);
        }
        return new PolyPitchNode(List.of(pitches), duration, List.of(), false, false);
    }

    /** Single-pitch goes to {@link SimplePitchNode}; multi-pitch to {@link PolyPitchNode}. */
    static PitchNode poly(Duration duration, List<Pitch> pitches) {
        if (pitches.size() == 1) {
            return of(pitches.get(0), duration);
        }
        return new PolyPitchNode(pitches, duration, List.of(), false, false);
    }

    static PitchNode graced(List<GraceNote> graces, Duration duration, List<Pitch> pitches) {
        if (pitches.size() == 1) {
            return new SimplePitchNode(pitches.get(0), duration, Optional.empty(), graces, false, false);
        }
        return new PolyPitchNode(pitches, duration, graces, false, false);
    }

    static PitchNode tuplet(List<GraceNote> graces, Duration duration, List<Pitch> pitches) {
        if (pitches.size() == 1) {
            return new SimplePitchNode(pitches.get(0), duration, Optional.empty(), graces, true, false);
        }
        return new PolyPitchNode(pitches, duration, graces, true, false);
    }
}
