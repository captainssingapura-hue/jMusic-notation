package music.notation.phrase;

import music.notation.duration.Duration;
import music.notation.event.Instrument;
import music.notation.structure.DrumTrack;
import music.notation.structure.MelodicTrack;

import java.util.List;
import java.util.Map;

public record RestPhrase(Duration duration, PhraseMarking marking) implements AuthorPhrase {

    /**
     * Phase 4b adapter: a rest phrase becomes a single Bar containing
     * one {@link RestNode} of the same duration. The phrase marking is
     * dropped. Rejects {@link Instrument#DRUM_KIT} on melodic conversion.
     */
    public MelodicTrack toMelodicTrack(String name, Instrument instrument) {
        Bar bar = new Bar(duration.sixtyFourths(), List.of(new RestNode(duration)));
        return new MelodicTrack(name, instrument, Phrase.of(bar));
    }

    /** Phase 4b adapter: same shape as {@link #toMelodicTrack} but on a {@link DrumTrack}. */
    public DrumTrack toDrumTrack(String name) {
        Bar bar = new Bar(duration.sixtyFourths(), List.of(new RestNode(duration)));
        return new DrumTrack(name, Phrase.of(bar));
    }
}
