package music.notation.structure;

import music.notation.phrase.Bar;

import java.util.List;

/**
 * A named track in a {@link Piece} — sealed parent of the kinded
 * track variants {@link MelodicTrack} and {@link DrumTrack}.
 *
 * <p>The canonical content accessor is {@link #bars()} — every track
 * resolves to a flat {@link Bar} list. Implementations may compute it
 * lazily from a {@link music.notation.phrase.BarPhrase} tree (preserving
 * authored elision boundaries) but must return an immutable list.</p>
 *
 * <p>Phase 4d cutover: replaces the previous {@code Track} record
 * carrying {@link music.notation.phrase.Phrase} sequences. Elision
 * resolution now lives inside the BarPhrase tree
 * ({@link music.notation.phrase.JoinedPhrase}); consumers read
 * {@code bars()} as a uniform value object and no longer pattern-match
 * on legacy phrase types.</p>
 */
public sealed interface Track permits MelodicTrack, DrumTrack {

    /** Track name (non-blank, unique per piece). */
    String name();

    /** Resolved bar list. May be lazy; treat as immutable. */
    List<Bar> bars();

    /**
     * Auxiliary parallel voices on the same track timeline. Each aux
     * track is itself a {@code Track} — covariantly typed by subclass.
     */
    List<? extends Track> auxTracks();
}
