package music.notation.phrase;

import music.notation.duration.Duration;

import java.util.ArrayList;

/**
 * Whole-bar aux (voice overlay) sub-builder used inside a lambda from
 * {@link BarBuilderTyped#aux(java.util.function.Consumer)}. The
 * collected nodes form a single aux {@link Bar} matching the parent's
 * bar size; if the content is shorter than the bar a trailing
 * {@link RestNode} is appended to fill, longer content throws.
 */
public final class AuxBarBuilderTyped extends NoteAcceptor<AuxBarBuilderTyped> {

    AuxBarBuilderTyped(BuilderContext ctx, Duration activeDur) {
        super(ctx, activeDur);
    }

    /** Freeze the collected nodes into a {@link Bar} sized to {@code barSize}. */
    Bar toBar(int barSize) {
        consumed = true;
        int total = 0;
        for (PhraseNode n : current) total += Bar.nodeSixtyFourths(n);
        if (total > barSize) {
            throw new IllegalArgumentException(
                    "Aux content totals " + total + "/64 but bar is only "
                            + barSize + "/64");
        }
        if (total < barSize) {
            var padded = new ArrayList<>(current);
            padded.add(new RestNode(Duration.ofSixtyFourths(barSize - total)));
            return new Bar(music.notation.duration.BarDuration.fromSixtyFourths(barSize), padded);
        }
        return new Bar(music.notation.duration.BarDuration.fromSixtyFourths(barSize), current);
    }
}
