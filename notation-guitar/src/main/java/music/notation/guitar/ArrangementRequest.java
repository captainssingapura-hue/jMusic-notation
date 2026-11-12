package music.notation.guitar;

import java.util.Objects;

/**
 * Everything the arranger needs, in one value. Only {@code source} and
 * {@code track} are required; the rest default to
 * {@link Tuning#STANDARD}, {@link HandConstraints#DEFAULT},
 * {@link CostWeights#DEFAULT}, {@link BreakdownSpec#DEFAULT} and the
 * whole track.
 *
 * <pre>{@code
 * {
 *   "source":    { "type": "library", "title": "…", "variant": "Super Blue in B♭" },
 *   "track":     "Solo 1",
 *   "bars":      { "fromBar": 17, "toBar": 24 },
 *   "tuning":    { "name": "standard", "openStrings": [64,59,55,50,45,40], "capo": 0 },
 *   "hand":      { "maxFret": 22, "span": 4, "maxStretch": 5, "preferredMaxFret": 12,
 *                  "allowOpenStrings": true, "foldOutOfRange": false },
 *   "weights":   { "positionChange": 2, "shift": 1, "stringSkip": 0.5, "highFret": 0.75,
 *                  "openString": 0.5, "stretch": 2, "speedSensitivity": 1, "maxSpeedFactor": 4 },
 *   "breakdown": { "mode": "PHRASE", "maxBars": 2, "phraseGapBeats": 1.0 }
 * }
 * }</pre>
 */
public record ArrangementRequest(PieceSource source, String track, BarRange bars, Tuning tuning,
                                 HandConstraints hand, CostWeights weights, BreakdownSpec breakdown) {

    public ArrangementRequest {
        Objects.requireNonNull(source, "source");
        if (track == null || track.isBlank()) throw new IllegalArgumentException("track required");
        tuning    = tuning    == null ? Tuning.STANDARD         : tuning;
        hand      = hand      == null ? HandConstraints.DEFAULT : hand;
        weights   = weights   == null ? CostWeights.DEFAULT     : weights;
        breakdown = breakdown == null ? BreakdownSpec.DEFAULT   : breakdown;
    }

    public static ArrangementRequest of(PieceSource source, String track) {
        return new ArrangementRequest(source, track, null, null, null, null, null);
    }

    public ArrangementRequest withBars(BarRange range) {
        return new ArrangementRequest(source, track, range, tuning, hand, weights, breakdown);
    }
}
