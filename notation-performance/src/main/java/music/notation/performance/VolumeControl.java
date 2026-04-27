package music.notation.performance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Per-track sparse volume timeline: MIDI CC #7 set-points anchored to
 * ticks. Consecutive same-level entries are deduped to keep the
 * representation canonical.
 *
 * <p>An empty control means "no codec-emitted volume CC #7" — the synth's
 * default applies. {@link #constant(int)} pins a flat level for the whole
 * track.</p>
 */
public record VolumeControl(List<VolumeChange> changes) {
    public VolumeControl {
        Objects.requireNonNull(changes, "changes");
        List<VolumeChange> sorted = new ArrayList<>(changes);
        sorted.sort(Comparator.comparingLong(VolumeChange::tickMs));
        List<VolumeChange> deduped = new ArrayList<>(sorted.size());
        int last = Integer.MIN_VALUE;
        for (VolumeChange c : sorted) {
            if (c.level() != last) {
                deduped.add(c);
                last = c.level();
            }
        }
        changes = List.copyOf(deduped);
    }

    public static VolumeControl empty() { return new VolumeControl(List.of()); }

    public static VolumeControl constant(int level) {
        return new VolumeControl(List.of(new VolumeChange(0, level)));
    }
}
