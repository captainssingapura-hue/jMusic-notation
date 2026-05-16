package music.notation.performance;

/**
 * Sealed parent for everything "pitched" at the concrete-note layer.
 *
 * <p>Today's variants:</p>
 * <ul>
 *   <li>{@link PitchedNote} — the canonical authored / concretized note,
 *       carrying its MIDI value as a record field.</li>
 *   <li>{@link ShiftedNote} — a wrapper representing a transposed view
 *       of an underlying {@link PitchedNote}. {@link #midi()} returns
 *       the <em>effective</em> (shifted) MIDI value; the original is
 *       recoverable from {@link ShiftedNote#original()}.</li>
 * </ul>
 *
 * <p>Why a parent interface? "Pitched-ness" is itself a closed sum.
 * Consumers that care only about effective MIDI value ({@link MidiCodec},
 * playback) read {@code midi()} polymorphically and don't need to know
 * whether the note has been transposed. Consumers that care about the
 * original (the UI's show-both pitch-scroll rendering) pattern-match
 * on {@link ShiftedNote} to recover both pitches.</p>
 *
 * <p>The {@link Track} kind-invariant accepts {@code PitchedLike} for
 * {@code PITCHED} tracks (was previously just {@code PitchedNote}).
 * Drum tracks remain {@code DrumNote}-only — drum "pitch" is a kit
 * selector and is not transposable, so {@code DrumNote} deliberately
 * does <b>not</b> extend {@code PitchedLike}.</p>
 */
public sealed interface PitchedLike
        extends ConcreteNote
        permits PitchedNote, ShiftedNote {

    /**
     * Effective MIDI value for playback emission. For {@link PitchedNote}
     * this is the authored pitch; for {@link ShiftedNote} this is
     * {@code original.midi() + semitoneShift}. Always in [0, 127] — the
     * constructors of both variants enforce this invariant.
     */
    int midi();
}
