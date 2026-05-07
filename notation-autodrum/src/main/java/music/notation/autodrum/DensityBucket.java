package music.notation.autodrum;

/**
 * Coarse melodic-density bucket. Strategies declare a {@link PatternSpec}
 * per (BarDuration, Energy, DensityBucket) so the drum response shapes
 * itself to what the melody is doing in each bar.
 */
public enum DensityBucket {
    /** Source bar contains only rests. Drum should mark the pulse softly. */
    EMPTY,
    /** Half-notes / dotted-quarters territory — drum can be busier without crowding. */
    SPARSE,
    /** Eighth-note territory — strategy's standard pattern. */
    STANDARD,
    /** Sixteenth-runs / ornament flurries — drum should thin out and yield. */
    DENSE
}
