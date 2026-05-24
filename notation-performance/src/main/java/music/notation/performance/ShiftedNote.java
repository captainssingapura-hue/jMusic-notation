package music.notation.performance;

import music.notation.phrase.Tieable;

import java.util.Objects;

/**
 * A pitched note in its <em>shifted</em> state: a reference to the
 * original {@link PitchedNote} plus the integer semitone shift applied
 * to it. {@link #midi()} returns the effective (shifted) MIDI value;
 * {@link #originalMidi()} recovers the authored value.
 *
 * <p>Used as the in-memory representation produced by
 * {@link TransposeTransform#apply(Performance,
 * TransposeTransform.Params)}. The UI's show-both pitch-scroll
 * rendering pattern-matches on {@code ShiftedNote} to draw a "ghost"
 * lane at the original pitch alongside the primary lane at the shifted
 * pitch. The MIDI codec reads {@link #midi()} polymorphically and
 * therefore treats {@code ShiftedNote} as a regular pitched note for
 * emission.</p>
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li>{@code original} is never itself a {@code ShiftedNote} — the
 *       record type guarantees this via the {@link PitchedNote}
 *       parameter type. Use {@link #of(PitchedLike, int)} to flatten
 *       when wrapping a {@code PitchedLike} that might already be
 *       shifted.</li>
 *   <li>The effective MIDI ({@code original.midi() + semitoneShift})
 *       is validated to lie in [0, 127] at construction. Callers that
 *       might produce out-of-range shifts should pre-check with
 *       {@link TransposeTransform#countOutOfRange(Performance, int)}
 *       or drop such notes before wrapping.</li>
 *   <li>{@code tickMs}, {@code durationMs}, and the {@code tiedToNext}
 *       flag are delegated to the wrapped original — the shift is a
 *       pitch transformation only.</li>
 * </ul>
 *
 * <h2>Round-trip semantics</h2>
 *
 * <p>{@code MidiCodec.fromMidi} does not reconstruct {@code ShiftedNote}
 * — MIDI bytes carry no notion of "shifted from an original," so
 * round-trip produces plain {@link PitchedNote}s at the emitted
 * (effective) values. Same flavour of lossiness as Articulations on
 * round-trip; documented in {@code Performance}.</p>
 */
public record ShiftedNote(PitchedNote original, int semitoneShift)
        implements PitchedLike, Tieable {

    public ShiftedNote {
        Objects.requireNonNull(original, "original");
        int effective = original.midi() + semitoneShift;
        if (effective < 0 || effective > 127) {
            throw new IllegalArgumentException(
                    "ShiftedNote effective midi out of range: "
                            + original.midi() + " + " + semitoneShift
                            + " = " + effective + " (MIDI requires [0, 127])");
        }
    }

    @Override public long tickMs()         { return original.tickMs(); }
    @Override public long durationMs()     { return original.durationMs(); }
    @Override public int  midi()           { return original.midi() + semitoneShift; }
    @Override public boolean tiedToNext()  { return original.tiedToNext(); }

    /** The original (pre-shift) MIDI value. Used by UI ghost-lane rendering. */
    public int originalMidi() { return original.midi(); }

    /** True when the shift is non-zero (an actual transposition). */
    public boolean isShifted() { return semitoneShift != 0; }

    /**
     * Factory that flattens nested shifts and short-circuits the
     * no-op case. Use this in preference to {@code new ShiftedNote(...)}
     * when wrapping a {@link PitchedLike} that might already be shifted.
     *
     * <ul>
     *   <li>{@code shift == 0} → returns the base unchanged.</li>
     *   <li>base is {@link PitchedNote} → returns
     *       {@code new ShiftedNote(base, shift)}.</li>
     *   <li>base is {@link ShiftedNote} → returns
     *       {@code new ShiftedNote(base.original, base.shift + shift)} —
     *       the original is preserved; shifts compose.</li>
     * </ul>
     *
     * <p>The factory respects the in-range invariant: it throws if the
     * resulting effective MIDI would fall outside [0, 127]. Pre-check
     * with {@link TransposeTransform#countOutOfRange} when applying
     * piece-wide shifts.</p>
     */
    public static PitchedLike of(PitchedLike base, int shift) {
        if (shift == 0) return base;
        return switch (base) {
            case PitchedNote pn -> new ShiftedNote(pn, shift);
            case ShiftedNote s  -> new ShiftedNote(s.original(), s.semitoneShift() + shift);
        };
    }

    @Override
    public ShiftedNote withTiedToNext() {
        return new ShiftedNote(original.withTiedToNext(), semitoneShift);
    }
}
