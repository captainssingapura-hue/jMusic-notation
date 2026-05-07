package music.notation.phrase;

import music.notation.event.Instrument;
import music.notation.structure.MelodicTrack;
import music.notation.structure.TimeSignature;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A melodic phrase — a sequence of {@link PhraseNode}s optionally structured
 * into {@link Bar}s, optionally carrying parallel {@link VoiceOverlay}s and
 * sparsely-keyed aux voices that travel through to {@link MelodicTrack} via
 * {@link #toMelodicTrack(String, Instrument)}.
 */
public record MelodicPhrase(
        List<PhraseNode> nodes,
        List<Bar> bars,
        PhraseMarking marking,
        List<VoiceOverlay> voices,
        Map<String, Map<Integer, Bar>> auxBarsSparse
) implements AuthorPhrase {

    public MelodicPhrase {
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("MelodicPhrase must contain at least one node");
        }
        nodes = List.copyOf(nodes);
        bars = List.copyOf(bars);
        voices = List.copyOf(voices);
        auxBarsSparse = auxBarsSparse == null ? Map.of() : Map.copyOf(auxBarsSparse);
        validateVoices(bars, voices);
    }

    public MelodicPhrase(List<PhraseNode> nodes, PhraseMarking marking) {
        this(nodes, List.of(), marking, List.of(), Map.of());
    }

    public MelodicPhrase(List<PhraseNode> nodes, List<Bar> bars, PhraseMarking marking) {
        this(nodes, bars, marking, List.of(), Map.of());
    }

    public MelodicPhrase(List<PhraseNode> nodes, List<Bar> bars,
                         PhraseMarking marking, List<VoiceOverlay> voices) {
        this(nodes, bars, marking, voices, Map.of());
    }

    /**
     * Phase 4b adapter: convert this phrase into a {@link MelodicTrack}.
     * Aux voices declared on the underlying builder travel through to
     * the {@link Phrase} created here.
     */
    public MelodicTrack toMelodicTrack(String name, Instrument instrument) {
        List<Bar> outBars;
        if (!bars.isEmpty()) {
            outBars = bars;
        } else {
            int total = 0;
            for (PhraseNode n : nodes) {
                total += Bar.nodeSixtyFourths(n);
            }
            outBars = List.of(new Bar(
                    music.notation.duration.BarDuration.fromSixtyFourths(total), nodes));
        }
        return new MelodicTrack(name, instrument, Phrase.of(outBars, auxBarsSparse));
    }

    private static void validateVoices(List<Bar> bars, List<VoiceOverlay> voices) {
        if (voices.isEmpty()) return;
        if (bars.isEmpty()) {
            throw new IllegalArgumentException(
                    "MelodicPhrase with voices must have a bar structure (bars is empty)");
        }
        for (int v = 0; v < voices.size(); v++) {
            VoiceOverlay overlay = voices.get(v);
            if (overlay.size() != bars.size()) {
                throw new IllegalArgumentException(
                        "Voice " + v + ": overlay has " + overlay.size()
                                + " bar slots but main phrase has " + bars.size());
            }
            for (int i = 0; i < bars.size(); i++) {
                Optional<Bar> maybe = overlay.at(i);
                if (maybe.isEmpty()) continue;
                int actual = maybe.get().expectedSixtyFourths();
                int expected = bars.get(i).expectedSixtyFourths();
                if (actual != expected) {
                    throw new IllegalArgumentException(
                            "Voice " + v + ", bar " + i + ": overlay bar is "
                                    + actual + "/64 but main bar is " + expected + "/64");
                }
            }
        }
    }

    /**
     * Build a phrase from validated bars (no aux). Each {@link Bar} has already
     * verified its own duration at construction time. Middle bars must match the
     * time signature; first/last may be partial pickups.
     */
    public static MelodicPhrase fromBars(TimeSignature ts, PhraseMarking marking, Bar... bars) {
        return fromBars(ts, marking, Map.of(), bars);
    }

    /**
     * Build a phrase from validated bars plus a sparse aux map (typically
     * supplied by {@link StaffPhraseBuilderTyped}).
     */
    public static MelodicPhrase fromBars(TimeSignature ts, PhraseMarking marking,
                                         Map<String, Map<Integer, Bar>> auxBarsSparse,
                                         Bar... bars) {
        int expected = ts.barSixtyFourths();
        for (int i = 0; i < bars.length; i++) {
            int actual = bars[i].expectedSixtyFourths();
            if (actual != expected) {
                boolean isFirstOrLast = (i == 0 || i == bars.length - 1);
                if (isFirstOrLast && actual < expected) {
                    continue;
                }
                throw new IllegalArgumentException(
                        "Bar " + (i + 1) + "/" + bars.length + ": expected " + expected
                                + " sixty-fourths (" + ts.beats() + "/" + ts.beatValue()
                                + ") but got " + actual);
            }
        }
        var flat = new ArrayList<PhraseNode>();
        for (Bar bar : bars) {
            flat.addAll(bar.nodes());
        }
        return new MelodicPhrase(flat, List.of(bars), marking, List.of(), auxBarsSparse);
    }
}
