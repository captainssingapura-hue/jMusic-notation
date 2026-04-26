package music.notation.performance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Per-track sparse program timeline: program changes anchored to ticks. Consecutive
 * same-program entries are deduped to keep representation canonical.
 */
public record InstrumentControl(List<InstrumentChange> changes) {
    public InstrumentControl {
        Objects.requireNonNull(changes, "changes");
        List<InstrumentChange> sorted = new ArrayList<>(changes);
        sorted.sort(Comparator.comparingLong(InstrumentChange::tickMs));
        List<InstrumentChange> deduped = new ArrayList<>(sorted.size());
        int last = Integer.MIN_VALUE;
        for (InstrumentChange c : sorted) {
            if (c.program() != last) {
                deduped.add(c);
                last = c.program();
            }
        }
        changes = List.copyOf(deduped);
    }

    public static InstrumentControl empty() { return new InstrumentControl(List.of()); }

    public static InstrumentControl constant(int program) {
        return new InstrumentControl(List.of(new InstrumentChange(0, program)));
    }
}
