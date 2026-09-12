package music.notation.performance;

import music.notation.duration.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tier 0 of voice separation — coalesces same-onset, same-duration
 * {@link PitchedNote}s into single {@link GroupedEvent}s representing
 * authored chords.
 *
 * <p>MIDI emits a chord as N parallel {@code note-on}s with identical
 * timing; the inverse fold is needed before any voice-separation logic
 * runs. See {@code .docs/voice-separation/tier-0-onset-grouping.md}.</p>
 *
 * <p>Post-ms→Duration migration: onset/duration tolerances are
 * expressed as musical {@link Duration}s. {@link Duration#zero()}
 * tolerance is fine for score-derived MIDI; a 1/96 tolerance (triplet
 * 32nd) is reasonable for human-played MIDI quantized to a coarse
 * grid.</p>
 *
 * <p>Stateless and thread-safe.</p>
 */
public final class OnsetGrouper {

    private OnsetGrouper() {}

    /**
     * A coalesced note event: one onset, one duration, one or more
     * pitches. Single-pitch events use a one-element {@code pitches}
     * list; chord events list pitches in ascending MIDI order.
     */
    public record GroupedEvent(Duration at, Duration duration, List<Integer> pitches) {
        public GroupedEvent {
            if (at == null)        throw new IllegalArgumentException("at must not be null");
            if (duration == null)  throw new IllegalArgumentException("duration must not be null");
            if (at.compareDuration(Duration.zero()) < 0)
                throw new IllegalArgumentException("at must be >= 0");
            if (duration.compareDuration(Duration.zero()) <= 0)
                throw new IllegalArgumentException("duration must be > 0");
            if (pitches == null || pitches.isEmpty()) {
                throw new IllegalArgumentException("pitches must be non-empty");
            }
            pitches = List.copyOf(pitches);
        }

        /** Highest pitch in the event. Useful for register-based logic. */
        public int highestPitch() { return pitches.get(pitches.size() - 1); }

        /** Lowest pitch. */
        public int lowestPitch()  { return pitches.get(0); }

        /** Mean pitch as a double — useful for centroid-based voice tracking. */
        public double centroid() {
            double sum = 0;
            for (int p : pitches) sum += p;
            return sum / pitches.size();
        }
    }

    /** Group with zero tolerance (exact same-position match, score-derived MIDI). */
    public static List<GroupedEvent> group(List<PitchedNote> notes) {
        return group(notes, Duration.zero());
    }

    /**
     * Coalesce same-onset same-duration notes into chord events.
     *
     * <p>Two notes count as "same onset" when their {@code at} positions
     * differ by at most {@code jitter}; "same duration" when their
     * {@code duration}s differ by at most {@code jitter}. Notes are
     * first sorted by onset, then by duration, then by pitch, so the
     * algorithm is deterministic regardless of input order.</p>
     *
     * @param notes input notes; need not be pre-sorted.
     * @param jitter musical-position tolerance. {@link Duration#zero()}
     *               for score-derived MIDI; ~1/96 (triplet 32nd) is
     *               reasonable for human-played MIDI on a coarse grid.
     */
    public static List<GroupedEvent> group(List<PitchedNote> notes, Duration jitter) {
        if (notes.isEmpty()) return List.of();
        var sorted = new ArrayList<>(notes);
        sorted.sort((a, b) -> {
            int c = a.at().compareDuration(b.at());
            if (c != 0) return c;
            c = a.duration().compareDuration(b.duration());
            if (c != 0) return c;
            return Integer.compare(a.midi(), b.midi());
        });

        var out = new ArrayList<GroupedEvent>();
        int i = 0;
        while (i < sorted.size()) {
            var head = sorted.get(i);
            var pitches = new ArrayList<Integer>();
            pitches.add(head.midi());
            int j = i + 1;
            while (j < sorted.size()
                    && near(sorted.get(j).at(),       head.at(),       jitter)
                    && near(sorted.get(j).duration(), head.duration(), jitter)) {
                pitches.add(sorted.get(j).midi());
                j++;
            }
            Collections.sort(pitches);
            out.add(new GroupedEvent(head.at(), head.duration(), pitches));
            i = j;
        }
        return out;
    }

    /** True when |a - b| ≤ tolerance, computed in rational space. */
    private static boolean near(Duration a, Duration b, Duration tolerance) {
        Duration diff = a.compareDuration(b) >= 0 ? a.minus(b) : b.minus(a);
        return diff.compareDuration(tolerance) <= 0;
    }
}
