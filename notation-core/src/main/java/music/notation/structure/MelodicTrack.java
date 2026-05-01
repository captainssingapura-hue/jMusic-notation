package music.notation.structure;

import music.notation.event.Instrument;
import music.notation.phrase.Bar;
import music.notation.phrase.Phrase;

import java.util.List;
import java.util.Map;

/**
 * A named melodic track holding a {@link Phrase} tree.
 *
 * <p>Auxiliary parallel voices live on the {@link Phrase} ADT — see
 * {@link Phrase#auxBars()}. The track simply delegates: aux is part of
 * the structural value object, so joining a phrase tree composes aux
 * the same way it composes primary bars.</p>
 */
public record MelodicTrack(
        String name,
        Instrument defaultInstrument,
        Phrase phrase
) implements Track {

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
    }

    /** Resolved bar list. Lazy: {@code phrase.bars()} runs on each call. */
    public List<Bar> bars() {
        return phrase.bars();
    }

    /** Dense aux bars per voice name, delegated to the phrase. */
    public Map<String, List<Bar>> auxBars() {
        return phrase.auxBars();
    }

    // ── Backwards-compat factories ──────────────────────────────────

    /** Wrap a bar list as an anonymous {@link Phrase} on the track. */
    public static MelodicTrack of(String name, Instrument instrument, List<Bar> bars) {
        return new MelodicTrack(name, instrument, Phrase.of(bars));
    }

    /** Wrap bar varargs as an anonymous {@link Phrase} on the track. */
    public static MelodicTrack of(String name, Instrument instrument, Bar... bars) {
        return new MelodicTrack(name, instrument, Phrase.of(bars));
    }
}
