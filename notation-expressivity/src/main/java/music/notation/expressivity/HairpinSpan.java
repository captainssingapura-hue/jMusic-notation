package music.notation.expressivity;

import music.notation.duration.Duration;

import java.util.Objects;

/**
 * A single hairpin span — a continuous loudness ramp over a musical
 * interval. Carries <em>direction</em> and <em>span</em> only; the
 * actual endpoint levels are read at codec/render time from the
 * bracketing {@link VelocityChange}/{@link VolumeChange} set-points
 * on the same track.
 *
 * <h2>Resolution rule</h2>
 * <ul>
 *   <li><b>Start level</b> = most recent discrete loudness set-point
 *       at or before {@link #from()}, or codec default when none.</li>
 *   <li><b>End level</b> = discrete loudness set-point at or just
 *       after {@link #to()}, or one notch in {@link #direction()}
 *       from the start when none.</li>
 *   <li>Discrete marks <em>inside</em> the span are waypoints — they
 *       pin the level at their position; the hairpin keeps ramping
 *       toward the end target between them.</li>
 *   <li>The discrete loudness timeline is the <em>single source of
 *       truth</em>. Hairpins are gap-fillers; where they coexist with
 *       a discrete mark at a position, the discrete value wins.</li>
 * </ul>
 *
 * <p>{@link Direction#CRESCENDO}'s end level <em>must</em> be greater
 * than its start level (and vice versa for {@code DECRESCENDO}) for
 * the hairpin to be musically coherent — a contradictory direction
 * (e.g., {@code <} from {@code p} to {@code pp}) is honoured at the
 * discrete level (the marks win) and the parser logs a warning.</p>
 */
public record HairpinSpan(Duration from, Duration to, Direction direction) {
    public HairpinSpan {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(direction, "direction");
        if (from.compareDuration(Duration.zero()) < 0) {
            throw new IllegalArgumentException("from must be >= 0: " + from);
        }
        if (to.compareDuration(from) <= 0) {
            throw new IllegalArgumentException(
                    "to must be > from: from=" + from + ", to=" + to);
        }
    }
}
