package music.notation.structure;

import music.notation.phrase.Bar;

import java.util.List;

/**
 * A named track in a {@link Piece} — sealed parent of the kinded
 * track variants {@link MelodicTrack} and {@link DrumTrack}.
 *
 * <p>The canonical content accessor is {@link #bars()} — every track
 * resolves to a flat {@link Bar} list. Implementations may compute it
 * lazily from a {@link music.notation.phrase.Phrase} tree (preserving
 * authored elision boundaries) but must return an immutable list.</p>
 *
 * <p>Auxiliary parallel voices are a {@link MelodicTrack}-only feature
 * (see {@link MelodicTrack#auxBars()}); they share the parent track's
 * MIDI channel, instrument, and volume. Anything that needs an
 * independent fader belongs in its own {@code Track}.</p>
 */
public sealed interface Track permits MelodicTrack, DrumTrack {

    /** Track name (non-blank, unique per piece). */
    String name();

    /** Resolved bar list. May be lazy; treat as immutable. */
    List<Bar> bars();
}
