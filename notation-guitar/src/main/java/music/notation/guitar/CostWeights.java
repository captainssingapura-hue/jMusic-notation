package music.notation.guitar;

/**
 * Weights of the fingering cost function. All weights are non-negative
 * except {@code openString}, which may be negative to reward open strings.
 *
 * <ul>
 *   <li>{@code positionChange} — flat cost whenever the hand moves.</li>
 *   <li>{@code shift} — per fret the hand moves.</li>
 *   <li>{@code stringSkip} — per string skipped between consecutive
 *       notes (adjacent strings are free, so sweeps stay cheap).</li>
 *   <li>{@code highFret} — per fret above {@link HandConstraints#preferredMaxFret()}.</li>
 *   <li>{@code openString} — per open string used.</li>
 *   <li>{@code stretch} — per fret a chord's fretted span exceeds the hand span.</li>
 *   <li>{@code speedSensitivity}, {@code maxSpeedFactor} — transition costs
 *       are multiplied by {@code min(maxSpeedFactor, speedSensitivity / gapInBeats)},
 *       floored at 1: shifting between sixteenths costs more than between
 *       whole notes.</li>
 * </ul>
 */
public record CostWeights(double positionChange, double shift, double stringSkip, double highFret,
                          double openString, double stretch, double speedSensitivity, double maxSpeedFactor) {

    public static final CostWeights DEFAULT = new CostWeights(2.0, 1.0, 0.5, 0.75, 0.5, 2.0, 1.0, 4.0);

    public CostWeights {
        for (double v : new double[]{ positionChange, shift, stringSkip, highFret, stretch, speedSensitivity, maxSpeedFactor }) {
            if (v < 0 || Double.isNaN(v)) throw new IllegalArgumentException("weights must be non-negative");
        }
        if (Double.isNaN(openString)) throw new IllegalArgumentException("openString weight is NaN");
        if (maxSpeedFactor < 1) throw new IllegalArgumentException("maxSpeedFactor must be >= 1");
    }

    /** Multiplier for a transition whose onsets are {@code gapBeats} apart. */
    public double speedFactor(double gapBeats) {
        if (gapBeats <= 0) return maxSpeedFactor;
        return Math.max(1.0, Math.min(maxSpeedFactor, speedSensitivity / gapBeats));
    }
}
