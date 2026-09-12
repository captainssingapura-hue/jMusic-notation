package music.notation.mxl;

import music.notation.duration.Duration;
import music.notation.performance.TempoChange;
import music.notation.performance.TempoTrack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Piecewise-constant tempo function indexed by MusicXML division position.
 *
 * <p>MusicXML tempo lives in {@code <sound tempo="…">} attributes
 * scattered through {@code <direction>} elements (and occasionally as
 * direct measure children). To compute musical positions of notes in
 * the presence of mid-piece tempo changes, we collect tempo events
 * with their accumulated divisions-from-piece-start. The post-ms→Duration
 * model only needs the division anchors — the actual bpm values become
 * payload on the resulting {@link TempoTrack}, never used for position
 * math.</p>
 *
 * <p>{@link #divToDuration(long)} answers "what musical position
 * corresponds to this division?" by direct rational construction —
 * tempo doesn't enter. {@link #toTempoTrack()} produces the canonical
 * Duration-anchored {@link TempoTrack} ready for
 * {@link music.notation.performance.Performance}.</p>
 */
final class TempoTimeline {

    private final long[] breakDivs;
    private final int[]  breakBpms;
    private final int    divisions;

    private TempoTimeline(long[] divs, int[] bpms, int divisions) {
        this.breakDivs = divs;
        this.breakBpms = bpms;
        this.divisions = divisions;
    }

    static TempoTimeline constant(int divisions, int bpm) {
        return new TempoTimeline(new long[]{0L}, new int[]{bpm}, divisions);
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

        int n = bpmDeduped.size();
        long[] ds = new long[n];
        int[]  bs = new int[n];
        for (int i = 0; i < n; i++) {
            ds[i] = bpmDeduped.get(i).div();
            bs[i] = bpmDeduped.get(i).bpm();
        }
        return new TempoTimeline(ds, bs, divisions);
    }

    /**
     * Convert a MusicXML division position to a musical
     * {@link Duration}. {@code divisions} is ticks per quarter note,
     * so {@code div / (divisions × 4)} is the position in whole notes.
     * Pure rational arithmetic — no tempo involved.
     */
    Duration divToDuration(long div) {
        return Duration.of(div, (long) divisions * 4L);
    }

    int initialBpm() { return breakBpms[0]; }

    /**
     * Produce the canonical {@link TempoTrack} — each break-point's
     * division position is converted to a musical {@link Duration}.
     */
    TempoTrack toTempoTrack() {
        List<TempoChange> changes = new ArrayList<>(breakBpms.length);
        for (int i = 0; i < breakBpms.length; i++) {
            changes.add(new TempoChange(divToDuration(breakDivs[i]), breakBpms[i]));
        }
        return new TempoTrack(changes);
    }

    record TempoEvent(long div, int bpm) {}
}
