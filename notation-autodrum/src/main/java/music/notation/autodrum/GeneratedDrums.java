package music.notation.autodrum;

import music.notation.expressivity.VelocityControl;
import music.notation.structure.DrumTrack;

import java.util.Optional;

/**
 * Result bundle from a velocity-aware drum-strategy bake — the
 * {@link DrumTrack} plus a per-onset {@link VelocityControl} keyed to
 * the drum-track's MIDI channel.
 *
 * <p>{@code velocities} is empty when the strategy declined to opt in
 * (legacy strategies without {@link PatternSpec#slotVelocities}); the
 * caller treats that as "use the codec default." When non-empty, it
 * carries one entry per non-rest slot the strategy emitted, anchored
 * at the slot's tickMs in the source piece's tempo.</p>
 */
public record GeneratedDrums(DrumTrack track, VelocityControl velocities) {

    /** Wrap a plain {@link DrumTrack} with empty velocities. */
    public static GeneratedDrums of(DrumTrack track) {
        return new GeneratedDrums(track, VelocityControl.empty());
    }

    /** Same as {@link #of(DrumTrack)} but unwraps an {@link Optional}. */
    public static Optional<GeneratedDrums> wrap(Optional<DrumTrack> maybe) {
        return maybe.map(GeneratedDrums::of);
    }
}
