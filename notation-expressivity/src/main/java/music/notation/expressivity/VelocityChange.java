package music.notation.expressivity;

import music.notation.duration.Duration;

import java.util.Objects;

/**
 * A single per-note loudness set-point on a track, anchored at a
 * musical position from the start of the owning track. The
 * {@link #loudness} payload is a {@link Loudness} sum type so authored
 * symbolic marks survive round-trip ({@link Loudness.Named}) while
 * computed/sampled values stay numeric ({@link Loudness.Raw}). The
 * model carries no MIDI velocity byte (a synth-specific 7-bit value).
 *
 * <p>Output mapping at the codec boundary:</p>
 * <ul>
 *   <li>MIDI {@code NOTE_ON velocity = round(loudness.level() × 127)}
 *       clamped to {@code [1, 127]} (zero is illegal — MIDI uses
 *       {@code NOTE_ON, vel=0} as a NOTE_OFF synonym, and the codec
 *       emits NOTE_OFF explicitly).</li>
 *   <li>Score engraver: pattern-match on {@code Named} → glyph;
 *       {@code Raw} → numeric annotation or nearest-name
 *       approximation.</li>
 *   <li>Physical-modelling / sampler synths: scale {@link #level()}
 *       into their own attack-velocity range.</li>
 * </ul>
 *
 * <p>{@code Raw(0.0)} is permitted (silence-attack); the codec floors
 * it to MIDI 1 on emit so the model never produces a
 * NOTE_OFF-disguised-as-NOTE_ON.</p>
 */
public record VelocityChange(Duration at, Loudness loudness) {
    public VelocityChange {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(loudness, "loudness");
        if (at.compareDuration(Duration.zero()) < 0) {
            throw new IllegalArgumentException("at must be >= 0: " + at);
        }
    }

    /** Convenience for {@code new VelocityChange(at, Loudness.of(level))}. */
    public VelocityChange(Duration at, double level) {
        this(at, Loudness.of(level));
    }

    /** Convenience for {@code new VelocityChange(at, Loudness.of(mark))}. */
    public VelocityChange(Duration at, music.notation.event.Dynamic mark) {
        this(at, Loudness.of(mark));
    }

    /** Resolved loudness in {@code [0.0, 1.0]}. */
    public double level() { return loudness.level(); }
}
