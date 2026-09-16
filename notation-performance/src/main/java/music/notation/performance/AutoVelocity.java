package music.notation.performance;

import music.notation.duration.Duration;
import music.notation.expressivity.Velocities;
import music.notation.expressivity.VelocityChange;
import music.notation.expressivity.VelocityControl;
import music.notation.expressivity.TrackId;
import music.notation.structure.TimeSignature;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates a baseline {@link Velocities} side-channel for a
 * {@link Performance} that doesn't carry one of its own. Used as a
 * fallback when an MXL source omits {@code <dynamics>} markings, or
 * for DSL-authored library pieces — the playback gets a hint of
 * pulse without sounding mechanical.
 *
 * <p>Heuristic (V1):</p>
 * <ul>
 *   <li>Base level {@link VelocityControl#DEFAULT_LEVEL} (≈ mf).</li>
 *   <li>Beat 1 of each bar gets a downbeat bump ({@value #DOWNBEAT_ACCENT}).</li>
 *   <li>The "halfway" beat in even meters of 4 or more beats per bar
 *       gets a mid-bar bump ({@value #MIDBAR_ACCENT}). 4/4 → beat 3;
 *       6/8 (six-beat counting) → beat 4; etc.</li>
 *   <li>Each note gets ±{@value #JITTER} deterministic jitter based on
 *       its musical position — repeatable playback, not mechanical.</li>
 *   <li>Drum tracks are skipped — auto-drum strategies own per-hit
 *       velocity.</li>
 * </ul>
 *
 * <p>Beat positions derive from the time signature alone — pure
 * musical arithmetic, tempo-independent. Tempo edits don't shift
 * accent positions.</p>
 */
public final class AutoVelocity {

    /** Base level for all generated velocities (≈ mf). */
    public static final double BASE_LEVEL = VelocityControl.DEFAULT_LEVEL;

    /** Level bump on beat 1 of each bar (5/127 ≈ 0.0394). */
    public static final double DOWNBEAT_ACCENT = 5.0 / 127.0;

    /** Level bump on the mid-bar accent beat (3/127 ≈ 0.0236). */
    public static final double MIDBAR_ACCENT = 3.0 / 127.0;

    /** Half-range of deterministic per-note jitter (2/127 ≈ 0.0157). */
    public static final double JITTER = 2.0 / 127.0;

    private AutoVelocity() {}

    /**
     * Generate a baseline velocity timeline. Returns
     * {@link Velocities#empty()} when the performance is empty, has no
     * pitched tracks, or {@code ts} is null.
     */
    public static Velocities generate(Performance performance, TimeSignature ts) {
        if (performance == null || ts == null) return Velocities.empty();
        if (performance.score().tracks().isEmpty()) return Velocities.empty();

        // Bar length and beat length as musical Durations.
        // e.g. 4/4 → bar = 1 whole, beat = 1/4; 6/8 → bar = 6/8, beat = 1/8.
        Duration barDuration  = Duration.of(ts.beats(), ts.beatValue());
        Duration beatDuration = Duration.of(1, ts.beatValue());
        if (barDuration.isZero() || beatDuration.isZero()) return Velocities.empty();

        int beatsPerBar = ts.beats();
        int midbarBeat = (beatsPerBar >= 4 && beatsPerBar % 2 == 0)
                ? beatsPerBar / 2 + 1 : -1;

        Map<TrackId, VelocityControl> map = new LinkedHashMap<>();
        for (Track t : performance.score().tracks()) {
            if (t.kind() != TrackKind.PITCHED) continue;

            List<VelocityChange> changes = new ArrayList<>();
            for (ConcreteNote n : t.notes()) {
                Duration at = n.at();
                int beatNum = beatNumberWithinBar(at, barDuration, beatDuration);

                double v = BASE_LEVEL;
                if (beatNum == 1)                v += DOWNBEAT_ACCENT;
                else if (beatNum == midbarBeat) v += MIDBAR_ACCENT;
                v += deterministicJitter(at);
                v = Math.max(0.0, Math.min(1.0, v));

                changes.add(new VelocityChange(at, v));
            }

            if (!changes.isEmpty()) {
                VelocityControl ctrl = new VelocityControl(changes);
                if (!ctrl.changes().isEmpty()) map.put(t.id(), ctrl);
            }
        }
        return map.isEmpty() ? Velocities.empty() : new Velocities(map);
    }

    /**
     * Within-bar beat number (1-based) for a position {@code at}.
     * Computes via direct rational arithmetic:
     * {@code beat = floor((at mod bar) / beatLen) + 1}.
     */
    private static int beatNumberWithinBar(Duration at, Duration bar, Duration beat) {
        // at = k * bar + within  (find k and within)
        // For rational arithmetic, work with cross-multiplied integers:
        //   at = atN / atD, bar = barN / barD
        //   bars = (atN * barD) / (atD * barN)
        long atN = at.numerator();
        long atD = at.denominator();
        long barN = bar.numerator();
        long barD = bar.denominator();
        long fullBars = (atN * barD) / (atD * barN);
        Duration within = at.minus(bar.times(fullBars));

        // beatIndex = within / beat = (within.num * beat.den) / (within.den * beat.num)
        long beatIdx =
                (within.numerator() * beat.denominator())
                / (within.denominator() * beat.numerator());
        return (int) beatIdx + 1;
    }

    /**
     * Cheap deterministic ±{@link #JITTER} based on the note's
     * musical position. Uses Knuth's multiplicative hash on a stable
     * integer fingerprint of the rational so the same position always
     * yields the same jitter — playback is bit-identical across runs.
     */
    private static double deterministicJitter(Duration at) {
        long fingerprint = at.numerator() * 31L + at.denominator();
        long mix = (fingerprint * 2654435761L) & 0xFFFFFFFFL;
        // Map to integer in [-2, 2], then divide by 127 for level units.
        int range = 5; // 2 * 2 + 1
        int intJitter = (int) (mix % range) - 2;
        return intJitter / 127.0;
    }
}
