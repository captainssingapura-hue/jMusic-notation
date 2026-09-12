package music.notation.performance;

import music.notation.duration.Duration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Re-times pairs of consecutive notes within each track to give a swung
 * (long–short) feel. Applied as a {@link Performance} transformation:
 * input is a clean grid of even notes, output has every (0,1), (2,3), …
 * pair re-weighted so the first note holds for {@code longRatio} of the
 * pair's total duration and the second note picks up the remainder.
 *
 * <p>Common ratios:</p>
 * <ul>
 *   <li>{@link #TRIPLET} = 2/3 — classic jazz/blues triplet feel
 *       (long is twice as long as short).</li>
 *   <li>{@link #SHUFFLE} = 0.60 — lighter shuffle.</li>
 *   <li>{@link #NONE} = 0.50 — no swing (identity).</li>
 * </ul>
 *
 * <p>Pairing is by note index within each track. Tracks with an odd note
 * count leave their final note untouched. Tempo, instruments, and
 * articulations pass through unchanged.</p>
 *
 * <p>Post-ms→Duration: pair durations are computed as Durations and
 * the long-fraction is expressed via {@link Duration#times(long)} +
 * {@link Duration#dividedBy(long)}. We snap to a 64ths grid via
 * {@code RawDuration} arithmetic to keep equality stable.</p>
 */
public final class Swing {

    public static final double TRIPLET = 2.0 / 3.0;
    public static final double SHUFFLE = 0.60;
    public static final double NONE    = 0.50;

    /** Granularity to which swung positions get snapped. 1/96 = a triplet-32nd. */
    private static final long SWING_GRID_DENOM = 96;

    private Swing() {}

    /**
     * Apply swing to every track of {@code performance}. The first note
     * of each pair gets {@code longRatio} of the pair's total duration;
     * the second note gets the remainder, with its onset slid right.
     *
     * @param longRatio fraction of pair duration for the long (first) note;
     *                  must be in {@code [0.5, 1.0]}
     */
    public static Performance apply(Performance performance, double longRatio) {
        if (longRatio < 0.5 || longRatio > 1.0) {
            throw new IllegalArgumentException(
                    "longRatio must be in [0.5, 1.0]: " + longRatio);
        }
        if (longRatio == NONE) return performance;  // identity

        var newTracks = new ArrayList<Track>(performance.score().tracks().size());
        for (Track t : performance.score().tracks()) {
            newTracks.add(swingTrack(t, longRatio));
        }
        return new Performance(
                new Score(newTracks),
                performance.tempo(),
                performance.instruments(),
                performance.articulations());
    }

    private static Track swingTrack(Track t, double longRatio) {
        List<ConcreteNote> in = t.notes();
        var out = new ArrayList<ConcreteNote>(in.size());

        for (int i = 0; i < in.size(); i++) {
            ConcreteNote first = in.get(i);
            if (i + 1 < in.size()) {
                ConcreteNote second = in.get(i + 1);
                Duration pairStart = first.at();
                Duration pairEnd   = second.endAt();
                Duration pairDur   = pairEnd.minus(pairStart);

                Duration newFirstDur = scaleByRatio(pairDur, longRatio);
                if (newFirstDur.isZero()) newFirstDur = Duration.of(1, SWING_GRID_DENOM);
                Duration newSecondStart = pairStart.plus(newFirstDur);
                Duration newSecondDur = pairEnd.minus(newSecondStart);
                if (newSecondDur.compareDuration(Duration.zero()) <= 0) {
                    newSecondDur = Duration.of(1, SWING_GRID_DENOM);
                }

                out.add(retick(first, pairStart, newFirstDur));
                out.add(retick(second, newSecondStart, newSecondDur));
                i++; // consume the pair
            } else {
                out.add(first);  // odd tail — untouched
            }
        }
        return new Track(t.id(), t.kind(), out);
    }

    /**
     * Scale a Duration by a double ratio, snapped onto a 1 / {@link
     * #SWING_GRID_DENOM} grid for value-stable equality. We do the
     * round at the boundary so swung durations stay rational.
     */
    private static Duration scaleByRatio(Duration dur, double ratio) {
        // Express dur in grid-units, then multiply.
        long durInGrid =
                Math.round((double) dur.numerator() * SWING_GRID_DENOM
                           / (double) dur.denominator());
        long scaledInGrid = Math.round(durInGrid * ratio);
        return Duration.of(scaledInGrid, SWING_GRID_DENOM);
    }

    private static ConcreteNote retick(ConcreteNote n, Duration newAt, Duration newDur) {
        return switch (n) {
            case PitchedNote p -> new PitchedNote(newAt, newDur, p.midi());
            // For ShiftedNote we re-tick the inner original and preserve the
            // wrap — Swing is a timing transform, never a pitch transform.
            case ShiftedNote s -> new ShiftedNote(
                    new PitchedNote(newAt, newDur,
                                     s.original().midi(), s.original().tiedToNext()),
                    s.semitoneShift());
            case DrumNote d    -> new DrumNote(newAt, newDur, d.piece());
        };
    }

    /** Helper for use in side-channel pass-through (not currently needed). */
    @SuppressWarnings("unused")
    private static <K, V> Map<K, V> copy(Map<K, V> in) {
        return new LinkedHashMap<>(in);
    }
}
