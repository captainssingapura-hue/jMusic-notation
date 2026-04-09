package music.notation.pitch;

/**
 * The seven note letter names. Each constant is also a {@link Note}
 * whose accidental is resolved from the key signature.
 *
 * <p>Use {@link #s()}, {@link #f()}, or {@link #n()} to produce an
 * {@link AccidentedNote} that overrides the key signature.</p>
 */
public enum NoteName implements Note {
    C, D, E, F, G, A, B;

    /** This note forced sharp. */
    public AccidentedNote s() { return new AccidentedNote(this, Accidental.SHARP); }

    /** This note forced flat. */
    public AccidentedNote f() { return new AccidentedNote(this, Accidental.FLAT); }

    /** This note forced natural (cancels key signature). */
    public AccidentedNote n() { return new AccidentedNote(this, Accidental.NATURAL); }

    @Override
    public NoteName noteName() { return this; }
}
