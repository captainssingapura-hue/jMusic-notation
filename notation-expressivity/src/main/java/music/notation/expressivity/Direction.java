package music.notation.expressivity;

/**
 * Direction of a {@link HairpinSpan} — whether the loudness ramps up
 * (crescendo) or down (decrescendo) over the span.
 *
 * <p>The actual endpoint levels are <em>not</em> carried by the
 * hairpin; they are read at codec/render time from the bracketing
 * {@link VelocityChange}/{@link VolumeChange} set-points on the same
 * track. See {@link HairpinSpan} for the resolution rule.</p>
 */
public enum Direction {
    CRESCENDO,
    DECRESCENDO
}
