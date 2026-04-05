package music.notation.phrase;

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
}
