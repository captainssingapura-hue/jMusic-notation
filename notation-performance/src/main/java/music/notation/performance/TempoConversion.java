package music.notation.performance;

import java.util.List;
import java.util.Objects;

/**
 * Converts between millisecond positions and quarter-note positions
 * along a {@link TempoTrack}, treating tempo as piecewise-constant
 * between consecutive {@link TempoChange} events.
 *
 * <p>Used by sustain-pedal helpers ({@code AutoPedaling},
 * {@code PedalInjector}) to anchor pedal events to musical bar
 * boundaries even under rubato or accelerando — replacing the older
 * single-bpm approximation that drifted across the piece.</p>
 *
 * <p>Default bpm: an empty {@link TempoTrack}, or any region before
 * the first {@link TempoChange}, is treated as {@value #DEFAULT_BPM}
 * bpm — matching the runtime default elsewhere in the codec.</p>
 */
public final class TempoConversion {

    /** Fallback bpm when no {@link TempoChange} covers a region. */
    public static final int DEFAULT_BPM = 120;

    private TempoConversion() {}

    /**
     * Convert a millisecond position to a quarter-note position by
     * integrating the piecewise-constant bpm of {@code tempos}.
     *
     * @return total quarter notes elapsed at {@code ms}; 0 when {@code ms <= 0}
     */
    public static double msToQuarters(TempoTrack tempos, long ms) {
        Objects.requireNonNull(tempos, "tempos");
        if (ms <= 0) return 0.0;

        List<TempoChange> changes = tempos.changes();
        if (changes.isEmpty()) {
            return ms * (double) DEFAULT_BPM / 60_000.0;
        }

        double quarters = 0.0;
        long cursor = 0;
        int currentBpm = DEFAULT_BPM;

        for (TempoChange ch : changes) {
            long boundary = ch.tickMs();
            if (boundary > cursor) {
                long segEnd = Math.min(boundary, ms);
                quarters += (segEnd - cursor) * (double) currentBpm / 60_000.0;
                if (segEnd >= ms) return quarters;
                cursor = boundary;
            }
            currentBpm = ch.bpm();
        }
        // Tail segment from the last change onward.
        quarters += (ms - cursor) * (double) currentBpm / 60_000.0;
        return quarters;
    }

    /**
     * Convert a quarter-note position to a millisecond position by
     * walking the piecewise-constant bpm timeline of {@code tempos}.
     * Inverse of {@link #msToQuarters(TempoTrack, long)} (modulo
     * rounding).
     *
     * @return ms elapsed at {@code quarters}; 0 when {@code quarters <= 0}
     */
    public static long quartersToMs(TempoTrack tempos, double quarters) {
        Objects.requireNonNull(tempos, "tempos");
        if (quarters <= 0) return 0L;

        List<TempoChange> changes = tempos.changes();
        if (changes.isEmpty()) {
            return Math.round(quarters * 60_000.0 / DEFAULT_BPM);
        }

        double ms = 0.0;
        double remaining = quarters;
        long cursor = 0;
        int currentBpm = DEFAULT_BPM;

        for (TempoChange ch : changes) {
            long boundary = ch.tickMs();
            if (boundary > cursor) {
                long segDurationMs = boundary - cursor;
                double segQuarters = segDurationMs * (double) currentBpm / 60_000.0;
                if (segQuarters >= remaining) {
                    ms += remaining * 60_000.0 / currentBpm;
                    return Math.round(ms);
                }
                remaining -= segQuarters;
                ms += segDurationMs;
                cursor = boundary;
            }
            currentBpm = ch.bpm();
        }
        // Tail segment at the last bpm — runs to infinity.
        ms += remaining * 60_000.0 / currentBpm;
        return Math.round(ms);
    }

    /**
     * Convert a millisecond position to MIDI ticks at resolution
     * {@code ppq}, accounting for tempo changes along the way.
     */
    public static long msToTicks(TempoTrack tempos, long ms, int ppq) {
        if (ppq <= 0) throw new IllegalArgumentException("ppq must be > 0: " + ppq);
        return Math.round(msToQuarters(tempos, ms) * ppq);
    }
}
