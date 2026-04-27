package music.notation.phrase;

import music.notation.duration.Duration;
import music.notation.event.Instrument;
import music.notation.structure.DrumTrack;
import music.notation.structure.MelodicTrack;

import java.util.List;

public record RestPhrase(Duration duration, PhraseMarking marking) implements Phrase {

    /**
     * Phase 4b adapter: a rest phrase becomes a single Bar containing
     * one {@link RestNode} of the same duration. The phrase marking is
     * dropped. Rejects {@link Instrument#DRUM_KIT} on melodic conversion.
     */
    public MelodicTrack toMelodicTrack(String name, Instrument instrument) {
        Bar bar = new Bar(duration.sixtyFourths(), List.of(new RestNode(duration)), List.of());
        return new MelodicTrack(name, instrument, List.of(bar), List.of());
    }

    /** Phase 4b adapter: same shape as {@link #toMelodicTrack} but on a {@link DrumTrack}. */
    public DrumTrack toDrumTrack(String name) {
        Bar bar = new Bar(duration.sixtyFourths(), List.of(new RestNode(duration)), List.of());
        return new DrumTrack(name, List.of(bar), List.of());
    }
}
