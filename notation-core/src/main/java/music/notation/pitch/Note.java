package music.notation.pitch;

/**
 * A note letter with optional accidental and octave shift.
 *
 * <p>{@link NoteName} constants ({@code A}, {@code B}, …) implement this
 * directly — their accidental is resolved from context (key signature)
 * and octave shift defaults to 0.</p>
 *
 * <p>Modifiers can be chained:</p>
 * <pre>{@code
 * o4(QUARTER, A, C, E)                // all key-aware, same octave
 * o4(QUARTER, A, C.f(), E)            // C forced flat
 * o4(QUARTER, C, G, C.higher(1))      // C5 in an o4() call
 * o4(QUARTER, F.n().higher(1))        // F natural, one octave up
 * }</pre>
 */
public sealed interface Note permits NoteName, AccidentedNote, ShiftedNote {

    /** The letter name of this note. */
    NoteName noteName();

    /** Octave offset relative to the builder's octave (default 0). */
    default int octaveShift() { return 0; }

    /** Return this note shifted up by the given number of octaves. */
    default ShiftedNote higher(int octaves) { return new ShiftedNote(this, octaves); }

    /** Return this note shifted down by the given number of octaves. */
    default ShiftedNote lower(int octaves) { return new ShiftedNote(this, -octaves); }
}
