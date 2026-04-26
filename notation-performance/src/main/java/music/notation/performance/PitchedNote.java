package music.notation.performance;

import music.notation.phrase.Tieable;

/**
 * A melodic note carrying only structural content (timing and pitch).
 * Rendering selects the program (timbre) from the {@link Instrumentation} side-channel
 * for the note's owning track.
 *
 * <p>Implements {@link Tieable}: the {@code tiedToNext} flag is intrinsic and
 * survives JSON round-trip. Phase 1 declares the contract; the codec-level
 * coalescing on emission lands in a later phase.</p>
 */
public record PitchedNote(long tickMs, long durationMs, int midi, boolean tiedToNext)
        implements ConcreteNote, Tieable {

    public PitchedNote {
        if (tickMs < 0) throw new IllegalArgumentException("tickMs must be >= 0: " + tickMs);
        if (durationMs <= 0) throw new IllegalArgumentException("durationMs must be > 0: " + durationMs);
        if (midi < 0 || midi > 127) throw new IllegalArgumentException("midi must be in [0,127]: " + midi);
    }

    /** Backward-compat ctor: defaults {@code tiedToNext} to {@code false}. */
    public PitchedNote(long tickMs, long durationMs, int midi) {
        this(tickMs, durationMs, midi, false);
    }

    public static PitchedNote of(int midi, long durationMs) {
        return new PitchedNote(0, durationMs, midi, false);
    }

    @Override
    public PitchedNote withTiedToNext() {
        return new PitchedNote(tickMs, durationMs, midi, true);
    }
}
