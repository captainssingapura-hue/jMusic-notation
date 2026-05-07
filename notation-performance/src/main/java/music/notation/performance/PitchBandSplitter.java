package music.notation.performance;

import music.notation.performance.OnsetGrouper.GroupedEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Tier 1 voice separation — splits a single grouped-event stream into
 * a "high" band and a "low" band by a fixed MIDI-pitch cutoff.
 *
 * <p>For keyboard music this approximates the right-hand / left-hand
 * split. A chord that <em>straddles</em> the cutoff (e.g. a C-major
 * voicing C3-G3-C4-E4 with cutoff = 60) is split into two parallel
 * events at the same onset and duration but with disjoint pitch sets,
 * so each band remains a faithful sub-chord of the original.</p>
 *
 * <p>Default cutoff = 60 (middle C). The cutoff is inclusive on the
 * "high" side: pitch == cutoff goes to the high band. Tune per-piece
 * via {@link #split(List, int)} for material whose hands sit unusually
 * high or low.</p>
 *
 * <p>See {@code .docs/voice-separation/tier-1-pitch-band.md}.</p>
 *
 * <p>Stateless and thread-safe.</p>
 */
public final class PitchBandSplitter {

    private PitchBandSplitter() {}

    /** Output of a band split. Either band may be empty. */
    public record SplitResult(List<GroupedEvent> high, List<GroupedEvent> low) {
        public SplitResult {
            high = List.copyOf(high);
            low  = List.copyOf(low);
        }

        /** Whether anything ended up in the high band. */
        public boolean hasHigh() { return !high.isEmpty(); }

        /** Whether anything ended up in the low band. */
        public boolean hasLow()  { return !low.isEmpty(); }
    }

    /** Split with the conventional middle-C cutoff (MIDI 60). */
    public static SplitResult split(List<GroupedEvent> events) {
        return split(events, 60);
    }

    /**
     * Split each event into the band determined by its pitch contents.
     *
     * @param events  Tier-0-grouped event stream (output of
     *                {@link OnsetGrouper#group}).
     * @param cutoff  MIDI-pitch threshold; pitches {@code >= cutoff}
     *                go to {@code high}, pitches {@code < cutoff} go
     *                to {@code low}.
     * @return two parallel lists; events keep their relative order.
     */
    public static SplitResult split(List<GroupedEvent> events, int cutoff) {
        var high = new ArrayList<GroupedEvent>();
        var low  = new ArrayList<GroupedEvent>();
        for (GroupedEvent ev : events) {
            // Fast path: event entirely in one band.
            if (ev.lowestPitch() >= cutoff) {
                high.add(ev);
                continue;
            }
            if (ev.highestPitch() < cutoff) {
                low.add(ev);
                continue;
            }
            // Straddling chord: partition pitches.
            var hp = new ArrayList<Integer>();
            var lp = new ArrayList<Integer>();
            for (int p : ev.pitches()) {
                if (p >= cutoff) hp.add(p);
                else             lp.add(p);
            }
            if (!hp.isEmpty()) high.add(new GroupedEvent(ev.onsetMs(), ev.durationMs(), hp));
            if (!lp.isEmpty()) low .add(new GroupedEvent(ev.onsetMs(), ev.durationMs(), lp));
        }
        return new SplitResult(high, low);
    }
}
