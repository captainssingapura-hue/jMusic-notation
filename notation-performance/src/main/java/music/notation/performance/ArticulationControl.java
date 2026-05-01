package music.notation.performance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Per-track sparse articulation timeline. Consecutive same-kind entries are deduped
 * to keep representation canonical.
 */
public record ArticulationControl(List<ArticulationChange> changes) {
    public ArticulationControl {
        Objects.requireNonNull(changes, "changes");
        List<ArticulationChange> sorted = new ArrayList<>(changes);
        sorted.sort(Comparator.comparingLong(ArticulationChange::tickMs));
        List<ArticulationChange> deduped = new ArrayList<>(sorted.size());
        Articulation last = null;
        for (ArticulationChange c : sorted) {
            if (c.kind() != last) {
                deduped.add(c);
                last = c.kind();
            }
        }
        changes = List.copyOf(deduped);
    }

    public static ArticulationControl empty() { return new ArticulationControl(List.of()); }

    public static ArticulationControl constant(Articulation kind) {
        return new ArticulationControl(List.of(new ArticulationChange(0, kind)));
    }
}
