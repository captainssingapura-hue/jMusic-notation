package music.notation.expressivity;

/**
 * A single velocity set-point on a track, anchored at a millisecond
 * tick. Maps to the MIDI {@code NOTE_ON} velocity byte for any note
 * starting at or after {@code tickMs} until the next change.
 *
 * <p>Velocity range 1..127. Zero is illegal — MIDI uses
 * {@code NOTE_ON, vel=0} as a synonym for {@code NOTE_OFF}, and the
 * codec emits {@code NOTE_OFF} explicitly. Forbidding 0 here avoids
 * surprise rest-events when a velocity timeline is consulted at
 * emission.</p>
 */
public record VelocityChange(long tickMs, int velocity) {
    public VelocityChange {
        if (tickMs < 0)
            throw new IllegalArgumentException("tickMs must be >= 0: " + tickMs);
        if (velocity < 1 || velocity > 127)
            throw new IllegalArgumentException("velocity must be in [1,127]: " + velocity);
    }
}
