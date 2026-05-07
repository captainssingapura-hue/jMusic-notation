package music.notation.performance;

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
 *   <li>Base velocity {@link VelocityControl#DEFAULT_VELOCITY} (90 = mf).</li>
 *   <li>Beat 1 of each bar gets {@code +5} ({@value #DOWNBEAT_ACCENT}).</li>
 *   <li>The "halfway" beat in even meters of 4 or more beats per bar
 *       gets {@code +3} ({@value #MIDBAR_ACCENT}). 4/4 → beat 3;
 *       6/8 (six-beat counting) → beat 4; etc.</li>
 *   <li>Each note gets {@code ±2} ({@value #JITTER}) deterministic
 *       jitter based on its tickMs — repeatable playback, not
 *       mechanical.</li>
 *   <li>Drum tracks are skipped — auto-drum strategies own per-hit
 *       velocity.</li>
 * </ul>
 *
 * <p>Bar / beat positions are walked through {@link TempoConversion}
 * against the performance's own {@link TempoTrack}, so accelerandi
 * and tempo set-points don't shift accent positions.</p>
 */
public final class AutoVelocity {

    /** Base for all generated velocities. */
    public static final int BASE_VELOCITY = VelocityControl.DEFAULT_VELOCITY;

    /** Velocity bump on beat 1 of each bar. */
    public static final int DOWNBEAT_ACCENT = 5;

    /** Velocity bump on the mid-bar accent beat (when applicable). */
    public static final int MIDBAR_ACCENT = 3;

    /** Half-range of deterministic per-note jitter. */
    public static final int JITTER = 2;

    private AutoVelocity() {}

    /**
     * Generate a baseline velocity timeline. Returns
     * {@link Velocities#empty()} when the performance is empty, has no
     * pitched tracks, or {@code ts} is null.
     */
    public static Velocities generate(Performance performance, TimeSignature ts) {
        if (performance == null || ts == null) return Velocities.empty();
        if (performance.score().tracks().isEmpty()) return Velocities.empty();

        double quartersPerBar = ts.beats() * 4.0 / ts.beatValue();
        if (quartersPerBar <= 0) return Velocities.empty();
        double quartersPerBeat = 4.0 / ts.beatValue();
        int beatsPerBar = ts.beats();
        int midbarBeat = (beatsPerBar >= 4 && beatsPerBar % 2 == 0)
                ? beatsPerBar / 2 + 1 : -1;
        TempoTrack tempos = performance.tempo();

        Map<TrackId, VelocityControl> map = new LinkedHashMap<>();
        for (Track t : performance.score().tracks()) {
            if (t.kind() != TrackKind.PITCHED) continue;

            List<VelocityChange> changes = new ArrayList<>();
            for (ConcreteNote n : t.notes()) {
                long ms = n.tickMs();
                double quartersTotal = TempoConversion.msToQuarters(tempos, ms);
                double quartersInBar = quartersTotal
                        - Math.floor(quartersTotal / quartersPerBar) * quartersPerBar;
                int beatNum = (int) Math.floor(quartersInBar / quartersPerBeat) + 1;

                int v = BASE_VELOCITY;
                if (beatNum == 1)            v += DOWNBEAT_ACCENT;
                else if (beatNum == midbarBeat) v += MIDBAR_ACCENT;
                v += deterministicJitter(ms);
                v = Math.max(1, Math.min(127, v));

                changes.add(new VelocityChange(ms, v));
            }

            if (!changes.isEmpty()) {
                VelocityControl ctrl = new VelocityControl(changes);
                if (!ctrl.changes().isEmpty()) map.put(t.id(), ctrl);
            }
        }
        return map.isEmpty() ? Velocities.empty() : new Velocities(map);
    }

    /**
     * Cheap deterministic ±{@link #JITTER} based on the tickMs. Uses
     * Knuth's multiplicative hash so the same tickMs always yields the
     * same jitter — playback is bit-identical across runs.
     */
    private static int deterministicJitter(long ms) {
        long mix = (ms * 2654435761L) & 0xFFFFFFFFL;
        int range = JITTER * 2 + 1;
        return (int) (mix % range) - JITTER;
    }
}
