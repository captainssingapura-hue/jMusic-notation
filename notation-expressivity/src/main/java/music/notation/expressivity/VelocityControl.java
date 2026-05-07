package music.notation.expressivity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Per-track sustain velocity timeline — sparse list of
 * {@link VelocityChange}s sorted by tick. Consecutive same-velocity
 * events are deduped to keep the representation canonical (a velocity
 * already at 80 doesn't need a second "set to 80" entry).
 *
 * <p>An empty control means "no codec-emitted velocity events" — every
 * note on the track inherits the codec default ({@value #DEFAULT_VELOCITY}).
 * {@link #constant(int)} pins a flat velocity for the whole track.</p>
 *
 * <p>Lookup semantics — step-function. {@link #velocityAt(long)}
 * returns the most recent change whose {@code tickMs <= queryMs};
 * before the first change (or when no entries exist), it returns
 * {@link #DEFAULT_VELOCITY}.</p>
 */
public record VelocityControl(List<VelocityChange> changes) {

    /** MIDI {@code mf} — used when no entry covers a query. */
    public static final int DEFAULT_VELOCITY = 90;

    public VelocityControl {
        Objects.requireNonNull(changes, "changes");
        List<VelocityChange> sorted = new ArrayList<>(changes);
        sorted.sort(Comparator.comparingLong(VelocityChange::tickMs));
        List<VelocityChange> deduped = new ArrayList<>(sorted.size());
        int last = Integer.MIN_VALUE;
        for (VelocityChange c : sorted) {
            if (c.velocity() != last) {
                deduped.add(c);
                last = c.velocity();
            }
        }
        changes = List.copyOf(deduped);
    }

    public static VelocityControl empty() {
        return new VelocityControl(List.of());
    }

    public static VelocityControl constant(int velocity) {
        return new VelocityControl(List.of(new VelocityChange(0, velocity)));
    }

    /**
     * Step-function lookup: return the most recent {@link VelocityChange}'s
     * velocity at or before {@code queryMs}, falling back to
     * {@link #DEFAULT_VELOCITY} when no entry covers the query.
     */
    public int velocityAt(long queryMs) {
        int current = DEFAULT_VELOCITY;
        for (VelocityChange c : changes) {
            if (c.tickMs() > queryMs) break;
            current = c.velocity();
        }
        return current;
    }
}
