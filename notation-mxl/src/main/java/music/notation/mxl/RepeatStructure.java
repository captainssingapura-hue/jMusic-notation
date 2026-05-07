package music.notation.mxl;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

/**
 * Notation-layout annotation that captures the score's repeat / volta /
 * jump markings independently of the {@link music.notation.performance.Performance}
 * timeline. The {@code Performance} is the expanded playback truth — every
 * loop and pass already materialised; this record describes the original
 * sheet-music structure so a downstream tool can reconstruct the source
 * MusicXML or sheet music if desired.
 *
 * <p>All measure indices reference the <em>original</em> measure list
 * (not the expanded schedule), using the XML {@code <measure number="…">}
 * attribute when present. Empty fields ({@code List.of()}) indicate the
 * score has none of that construct.</p>
 *
 * @see RepeatExpander for the playback-schedule simulation that consumes
 *      this structure
 */
public record RepeatStructure(
        List<RepeatBar> repeatBars,
        List<Volta> voltas,
        List<Jump> jumps
) {
    public RepeatStructure {
        repeatBars = List.copyOf(repeatBars);
        voltas     = List.copyOf(voltas);
        jumps      = List.copyOf(jumps);
    }

    public static RepeatStructure empty() {
        return new RepeatStructure(List.of(), List.of(), List.of());
    }

    @JsonIgnore
    public boolean isEmpty() {
        return repeatBars.isEmpty() && voltas.isEmpty() && jumps.isEmpty();
    }

    /** A single {@code <repeat direction="…" times="…"/>} barline. */
    public record RepeatBar(int measureIndex, Direction direction, int times) {
        public RepeatBar {
            if (times < 1) throw new IllegalArgumentException("times must be >= 1: " + times);
        }
    }

    /**
     * A volta (alternative ending) span. {@code numbers} are the pass
     * numbers on which this ending plays — typically {@code [1]} for
     * "1." and {@code [2]} for "2." but composers can write
     * {@code "1, 3"} for shared endings.
     */
    public record Volta(int startMeasure, int stopMeasure, List<Integer> numbers) {
        public Volta {
            numbers = List.copyOf(numbers);
        }
    }

    /**
     * A jump-flow marker on a measure: segno / coda anchors and
     * D.C. / D.S. / to-coda / fine reroutes.
     *
     * @param label segno/coda label (often empty); empty for unnamed
     *              segno/coda pairs and for D.C./fine which don't
     *              reference a label
     */
    public record Jump(int measureIndex, JumpKind kind, String label) {
        public Jump {
            label = (label == null) ? "" : label;
        }
    }

    public enum Direction { FORWARD, BACKWARD }

    public enum JumpKind { SEGNO, CODA, DACAPO, DALSEGNO, TOCODA, FINE }
}
