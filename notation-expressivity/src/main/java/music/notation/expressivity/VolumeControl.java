package music.notation.expressivity;

import music.notation.duration.Duration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Per-track sparse volume timeline: channel-volume set-points anchored at
 * musical positions. The {@code level} carried by each {@link VolumeChange}
 * is a synth-agnostic loudness in <code>[0.0, 1.0]</code>; the codec maps
 * it to MIDI {@code CC #7 = round(level × 127)} at the emit boundary.
 *
 * <p>Consecutive same-level entries are deduped to keep the representation
 * canonical.</p>
 *
 * <p>An empty control means "no codec-emitted volume CC #7" — the synth's
 * default applies. {@link #constant(double)} pins a flat level for the
 * whole track.</p>
 */
public record VolumeControl(List<VolumeChange> changes) {
    public VolumeControl {
        Objects.requireNonNull(changes, "changes");
        List<VolumeChange> sorted = new ArrayList<>(changes);
        sorted.sort(Comparator.comparing(VolumeChange::at,
                (a, b) -> a.compareDuration(b)));
        List<VolumeChange> deduped = new ArrayList<>(sorted.size());
        Loudness last = null;
        for (VolumeChange c : sorted) {
            // Record-equality dedup: Named(F) and Raw(0.76) stay
            // distinct even when their levels coincide — preserves
            // authored symbolic marks through round-trip.
            if (!c.loudness().equals(last)) {
                deduped.add(c);
                last = c.loudness();
            }
        }
        changes = List.copyOf(deduped);
    }

    public static VolumeControl empty() { return new VolumeControl(List.of()); }

    public static VolumeControl constant(Loudness loudness) {
        return new VolumeControl(List.of(new VolumeChange(Duration.zero(), loudness)));
    }

    public static VolumeControl constant(double level) {
        return constant(Loudness.of(level));
    }

    public static VolumeControl constant(music.notation.event.Dynamic mark) {
        return constant(Loudness.of(mark));
    }
}
