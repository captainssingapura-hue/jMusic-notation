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
 */
public record Track(String name, Instrument defaultInstrument, List<Phrase> phrases) {
    public Track {
        phrases = List.copyOf(phrases);
    }
}
