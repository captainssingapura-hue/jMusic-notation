package music.notation.performance;

import music.notation.duration.Duration;
import music.notation.event.Step;
import music.notation.phrase.Tieable;

import java.util.Objects;

/**
 * A melodic note carrying only structural content (musical position +
 * length + pitch). Rendering selects the program / timbre from the
 * {@link Instrumentation} side-channel for the note's owning track.
 *
 * <p>The {@link #pitch} payload is a {@link Pitch} sum type so authored
 * spellings ({@link Pitch.Spelled}) survive engraver round-trip while
 * computed/sampled values stay numeric ({@link Pitch.RawMidi}). The
 * model carries no bare MIDI byte; the codec calls {@link #midi()} at
 * the MIDI boundary (which delegates to {@code pitch.midi()}).</p>
 *
 * <p>Implements {@link Tieable}: the {@code tiedToNext} flag is
 * intrinsic and survives JSON round-trip. Codec-level coalescing on
 * MIDI emission keeps the tied chain as a single audible sound.</p>
 *
 * <p>Backwards-compat constructors accept a plain {@code int midi}
 * (wrapped as {@link Pitch.RawMidi}) so existing callers from before
 * the sum-type refactor keep working unchanged.</p>
 */
public record PitchedNote(Duration at, Duration duration, Pitch pitch, boolean tiedToNext)
        implements PitchedLike, Tieable {

    public PitchedNote {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(duration, "duration");
        Objects.requireNonNull(pitch, "pitch");
        if (at.compareDuration(Duration.zero()) < 0) {
            throw new IllegalArgumentException("at must be >= 0: " + at);
        }
        if (duration.compareDuration(Duration.zero()) <= 0) {
            throw new IllegalArgumentException("duration must be > 0: " + duration);
        }
    }

    /** Backwards-compat ctor: wraps {@code midi} as {@link Pitch.RawMidi}. */
    public PitchedNote(Duration at, Duration duration, int midi, boolean tiedToNext) {
        this(at, duration, Pitch.of(midi), tiedToNext);
    }

    /** Backwards-compat ctor: defaults {@code tiedToNext} to {@code false} and wraps {@code midi}. */
    public PitchedNote(Duration at, Duration duration, int midi) {
        this(at, duration, Pitch.of(midi), false);
    }

    /** Convenience: defaults {@code tiedToNext} to {@code false} with an explicit {@link Pitch}. */
    public PitchedNote(Duration at, Duration duration, Pitch pitch) {
        this(at, duration, pitch, false);
    }

    /** Convenience: a spelled note at the start of the track. */
    public static PitchedNote spelled(Step step, int alter, int octave, Duration duration) {
        return new PitchedNote(Duration.zero(), duration, Pitch.of(step, alter, octave), false);
    }

    /** Convenience: a raw-MIDI note at the start of the track. */
    public static PitchedNote of(int midi, Duration duration) {
        return new PitchedNote(Duration.zero(), duration, Pitch.of(midi), false);
    }

    /**
     * Resolved 12-TET MIDI byte. Equivalent to {@code pitch().midi()};
     * provided as a convenience so existing call sites that read
     * {@code pn.midi()} compile unchanged.
     */
    public int midi() { return pitch.midi(); }

    @Override
    public PitchedNote withTiedToNext() {
        return new PitchedNote(at, duration, pitch, true);
    }
}
