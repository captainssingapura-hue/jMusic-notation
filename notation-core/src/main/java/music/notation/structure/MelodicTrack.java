package music.notation.structure;

import music.notation.event.Instrument;
import music.notation.phrase.Bar;
import music.notation.phrase.BarPhrase;

import java.util.List;

/**
 * A named melodic track holding a {@link BarPhrase} tree — the
 * structural value object that materialises into a flat
 * {@link List Bar} list on demand via {@link #bars()}.
 *
 * <p>Phase 4d.3a: the underlying storage is now a {@link BarPhrase}
 * tree (LeafPhrase + JoinedPhrase) preserving authored elision
 * boundaries. {@link #bars()} is lazy — each call invokes
 * {@code phrase.bars()} which resolves the tree freshly. Operation
 * is cheap; sparse callers are expected.</p>
 *
 * <p>Aux tracks remain a separate notion (parallel voices on the same
 * track timeline). Voice overlays were dropped in Phase 4c migration;
 * if needed they re-emerge as a "complex" bar shape later.</p>
 */
public record MelodicTrack(
        String name,
        Instrument defaultInstrument,
        BarPhrase phrase,
        List<MelodicTrack> auxTracks
) {
    public MelodicTrack {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("MelodicTrack name must be non-blank");
        }
        if (defaultInstrument == null) {
            throw new IllegalArgumentException("MelodicTrack defaultInstrument must not be null");
        }
        if (defaultInstrument == Instrument.DRUM_KIT) {
            throw new IllegalArgumentException(
                    "MelodicTrack '" + name + "' cannot use DRUM_KIT — use DrumTrack instead");
        }
        if (phrase == null) {
            throw new IllegalArgumentException("MelodicTrack phrase must not be null");
        }
        auxTracks = List.copyOf(auxTracks);
    }

    /** Resolved bar list. Lazy: {@code phrase.bars()} runs on each call. */
    public List<Bar> bars() {
        return phrase.bars();
    }

    // ── Backwards-compat factories ──────────────────────────────────

    /** Wrap a bar list as an anonymous {@link BarPhrase} on the track. */
    public static MelodicTrack of(String name, Instrument instrument, List<Bar> bars) {
        return new MelodicTrack(name, instrument, BarPhrase.of(bars), List.of());
    }

    /** Wrap bar varargs as an anonymous {@link BarPhrase} on the track. */
    public static MelodicTrack of(String name, Instrument instrument, Bar... bars) {
        return new MelodicTrack(name, instrument, BarPhrase.of(bars), List.of());
    }
}
