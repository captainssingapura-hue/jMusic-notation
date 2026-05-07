package music.notation.performance;

import music.notation.duration.Duration;

import java.util.Set;
import java.util.TreeSet;

/**
 * Snaps fractional sixty-fourth-note durations to the nearest legal
 * notation value (whole, half, quarter, … down to sixty-fourth, plus
 * dotted variants).
 *
 * <p>Used when reading MIDI: real performances have off-grid timings
 * (a "quarter note" might be 247 ms instead of the ideal 250 ms at
 * 120 BPM). The quantizer rounds those to the nearest value that
 * the {@link music.notation.phrase.Bar} model can represent, so a
 * {@code Performance} can be re-expressed as a list of {@code Bar}s
 * without leftover fractional ticks.</p>
 *
 * <p>Operates in <em>sixty-fourths</em> (the system's atomic
 * sub-division). The legal set spans 1sf (sixty-fourth) → 64sf (whole)
 * with dotted variants at every standard subdivision.</p>
 *
 * <p>Stateless and thread-safe.</p>
 */
public final class Quantizer {

    private Quantizer() {}

    /**
     * The set of durations the {@link music.notation.phrase.Bar} model
     * can express directly — sixty-fourths through whole, plus their
     * dotted variants. Stored as sixty-fourths counts.
     *
     * <p>Sorted ascending. Used by {@link #snap(double)} for nearest-
     * neighbour lookup and by callers that need greedy decomposition
     * of arbitrary gaps (e.g. rest filling).</p>
     */
    public static final TreeSet<Integer> LEGAL_SIXTY_FOURTHS;
    static {
        var s = new TreeSet<Integer>();
        s.add(1);   // SIXTY_FOURTH
        s.add(2);   // THIRTY_SECOND
        s.add(3);   // THIRTY_SECOND.dot()
        s.add(4);   // SIXTEENTH
        s.add(6);   // SIXTEENTH.dot()
        s.add(8);   // EIGHTH
        s.add(12);  // EIGHTH.dot()
        s.add(16);  // QUARTER
        s.add(24);  // QUARTER.dot()
        s.add(32);  // HALF
        s.add(48);  // HALF.dot()
        s.add(64);  // WHOLE
        LEGAL_SIXTY_FOURTHS = s;
    }

    /** Read-only view for callers that don't need the {@code TreeSet} API. */
    public static Set<Integer> legalDurations() {
        return java.util.Collections.unmodifiableSet(LEGAL_SIXTY_FOURTHS);
    }

    /**
     * Snap a fractional sixty-fourths value to the nearest legal duration.
     *
     * <p>Tie-breaking: when {@code rawSf} sits exactly between two legal
     * values, the smaller value wins (favours the shorter note, which is
     * usually the safer choice — overshoots tend to displace the next
     * onset).</p>
     *
     * @param rawSf a non-negative duration measured in sixty-fourths.
     *              Values below 1 are pulled up to 1 (the smallest
     *              representable note); zero-length blips disappear.
     * @return one of the values in {@link #LEGAL_SIXTY_FOURTHS}.
     */
    public static int snap(double rawSf) {
        int sf = Math.max(1, (int) Math.round(rawSf));
        Integer floor = LEGAL_SIXTY_FOURTHS.floor(sf);
        Integer ceil  = LEGAL_SIXTY_FOURTHS.ceiling(sf);
        if (floor == null) return ceil != null ? ceil : sf;
        if (ceil == null) return floor;
        return (sf - floor <= ceil - sf) ? floor : ceil;
    }

    /**
     * Largest legal duration ≤ {@code sf}. Useful for greedy gap
     * decomposition (filling a 13sf rest as 12 + 1, etc.).
     *
     * @return the floor key, or {@code null} if {@code sf < 1}.
     */
    public static Integer floor(int sf) {
        return LEGAL_SIXTY_FOURTHS.floor(sf);
    }

    /** Whether {@code sf} is itself a legal duration (no rounding needed). */
    public static boolean isLegal(int sf) {
        return LEGAL_SIXTY_FOURTHS.contains(sf);
    }

    // ── Rational-aware snap (Phase 4) ────────────────────────────────

