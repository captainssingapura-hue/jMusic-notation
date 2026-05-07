package music.notation.phrase;

import music.notation.event.ChordEvent;
import music.notation.event.Instrument;
import music.notation.structure.MelodicTrack;

import java.util.ArrayList;
import java.util.List;

public record ChordPhrase(List<ChordEvent> chords, PhraseMarking marking) implements AuthorPhrase {
    public ChordPhrase {
        if (chords.isEmpty()) {
            throw new IllegalArgumentException("ChordPhrase must contain at least one chord");
        }
        chords = List.copyOf(chords);
    }

    /**
     * Phase 4b adapter: convert this phrase into a {@link MelodicTrack}.
     *
     * <p>Each {@link ChordEvent} becomes one {@link PolyPitchNode} (every
     * ChordEvent has ≥2 pitches by construction, so the routing is
     * unambiguous). All resulting nodes are wrapped into a single
     * {@link Bar} sized to the total duration. The phrase marking and
     * the chord-event articulations are dropped — neither has a
     * representation on {@code MelodicTrack} yet.</p>
     *
     * @param name the resulting track's name
     * @param instrument the resulting track's default instrument; must not be {@link Instrument#DRUM_KIT}
     */
    public MelodicTrack toMelodicTrack(String name, Instrument instrument) {
        var nodes = new ArrayList<PhraseNode>(chords.size());
        int total = 0;
        for (ChordEvent chord : chords) {
            nodes.add(new PolyPitchNode(
                    chord.pitches(),
                    chord.duration(),
                    List.of(),   // graceNotes
                    false,       // equalDivision
                    false));     // tiedToNext
            total += chord.duration().sixtyFourths();
        }
        Bar bar = new Bar(music.notation.duration.BarDuration.fromSixtyFourths(total), nodes);
        return new MelodicTrack(name, instrument, Phrase.of(bar));
    }
}
