package music.notation.guitar;

import music.notation.performance.Performance;
import music.notation.performance.PitchedNote;
import music.notation.performance.Track;

import java.util.List;

/**
 * The pipeline: load the source, pick the track and bars, solve the
 * fingering, cut it into practice ranges.
 */
public final class Arranger {

    private Arranger() {}

    public static Arrangement arrange(ArrangementRequest request) {
        var loaded = SourceLoader.load(request.source());
        return arrange(request, loaded.title(), loaded.performance());
    }

    /** Same, for a performance already in hand (tests, apps). */
    public static Arrangement arrange(ArrangementRequest request, String title, Performance performance) {
        Track track = findTrack(performance, request.track());
        Meter meter = Meter.of(performance);

        List<PitchedNote> all = track.notes().stream()
                .filter(n -> n instanceof PitchedNote).map(n -> (PitchedNote) n).toList();
        if (all.isEmpty()) throw new IllegalArgumentException("track '" + track.id().name() + "' has no pitched notes");

        BarRange bars = request.bars() != null ? request.bars() : new BarRange(
                meter.barOf(all.get(0).at()) + 1,
                all.stream().mapToInt(n -> meter.barOf(n.at())).max().orElseThrow() + 1);
        List<PitchedNote> selected = all.stream().filter(n -> bars.contains(meter.barOf(n.at()) + 1)).toList();
        if (selected.isEmpty()) throw new IllegalArgumentException("no notes on '" + track.id().name() + "' in " + bars);

        Fingering fingering = new FingeringSolver(request.tuning(), request.hand(), request.weights(), meter).solve(selected);
        List<PracticeRange> ranges = RangeBreakdown.split(fingering, bars, meter, request.breakdown());
        return new Arrangement(request, title, track.id().name(), bars, meter, fingering, ranges);
    }

    /** Case-insensitive lookup; the error lists what is there. */
    public static Track findTrack(Performance performance, String name) {
        for (Track t : performance.score().tracks()) if (t.id().name().equalsIgnoreCase(name)) return t;
        StringBuilder known = new StringBuilder();
        for (Track t : performance.score().tracks()) {
            long pitched = t.notes().stream().filter(n -> n instanceof PitchedNote).count();
            known.append("\n  ").append(t.id().name()).append(" (").append(pitched).append(" notes)");
        }
        throw new IllegalArgumentException("no track named '" + name + "'. Tracks:" + known);
    }
}
