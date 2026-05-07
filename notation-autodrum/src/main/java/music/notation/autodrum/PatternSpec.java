package music.notation.autodrum;

import music.notation.duration.BaseValue;
import music.notation.event.PercussionSound;

import java.util.Arrays;
import java.util.Objects;

/**
 * Declarative drum pattern spec — a fixed subdivision unit ({@link #unit})
 * plus a per-slot sequence of percussion sounds ({@link #sequence}). A
 * {@code null} entry denotes a rest at that subdivision.
 *
 * <p>Drum bars in this DSL are sequential (no simultaneous hits in a
 * single track), so each slot carries one sound at most. Strategies pick
 * the dominant element per subdivision — e.g. kick on the down-beat,
 * hi-hat on the off-beat.</p>
 *
 * <p>Optional {@link #slotVelocities} attaches a velocity (1..127) to
 * each non-rest slot — kick on the back-beat hits at vel ~110, ghost
 * snares at vel ~50, etc. {@code null} means "no opinion" — the
 * playback path falls back to the codec's default velocity. When
 * non-null, the array length must match {@link #sequence}; entries at
 * rest slots are ignored.</p>
 */
public record PatternSpec(BaseValue unit,
                          PercussionSound[] sequence,
                          int[] slotVelocities) {

    public PatternSpec {
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(sequence, "sequence");
        sequence = sequence.clone();   // defensive copy — record is value-typed
        if (slotVelocities != null) {
            if (slotVelocities.length != sequence.length) {
                throw new IllegalArgumentException(
                        "slotVelocities length " + slotVelocities.length
                                + " must match sequence length " + sequence.length);
            }
            for (int v : slotVelocities) {
                if (v < 1 || v > 127) {
                    throw new IllegalArgumentException(
                            "slot velocity must be in [1,127]: " + v);
                }
            }
            slotVelocities = slotVelocities.clone();
        }
    }

    /**
     * Backwards-compat: build a spec without per-slot velocities. Notes
     * play at the codec's default velocity.
     */
    public PatternSpec(BaseValue unit, PercussionSound[] sequence) {
        this(unit, sequence, null);
    }

    public int slots() { return sequence.length; }

    /** True when this spec carries explicit per-slot velocities. */
    public boolean hasVelocities() { return slotVelocities != null; }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof PatternSpec p
                && p.unit == unit
                && Arrays.equals(p.sequence, sequence)
                && Arrays.equals(p.slotVelocities, slotVelocities);
    }

    @Override
    public int hashCode() {
        int h = 31 * unit.hashCode() + Arrays.hashCode(sequence);
        return 31 * h + Arrays.hashCode(slotVelocities);
    }

    @Override
    public String toString() {
        return "PatternSpec[unit=" + unit
                + ", sequence=" + Arrays.toString(sequence)
                + (slotVelocities == null ? "" : ", velocities=" + Arrays.toString(slotVelocities))
                + "]";
    }
}
