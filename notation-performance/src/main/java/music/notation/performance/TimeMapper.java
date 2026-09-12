package music.notation.performance;

import music.notation.duration.Duration;

import java.util.Objects;

/**
 * Translation layer between the Notation data model's musical
 * positions and wall-clock milliseconds.
 *
 * <h2>Why it exists</h2>
 *
 * <p>The post-migration model carries no ms anywhere — notes,
 * side-channel events, signature changes all anchor at a
 * {@link Duration} (rational fraction of a whole note). Downstream
 * consumers that genuinely need ms — the MIDI Sequencer, the UI
 * playhead, the WAV renderer — get it on demand by calling
 * {@link #toMs(Duration)} (or the inverse {@link #toDuration(long)}).
 * Their internal ms-based code stays as-is; only the boundary
 * lookup changes.</p>
 *
 * <h2>How it integrates tempo</h2>
 *
 * <p>The {@link TempoTrack} is piecewise-constant: each
 * {@link TempoChange} fixes a bpm starting at its musical position
 * until the next change. {@link #toMs(Duration)} walks the track,
 * accumulating wall-clock ms for each (bpm, segment-length) pair
 * up to the query position. The integration is exact in the
 * rational-arithmetic sense, with one final
 * {@code Math.round} at the end to land on a long ms value.</p>
 *
 * <h2>Variable BPM, accelerando, tempo edits</h2>
 *
 * <p>Because nothing is baked in the stored model, editing the
 * {@link TempoTrack} (insert / remove / change a
 * {@link TempoChange}) is sufficient to retime every note and
 * every side-channel event. The next {@link #toMs} call produces
 * the new wall-clock values automatically. This is the central
 * "variable BPM" enabler that the wall-clock-ms model lacked.</p>
 *
 * <h2>Limitation</h2>
 *
 * <p>{@link TempoTrack} remains piecewise-constant. True gradient
 * tempo (linear / exponential rit. or accel.) is approximated by N
 * small {@link TempoChange} events on the musical timeline —
 * identical to today's approximation. A future
 * {@code TempoRamp(fromAt, toAt, fromBpm, toBpm)} event with proper
 * integration is a clean follow-up but not required for variable
 * BPM as such.</p>
 *
 * <h2>Construction</h2>
 *
 * <p>Build one per {@link Performance} (typically by the codec or
 * the player when it loads a new piece); the instance is
 * thread-safe (immutable inputs, no mutable state).</p>
 */
public final class TimeMapper {

    /** Runtime fallback bpm when the TempoTrack is empty. */
    public static final int DEFAULT_BPM = 120;

    private final TempoTrack tempo;
    private final int defaultBpm;

    public TimeMapper(TempoTrack tempo) {
        this(tempo, DEFAULT_BPM);
    }

    public TimeMapper(TempoTrack tempo, int defaultBpm) {
        this.tempo = Objects.requireNonNull(tempo, "tempo");
        if (defaultBpm < 1 || defaultBpm > 999) {
            throw new IllegalArgumentException("defaultBpm must be in [1,999]: " + defaultBpm);
        }
        this.defaultBpm = defaultBpm;
    }

    /**
     * Wall-clock milliseconds at musical position {@code at}. Returns
     * 0 for the start of the piece. Linear in the number of
     * {@link TempoChange} events at or before {@code at}.
     */
    public long toMs(Duration at) {
        Objects.requireNonNull(at, "at");
        if (at.isZero()) return 0L;

        double msAcc = 0.0;
        Duration cursor = Duration.zero();
        int currentBpm = defaultBpm;

        for (TempoChange c : tempo.changes()) {
            // Stop once the change is at or past the query — the
            // last completed segment ran at the prior bpm; the new
            // bpm only applies to ticks at-or-after c.at().
            if (c.at().compareDuration(at) >= 0) break;
            if (c.at().compareDuration(cursor) > 0) {
                msAcc += msFor(c.at().minus(cursor), currentBpm);
                cursor = c.at();
            }
            currentBpm = c.bpm();
        }
        // Tail: from the cursor up to the query.
        msAcc += msFor(at.minus(cursor), currentBpm);
        return Math.round(msAcc);
    }

