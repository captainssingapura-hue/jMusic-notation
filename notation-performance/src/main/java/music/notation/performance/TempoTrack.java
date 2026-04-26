package music.notation.performance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Piece-wide sparse tempo timeline. Empty means playback uses a runtime default of
 * 120 bpm and the codec writes no tempo meta event. Consecutive same-bpm entries are
 * deduped to keep representation canonical.
 */
public record TempoTrack(List<TempoChange> changes) {
    public TempoTrack {
        Objects.requireNonNull(changes, "changes");
        List<TempoChange> sorted = new ArrayList<>(changes);
        sorted.sort(Comparator.comparingLong(TempoChange::tickMs));
        List<TempoChange> deduped = new ArrayList<>(sorted.size());
        int last = Integer.MIN_VALUE;
        for (TempoChange c : sorted) {
            if (c.bpm() != last) {
                deduped.add(c);
                last = c.bpm();
            }
        }
        changes = List.copyOf(deduped);
    }

    public static TempoTrack empty() { return new TempoTrack(List.of()); }

    public static TempoTrack constant(int bpm) {
        return new TempoTrack(List.of(new TempoChange(0, bpm)));
    }
}
