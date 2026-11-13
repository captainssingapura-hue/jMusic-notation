package music.notation.transcribe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Snaps real-time notes onto the bar grid of a {@link TimingSpec}.
 *
 * <ul>
 *   <li>Onset and release each snap to the nearest grid step, measured
 *       from the downbeat.</li>
 *   <li>Notes before the downbeat make whole pickup bars: everything is
 *       shifted so the earliest note lands in bar 1.</li>
 *   <li>Two notes snapping to the same onset keep the longer one.</li>
 *   <li>A release is cut back to the next onset (a monophonic line), and
 *       every note keeps at least one grid step.</li>
 * </ul>
 */
public final class GridQuantizer {

    private GridQuantizer() {}

    public record Result(List<QuantizedNote> notes, int pickupBars, int dropped) {
        public Result { notes = List.copyOf(notes); }
    }

    public static Result quantize(List<TranscribedNote> notes, TimingSpec timing, double downbeatSec) {
        if (notes.isEmpty()) return new Result(List.of(), 0, 0);
        double stepMs = timing.wholeNoteMs() * timing.gridWholes();
        double originMs = downbeatSec * 1000.0;
        int perBar = timing.stepsPerBar();

        record Slot(long ks, long ke, TranscribedNote n) {}
        var sorted = new ArrayList<>(notes);
        sorted.sort(Comparator.comparingDouble(TranscribedNote::startMs));
        var slots = new ArrayList<Slot>();
        int dropped = 0;
        for (TranscribedNote n : sorted) {
            long ks = Math.round((n.startMs() - originMs) / stepMs);
            long ke = Math.round((n.endMs() - originMs) / stepMs);
            Slot s = new Slot(ks, ke, n);
            if (!slots.isEmpty() && slots.getLast().ks() == ks) {
                dropped++;
                if (n.durationMs() > slots.getLast().n().durationMs()) slots.set(slots.size() - 1, s);
                continue;
            }
            slots.add(s);
        }
        long minK = slots.getFirst().ks();
        int pickupBars = minK < 0 ? (int) Math.ceil((double) -minK / perBar) : 0;
        long shift = (long) pickupBars * perBar;

        double loudest = 0;
        for (Slot s : slots) loudest = Math.max(loudest, s.n().peakRms());

        var out = new ArrayList<QuantizedNote>(slots.size());
        for (int i = 0; i < slots.size(); i++) {
            Slot s = slots.get(i);
            long ks = s.ks() + shift, ke = s.ke() + shift;
            if (i + 1 < slots.size()) ke = Math.min(ke, slots.get(i + 1).ks() + shift);
            if (ke <= ks) ke = ks + 1;
            int bar = (int) (ks / perBar) + 1;
            double beat = 1 + (ks % perBar) * timing.gridWholes() * timing.timeSignature().beatValue();
            out.add(new QuantizedNote(timing.grid().times(ks), timing.grid().times(ke - ks), s.n().midi(),
                    s.n().cents(), velocity(s.n().peakRms(), loudest), bar, beat, s.n()));
        }
        return new Result(out, pickupBars, dropped);
    }

    /** 40–127 on a square-root curve of loudness relative to the loudest note. */
    static int velocity(double peak, double loudest) {
        if (loudest <= 0) return 80;
        double level = Math.max(0, Math.min(1, peak / loudest));
        return 40 + (int) Math.round(Math.sqrt(level) * 87);
    }
}