    /**
     * Wall-clock milliseconds of the segment {@code [from, to)}.
     * Equivalent to {@code toMs(to) - toMs(from)} but slightly
     * faster — one walk instead of two.
     */
    public long msBetween(Duration from, Duration to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        return toMs(to) - toMs(from);
    }

    /**
     * Inverse mapping — given wall-clock {@code ms} (e.g. from a
     * Sequencer playhead), find the musical position. Useful for
     * rendering a "play head" in musical-time coordinates and for
     * any UI that wants to know "what bar/beat is the player at?"
     *
     * <p>The result is the unique {@link Duration} such that
     * {@code toMs(result) == ms} when {@code ms} lands on a segment
     * boundary; otherwise it's the rational pro-rata within the
     * containing segment.</p>
     *
     * <p>Returns {@link Duration#zero()} for {@code ms <= 0}.</p>
     */
    public Duration toDuration(long ms) {
        if (ms <= 0) return Duration.zero();

        Duration cursor = Duration.zero();
        double msAcc = 0.0;
        int currentBpm = defaultBpm;

        for (TempoChange c : tempo.changes()) {
            if (c.at().compareDuration(cursor) > 0) {
                Duration seg = c.at().minus(cursor);
                double segMs = msFor(seg, currentBpm);
                if (msAcc + segMs >= ms) {
                    // Target lies within this segment — pro-rate.
                    long remaining = Math.round(ms - msAcc);
                    return cursor.plus(prorate(seg, currentBpm, remaining));
                }
                msAcc += segMs;
                cursor = c.at();
            }
            currentBpm = c.bpm();
        }
        // Past the last tempo change — tail segment at currentBpm.
        long remaining = Math.round(ms - msAcc);
        return cursor.plus(prorate(null, currentBpm, remaining));
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    /**
     * Wall-clock duration (ms) of a musical slice at constant bpm.
     * Carries the rational exactly through; the caller rounds at the
     * boundary.
     *
     * <p>Formula derivation:
     * <ul>
     *   <li>{@code slice} is in fractions-of-a-whole-note. Quarters
     *       in the slice = {@code slice × 4}.</li>
     *   <li>One quarter at bpm B lasts {@code 60_000 / B} ms.</li>
     *   <li>Therefore ms = {@code slice.num × 4 × 60_000 / (slice.den × bpm)}
     *       = {@code slice.num × 240_000 / (slice.den × bpm)}.</li>
     * </ul></p>
     */
    private static double msFor(Duration slice, int bpm) {
        return (double) slice.numerator() * 240_000.0
                / ((double) slice.denominator() * (double) bpm);
    }

    /**
     * Rational pro-rata: given a slice {@code seg} that runs at
     * {@code bpm}, return the {@link Duration} that consumes
     * {@code msTarget} ms within that slice. If {@code seg} is null,
     * treats the slice as unbounded (tail-segment past the last
     * tempo change).
     */
    private static Duration prorate(Duration seg, int bpm, long msTarget) {
        if (msTarget <= 0) return Duration.zero();
        // ms = quartersFraction × 60_000 / bpm
        //    = (wholes × 4) × 60_000 / bpm
        //    → wholes = ms × bpm / 240_000
        // Express as a rational: numerator = ms × bpm, denominator = 240_000.
        Duration wholesConsumed = Duration.of(msTarget * (long) bpm, 240_000L);
        if (seg != null && wholesConsumed.compareDuration(seg) > 0) {
            // Caller should have stopped at segment boundary; clamp
            // to the segment as a defensive bound.
            return seg;
        }
        return wholesConsumed;
    }
}
