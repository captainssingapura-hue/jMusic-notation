package music.notation.guitar;

import java.util.List;
import java.util.Objects;

/**
 * The finished product: the request it answers, the piece and track it
 * was cut from, the bars covered, the fingering, and the practice ranges.
 */
public record Arrangement(ArrangementRequest request, String title, String track, BarRange bars,
                          Meter meter, Fingering fingering, List<PracticeRange> ranges) {

    public Arrangement {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(track, "track");
        Objects.requireNonNull(bars, "bars");
        Objects.requireNonNull(meter, "meter");
        Objects.requireNonNull(fingering, "fingering");
        ranges = List.copyOf(Objects.requireNonNull(ranges, "ranges"));
    }
}
