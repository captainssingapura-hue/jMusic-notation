package music.notation.phrase;

import music.notation.structure.DrumTrack;
import music.notation.structure.TimeSignature;

import java.util.ArrayList;
import java.util.List;

public record DrumPhrase(List<PhraseNode> nodes, PhraseMarking marking) implements Phrase {
    public DrumPhrase {
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("DrumPhrase must contain at least one node");
        }
        nodes = List.copyOf(nodes);
    }

    /**
     * Build a drum phrase from validated bars. Each {@link Bar} has already verified
     * its own duration at construction time.
     */
    public static DrumPhrase fromBars(TimeSignature ts, PhraseMarking marking, Bar... bars) {
        var nodes = new ArrayList<PhraseNode>();
        for (Bar bar : bars) {
            nodes.addAll(bar.nodes());
        }
        return new DrumPhrase(nodes, marking);
    }

    /**
     * Phase 4b adapter: convert this phrase into a {@link DrumTrack}.
     *
     * <p><b>Lossy migration.</b> The phrase marking is dropped. All
     * nodes are collapsed into a single Bar sized to the total duration
     * — {@code DrumPhrase} has never carried a bar structure, so this
     * is the closest faithful representation.</p>
     *
     * @param name the resulting track's name
     */
    public DrumTrack toDrumTrack(String name) {
        int total = 0;
        for (PhraseNode n : nodes) {
            total += Bar.nodeSixtyFourths(n);
        }
        Bar bar = new Bar(total, nodes, List.of());
        return new DrumTrack(name, BarPhrase.of(bar), List.of());
    }
}
