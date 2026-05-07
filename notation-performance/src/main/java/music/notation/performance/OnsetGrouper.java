package music.notation.performance;

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
 * <p>Stateless and thread-safe.</p>
 */
public final class OnsetGrouper {

    private OnsetGrouper() {}

    /**
     * A coalesced note event: one onset, one duration, one or more
     * pitches. Single-pitch events use a one-element {@code pitches}
     * list; chord events list pitches in ascending MIDI order.
     */
    public record GroupedEvent(long onsetMs, long durationMs, List<Integer> pitches) {
        public GroupedEvent {
            if (onsetMs < 0)     throw new IllegalArgumentException("onsetMs must be >= 0");
            if (durationMs <= 0) throw new IllegalArgumentException("durationMs must be > 0");
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

    /** Group with default jitter tolerance of 0 ms (score-derived MIDI). */
    public static List<GroupedEvent> group(List<PitchedNote> notes) {
        return group(notes, 0);
    }

    /**
     * Coalesce same-onset same-duration notes into chord events.
     *
     * <p>Two notes count as "same onset" when their {@code tickMs}
     * differ by at most {@code jitterMs}; "same duration" when their
     * {@code durationMs} differ by at most {@code jitterMs}. Notes
     * are first sorted by onset, then by duration, then by pitch, so
     * the algorithm is deterministic regardless of input order.</p>
     *
     * @param notes input notes; need not be pre-sorted.
     * @param jitterMs tolerance in milliseconds. {@code 0} for
     *                 score-derived MIDI; ~10 ms is reasonable for
     *                 human-played MIDI.
     * @return events sorted ascending by onset; each event's pitches
     *         sorted ascending.
     */
    public static List<GroupedEvent> group(List<PitchedNote> notes, int jitterMs) {
        if (notes.isEmpty()) return List.of();
        var sorted = new ArrayList<>(notes);
        sorted.sort((a, b) -> {
            int c = Long.compare(a.tickMs(), b.tickMs());
            if (c != 0) return c;
            c = Long.compare(a.durationMs(), b.durationMs());
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
                    && near(sorted.get(j).tickMs(),     head.tickMs(),     jitterMs)
                    && near(sorted.get(j).durationMs(), head.durationMs(), jitterMs)) {
                pitches.add(sorted.get(j).midi());
                j++;
            }
            Collections.sort(pitches);
            out.add(new GroupedEvent(head.tickMs(), head.durationMs(), pitches));
            i = j;
        }
        return out;
    }

    private static boolean near(long a, long b, int tolerance) {
        return Math.abs(a - b) <= tolerance;
    }
}
