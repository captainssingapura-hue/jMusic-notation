package music.notation.structure;

import music.notation.event.Instrument;
import music.notation.phrase.Phrase;

import java.util.List;

/**
 * A named track carrying musical phrases and a default instrument suggestion.
 *
 * <p>The {@code defaultInstrument} is a hint for cold-start playback.
 * At runtime the user may assign any number of instruments to a track —
 * that mapping lives outside the {@code Track} (e.g. in the player/UI).</p>
 *
 * <p>A track may contain {@code auxTracks} — auxiliary voices that play
 * simultaneously with the main content, sharing the parent's instrument
 * (or using their own). This mirrors the {@link music.notation.phrase.Bar} /
 * {@link music.notation.phrase.AuxBar} relationship at the track level.</p>
 */
public record Track(String name, Instrument defaultInstrument, List<Phrase> phrases, List<Track> auxTracks) {
    public Track {
        phrases = List.copyOf(phrases);
        auxTracks = List.copyOf(auxTracks);
    }

    /** Convenience factory — no aux tracks. */
    public static Track of(String name, Instrument defaultInstrument, List<Phrase> phrases) {
        return new Track(name, defaultInstrument, phrases, List.of());
    }
}
