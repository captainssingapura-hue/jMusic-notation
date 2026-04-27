package music.notation.structure;

import music.notation.event.Instrument;
import music.notation.phrase.Bar;

import java.util.List;

/**
 * A named melodic track holding bars directly — the planned replacement
 * for the {@link Track} + {@link music.notation.phrase.MelodicPhrase}
 * pairing.
 *
 * <p><b>Status: Phase 4a sketch.</b> Not yet wired into the playback or
 * build pipeline; existing songs continue to use {@link Track}. The
 * sealed parent type that will tie this to {@link DrumTrack} will appear
 * in Phase 4d once the flat {@link Track} record is removed and the
 * {@code Track} name becomes available for the sealed family.</p>
 *
 * <p>Compared to today's {@code Track}:</p>
 * <ul>
 *   <li>{@code bars} replaces {@code phrases} — bars sit directly on
 *       the track, with no {@code MelodicPhrase} wrapper.</li>
 *   <li>{@code auxTracks} is typed as {@code List<MelodicTrack>} —
 *       auxiliary voices on a melodic track are themselves melodic.</li>
 *   <li>Phrase-boundary markings (BREATH / CAESURA / ATTACCA / ELISION)
 *       are not represented yet; they need a new home and that design
 *       call lands later in Phase 4.</li>
 * </ul>
 *
 * <p>Drum content lives in the sibling {@link DrumTrack} type.</p>
 */
public record MelodicTrack(
        String name,
        Instrument defaultInstrument,
        List<Bar> bars,
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
        bars = List.copyOf(bars);
        auxTracks = List.copyOf(auxTracks);
    }

    /** Convenience: a melodic track with no aux tracks. */
    public static MelodicTrack of(String name, Instrument instrument, List<Bar> bars) {
        return new MelodicTrack(name, instrument, bars, List.of());
    }

    /** Convenience: a melodic track with bars passed varargs-style. */
    public static MelodicTrack of(String name, Instrument instrument, Bar... bars) {
        return new MelodicTrack(name, instrument, List.of(bars), List.of());
    }
}
