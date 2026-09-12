package music.notation.event;

/**
 * Diatonic step — the seven natural note letters that anchor pitch
 * spelling in a score. Carries the semitone offset within an octave
 * from {@code C}, used to resolve a {@code (step, alter, octave)}
 * triple into a 12-TET MIDI byte at the codec boundary.
 *
 * <p>{@code C♯} and {@code D♭} share a MIDI number (61) but spell
 * differently — the {@link Step} is the diatonic letter; the alter
 * adjusts chromatically. Score renderers select the glyph from
 * {@code (step, alter, key signature)}; the codec converts to MIDI
 * via {@link #semitoneFromC()}.</p>
 */
public enum Step {
    C(0),
    D(2),
    E(4),
    F(5),
    G(7),
    A(9),
    B(11);

    private final int semitoneFromC;

    Step(int semitoneFromC) { this.semitoneFromC = semitoneFromC; }

    /** Semitones above {@code C} within the same octave (0–11). */
    public int semitoneFromC() { return semitoneFromC; }
}
