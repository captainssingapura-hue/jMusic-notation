package music.notation.mxl;

import music.notation.performance.TempoChange;
import music.notation.performance.TempoTrack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Piecewise-constant tempo function indexed by MusicXML division position.
 *
 * <p>MusicXML tempo lives in {@code <sound tempo="…">} attributes scattered
 * through {@code <direction>} elements (and occasionally as direct measure
 * children). To compute concrete-note ms positions in the presence of
 * mid-piece tempo changes, we collect all such events with their
 * accumulated divisions-from-piece-start, then convert to millisecond
 * breakpoints by integrating segment-by-segment.</p>
 *
 * <p>{@link #divToMs(long)} answers "what ms position corresponds to this
 * division?" via binary search + linear interpolation within the segment.
 * {@link #toTempoTrack()} produces the canonical {@link TempoTrack} ready
 * for {@link music.notation.performance.Performance}.</p>
 */
final class TempoTimeline {

    private final long[] breakDivs;
    private final long[] breakMs;
    private final int[]  breakBpms;
    private final int    divisions;

    private TempoTimeline(long[] divs, long[] mss, int[] bpms, int divisions) {
        this.breakDivs = divs;
        this.breakMs   = mss;
        this.breakBpms = bpms;
        this.divisions = divisions;
    }

    static TempoTimeline constant(int divisions, int bpm) {
        return new TempoTimeline(new long[]{0L}, new long[]{0L}, new int[]{bpm}, divisions);
    }

    static TempoTimeline from(List<TempoEvent> events, int divisions, int defaultBpm) {
        if (events.isEmpty()) return constant(divisions, defaultBpm);

        // 1. Stable-sort by div (Java List.sort = TimSort, stable).
        List<TempoEvent> ordered = new ArrayList<>(events);
        ordered.sort(Comparator.comparingLong(TempoEvent::div));

        // 2. Same-div dedup: a later in-source event overrides an earlier one.
        List<TempoEvent> sameDivDeduped = new ArrayList<>();
        for (TempoEvent e : ordered) {
            int last = sameDivDeduped.size() - 1;
            if (last >= 0 && sameDivDeduped.get(last).div() == e.div()) {
                sameDivDeduped.set(last, e);
            } else {
                sameDivDeduped.add(e);
            }
        }

        // 3. Ensure a segment starts at div=0 — fall back to defaultBpm.
        if (sameDivDeduped.get(0).div() > 0) {
            sameDivDeduped.add(0, new TempoEvent(0L, defaultBpm));
        }

        // 4. Consecutive same-bpm dedup — collapse no-op tempo "changes".
        List<TempoEvent> bpmDeduped = new ArrayList<>();
        for (TempoEvent e : sameDivDeduped) {
            int last = bpmDeduped.size() - 1;
            if (last >= 0 && bpmDeduped.get(last).bpm() == e.bpm()) continue;
            bpmDeduped.add(e);
        }

        // 5. Compute ms anchor at each break by integrating segment durations.
        int n = bpmDeduped.size();
        long[] ds = new long[n];
        long[] ms = new long[n];
        int[]  bs = new int[n];
        ds[0] = bpmDeduped.get(0).div();
        ms[0] = 0L;
        bs[0] = bpmDeduped.get(0).bpm();
        for (int i = 1; i < n; i++) {
            long divSpan = bpmDeduped.get(i).div() - bpmDeduped.get(i - 1).div();
            long msSpan  = Math.round(divSpan * 60_000.0 / (bs[i - 1] * (double) divisions));
            ds[i] = bpmDeduped.get(i).div();
            ms[i] = ms[i - 1] + msSpan;
            bs[i] = bpmDeduped.get(i).bpm();
        }
        return new TempoTimeline(ds, ms, bs, divisions);
    }

    long divToMs(long div) {
        int idx = segmentIndex(div);
        long delta = div - breakDivs[idx];
        return breakMs[idx] + Math.round(delta * 60_000.0 / (breakBpms[idx] * (double) divisions));
    }

    int initialBpm() { return breakBpms[0]; }

    TempoTrack toTempoTrack() {
        List<TempoChange> changes = new ArrayList<>(breakBpms.length);
        for (int i = 0; i < breakBpms.length; i++) {
            changes.add(new TempoChange(breakMs[i], breakBpms[i]));
        }
        return new TempoTrack(changes);
    }

    /** Largest index i such that {@code breakDivs[i] <= div}. Assumes ascending divs. */
    private int segmentIndex(long div) {
        int lo = 0, hi = breakDivs.length - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (breakDivs[mid] <= div) lo = mid; else hi = mid - 1;
        }
        return lo;
    }

    record TempoEvent(long div, int bpm) {}
}
