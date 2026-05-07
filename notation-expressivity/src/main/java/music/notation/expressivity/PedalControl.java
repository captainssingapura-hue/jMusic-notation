package music.notation.expressivity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Per-track sustain-pedal timeline — sparse list of {@link PedalChange}s
 * sorted by tick. Consecutive same-state events are deduped to keep the
 * representation canonical (a {@code DOWN} that's already DOWN is a no-op).
 *
 * <p>An empty control means "no codec-emitted pedal events" — the synth's
 * default applies (pedal up). {@link #constant(PedalState)} pins a flat
 * state for the whole track.</p>
 */
public record PedalControl(List<PedalChange> changes) {
    public PedalControl {
        Objects.requireNonNull(changes, "changes");
        List<PedalChange> sorted = new ArrayList<>(changes);
        sorted.sort(Comparator.comparingLong(PedalChange::tickMs));
        List<PedalChange> deduped = new ArrayList<>(sorted.size());
        PedalState last = null;
        for (PedalChange c : sorted) {
            // CHANGE always survives (its meaning is "edge regardless of
            // current state"); DOWN/UP only survive when they flip the state.
            if (c.state() == PedalState.CHANGE || c.state() != last) {
                deduped.add(c);
                last = c.state();
            }
        }
        changes = List.copyOf(deduped);
    }

    public static PedalControl empty() { return new PedalControl(List.of()); }

    public static PedalControl constant(PedalState state) {
        return new PedalControl(List.of(new PedalChange(0, state)));
    }
}
