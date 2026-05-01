package music.notation.performance;

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
 */
public final class Swing {

    public static final double TRIPLET = 2.0 / 3.0;
    public static final double SHUFFLE = 0.60;
    public static final double NONE    = 0.50;

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
                long pairStart = first.tickMs();
                long pairEnd = second.offTickMs();
                long pairDur = pairEnd - pairStart;

                long newFirstDur = Math.max(1L, Math.round(pairDur * longRatio));
                long newSecondStart = pairStart + newFirstDur;
                long newSecondDur = Math.max(1L, pairEnd - newSecondStart);

                out.add(retick(first, pairStart, newFirstDur));
                out.add(retick(second, newSecondStart, newSecondDur));
                i++; // consume the pair
            } else {
                out.add(first);  // odd tail — untouched
            }
        }
        return new Track(t.id(), t.kind(), out);
    }

    private static ConcreteNote retick(ConcreteNote n, long newTickMs, long newDurMs) {
        return switch (n) {
            case PitchedNote p -> new PitchedNote(newTickMs, newDurMs, p.midi());
            case DrumNote d    -> new DrumNote(newTickMs, newDurMs, d.piece());
        };
    }

    /** Helper for use in side-channel pass-through (not currently needed). */
    @SuppressWarnings("unused")
    private static <K, V> Map<K, V> copy(Map<K, V> in) {
        return new LinkedHashMap<>(in);
    }
}
