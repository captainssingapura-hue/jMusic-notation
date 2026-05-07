package music.notation.autodrum;

import java.util.List;

/**
 * Per-bar melodic features — used by density-aware {@link DrumStrategy}
 * implementations to vary the drum pattern with the source.
 *
 * @param density             average non-rest notes per beat (across all
 *                            melody tracks). 0.0 = bar is rests; ~4.0 =
 *                            sixteenth-note runs in a quarter-note meter.
 * @param activeRatio         fraction of the bar covered by note durations,
 *                            [0.0, 1.0]. A whole-note bar has activeRatio
 *                            ≈ 1.0 even though density is low; a rest bar
 *                            has 0.0.
 * @param silent              true iff every melody track's bar contains
 *                            only rest nodes — convenience derived flag.
 * @param bassOnsetFractions  fractional bar positions [0.0, 1.0) where a
 *                            "bass" pitch (octave ≤ 3, i.e. below middle
 *                            C) attacks. Sorted, deduped. Used by
 *                            velocity-aware drum bakes to anchor the kick
 *                            against the source's bass line.
 */
public record BarFeatures(double density, double activeRatio, boolean silent,
                          List<Double> bassOnsetFractions) {

    public BarFeatures {
        if (density < 0)        throw new IllegalArgumentException("density < 0: " + density);
        if (activeRatio < 0 || activeRatio > 1.0001) {
            throw new IllegalArgumentException("activeRatio out of [0,1]: " + activeRatio);
        }
        bassOnsetFractions = (bassOnsetFractions == null)
                ? List.of()
                : List.copyOf(bassOnsetFractions);
    }

    /** Backwards-compat: defaults {@code bassOnsetFractions} to empty. */
    public BarFeatures(double density, double activeRatio, boolean silent) {
        this(density, activeRatio, silent, List.of());
    }

    /** Coarse density bucket — the unit of choice for strategies. */
    public DensityBucket bucket() {
        if (silent || density == 0.0) return DensityBucket.EMPTY;
        if (density <= 1.0)           return DensityBucket.SPARSE;
        if (density <= 3.0)           return DensityBucket.STANDARD;
        return DensityBucket.DENSE;
    }

    /**
     * True if a bass-onset lands within {@code tolerance} (fractional
     * bar units) of {@code slotFraction}. Used by the velocity bake to
     * boost kicks that line up with the source's bass attack.
     */
    public boolean bassOnsetNear(double slotFraction, double tolerance) {
        for (double f : bassOnsetFractions) {
            if (Math.abs(f - slotFraction) <= tolerance) return true;
        }
        return false;
    }
}