    /**
     * Snap a raw rational duration to the closest legal duration in
     * the supplied {@link QuantizerProfile}.
     *
     * <p>Closeness is measured by absolute value distance:
     * {@code |candidate - raw|} as a rational, evaluated as a
     * floating-point comparison for ordering. Ties favour the smaller
     * candidate (consistent with the legacy int-sf {@link #snap(double)}).</p>
     *
     * <p>Use this for MIDI imports where source timings may include
     * triplets, quintuplets, etc.: pass {@link QuantizerProfile#WITH_TRIPLETS}
     * for mainstream classical, {@link QuantizerProfile#FULL} for
     * Chopin/Liszt/jazz, or build a custom profile via
     * {@link QuantizerProfile#builder()}.</p>
     *
     * @param raw     a non-negative duration. Zero is mapped to the
     *                smallest legal duration in the profile.
     * @param profile the constraint profile (which durations are legal).
     * @return one of the durations in {@code profile.legalDurations()}.
     */
    public static Duration snap(Duration raw, QuantizerProfile profile) {
        var legals = profile.legalDurations();
        if (legals.isEmpty()) {
            throw new IllegalArgumentException("profile has no legal durations");
        }

        // Linear scan with floating-point distance — legal sets are <50 entries.
        // For each candidate, compute |candidate - raw| as a double, pick the
        // smallest. Ties favour the smaller candidate (stable order).
        Duration best = legals.get(0);
        double bestDist = Math.abs(asDouble(best) - asDouble(raw));
        for (int i = 1; i < legals.size(); i++) {
            Duration cand = legals.get(i);
            double dist = Math.abs(asDouble(cand) - asDouble(raw));
            if (dist < bestDist) {
                best = cand;
                bestDist = dist;
            }
            // No `<=` — earlier (smaller) candidate wins ties.
        }
        return best;
    }

    private static double asDouble(Duration d) {
        return (double) d.numerator() / d.denominator();
    }

    /**
     * Largest legal duration in {@code profile} whose value is
     * {@code <= max}. Returns {@code null} if {@code max} is smaller
     * than every legal duration (or is non-positive).
     *
     * <p>Useful for greedy decomposition of arbitrary rests / gaps
     * into legal sub-rests, the rational analogue of
     * {@link #floor(int)}.</p>
     */
    public static Duration floor(Duration max, QuantizerProfile profile) {
        if (max.numerator() <= 0) return null;
        var legals = profile.legalDurations();
        // legalDurations is sorted ascending — walk backwards for first <= max.
        for (int i = legals.size() - 1; i >= 0; i--) {
            Duration cand = legals.get(i);
            if (cand.compareDuration(max) <= 0) {
                return cand;
            }
        }
        return null;
    }

    /**
     * The finest position grid for the supplied {@link QuantizerProfile}
     * — {@code 1/N} where {@code N} is the LCM of every legal duration's
     * denominator.
     *
     * <p>Concrete values:
     * <ul>
     *   <li>{@link QuantizerProfile#STANDARD} → {@code 1/64}</li>
     *   <li>{@link QuantizerProfile#WITH_TRIPLETS} → {@code 1/192}</li>
     *   <li>{@link QuantizerProfile#FULL} → {@code 1/6720}
     *       (effectively continuous; harmless)</li>
     * </ul>
     *
     * <p>Onset positions snapped to multiples of this grid (via
     * {@link #snapToGrid}) eliminate the sub-quantum drift between
     * snapped durations and raw ms-derived onsets.</p>
     */
    public static Duration finestGrid(QuantizerProfile profile) {
        long n = 1;
        for (Duration d : profile.legalDurations()) {
            Duration c = d.canonical();
            n = lcm(n, c.denominator());
        }
        return Duration.of(1, n);
    }

    /**
     * Snap a non-negative gap to the nearest multiple of the profile's
     * {@link #finestGrid}. Sub-half-grid gaps round to zero.
     *
     * <p>Used by {@code BarBuilder} / {@code DrumBarBuilder} to align
     * onset positions to the profile grid before computing rest gaps.</p>
     */
    public static Duration snapToGrid(Duration gap, QuantizerProfile profile) {
        if (gap.numerator() <= 0) return Duration.of(0, 1);
        Duration grid = finestGrid(profile);
        // gap / grid = (gap.num * grid.den) / (gap.den * grid.num).
        // Round to nearest non-negative integer k.
        long num = gap.numerator()   * grid.denominator();
        long den = gap.denominator() * grid.numerator();
        long k   = (num + den / 2) / den;     // round half up (gap >= 0)
        if (k == 0) return Duration.of(0, 1);
        return grid.times(k);
    }

    private static long lcm(long a, long b) {
        return a / gcd(a, b) * b;
    }

    private static long gcd(long a, long b) {
        while (b != 0) { long t = b; b = a % b; a = t; }
        return a == 0 ? 1 : a;
    }
}
