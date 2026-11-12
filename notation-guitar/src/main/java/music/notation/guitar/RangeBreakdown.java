package music.notation.guitar;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Cuts a {@link Fingering} into whole-bar {@link PracticeRange}s
 * according to a {@link BreakdownSpec}. Ranges never exceed
 * {@link BreakdownSpec#maxBars()} and cover only bars in which a note starts —
 * silent bars are left out, so every range loops tight.
 */
public final class RangeBreakdown {

    private RangeBreakdown() {}

    public static List<PracticeRange> split(Fingering fingering, BarRange bars, Meter meter, BreakdownSpec spec) {
        List<FingeredNote> notes = fingering.notes();
        if (notes.isEmpty()) return List.of();

        // Bars (1-based) at which a new range must start.
        TreeSet<Integer> starts = new TreeSet<>();
        starts.add(bars.fromBar());
        switch (spec.mode()) {
            case BAR -> { for (int b = bars.fromBar(); b <= bars.toBar(); b++) starts.add(b); }
            case PHRASE -> {
                for (int i = 1; i < notes.size(); i++) {
                    FingeredNote prev = notes.get(i - 1), cur = notes.get(i);
                    double gapBeats = meter.toBeats(cur.at().minus(prev.endAt()));
                    if (gapBeats >= spec.phraseGapBeats()) starts.add(cur.bar());
                }
            }
            case POSITION -> {
                for (int i = 1; i < notes.size(); i++) {
                    if (notes.get(i).handPosition() != notes.get(i - 1).handPosition()) starts.add(notes.get(i).bar());
                }
            }
        }

        // Cap every range at maxBars.
        List<Integer> ordered = new ArrayList<>(starts);
        List<BarRange> ranges = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            int from = ordered.get(i);
            int to = i + 1 < ordered.size() ? ordered.get(i + 1) - 1 : bars.toBar();
            for (int f = from; f <= to; f += spec.maxBars()) {
                ranges.add(new BarRange(f, Math.min(to, f + spec.maxBars() - 1)));
            }
        }

        // Attach notes; trim each range to the bars that actually sound, drop the silent ones.
        List<PracticeRange> out = new ArrayList<>();
        for (BarRange r : ranges) {
            List<FingeredNote> in = notes.stream().filter(n -> r.contains(n.bar())).toList();
            if (in.isEmpty()) continue;
            BarRange trimmed = new BarRange(
                    in.stream().mapToInt(FingeredNote::bar).min().orElseThrow(),
                    in.stream().mapToInt(FingeredNote::bar).max().orElseThrow());
            out.add(PracticeRange.of(out.size() + 1, trimmed, in, meter));
        }
        return out;
    }
}
