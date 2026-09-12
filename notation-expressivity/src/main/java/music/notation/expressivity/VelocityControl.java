package music.notation.expressivity;

import music.notation.duration.Duration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Per-track sustain velocity timeline — sparse list of
 * {@link VelocityChange}s sorted by musical position. Consecutive
 * same-level events are deduped to keep the representation canonical
 * (a level already at 0.5 doesn't need a second "set to 0.5" entry).
 *
 * <p>An empty control means "no codec-emitted velocity events" —
 * every note on the track inherits the codec default
 * ({@link #DEFAULT_LEVEL}, which maps to MIDI velocity 90 = mf at
 * the boundary).
 * {@link #constant(double)} pins a flat level for the whole track.</p>
 *
 * <p>Lookup semantics — step-function. {@link #levelAt(Duration)}
 * returns the most recent change at or before the query, falling
 * back to {@link #DEFAULT_LEVEL}.</p>
 */
public record VelocityControl(List<VelocityChange> changes) {

    /** Abstract loudness used when no entry covers a query (≈ mf). */
    public static final double DEFAULT_LEVEL = 0.71;     // 90/127 ≈ mf

    public VelocityControl {
        Objects.requireNonNull(changes, "changes");
        List<VelocityChange> sorted = new ArrayList<>(changes);
        sorted.sort(Comparator.comparing(VelocityChange::at,
                (a, b) -> a.compareDuration(b)));
        List<VelocityChange> deduped = new ArrayList<>(sorted.size());
        Loudness last = null;
        for (VelocityChange c : sorted) {
            // Record-equality dedup: Named(F) and Raw(0.76) are NOT
            // deduped against each other even though their levels
            // coincide — the symbolic mark is semantically distinct
            // from a numeric value and must survive engraver round-trip.
            if (!c.loudness().equals(last)) {
                deduped.add(c);
                last = c.loudness();
            }
        }
        changes = List.copyOf(deduped);
    }

    public static VelocityControl empty() {
        return new VelocityControl(List.of());
    }

    public static VelocityControl constant(Loudness loudness) {
        return new VelocityControl(List.of(new VelocityChange(Duration.zero(), loudness)));
    }

    public static VelocityControl constant(double level) {
        return constant(Loudness.of(level));
    }

    public static VelocityControl constant(music.notation.event.Dynamic mark) {
        return constant(Loudness.of(mark));
    }

    /**
     * Step-function lookup: return the most recent
     * {@link VelocityChange}'s level at or before {@code query},
     * falling back to {@link #DEFAULT_LEVEL} when no entry covers
     * the query.
     */
    public double levelAt(Duration query) {
        double current = DEFAULT_LEVEL;
        for (VelocityChange c : changes) {
            if (c.at().compareDuration(query) > 0) break;
            current = c.level();
        }
        return current;
    }
}
