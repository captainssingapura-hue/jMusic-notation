package music.notation.pitch;

/**
 * A note with an octave shift relative to the builder's base octave.
 *
 * <p>Created via {@code C.higher(1)} or {@code A.s().lower(1)}.</p>
 */
public record ShiftedNote(Note base, int shift) implements Note {

    @Override
    public NoteName noteName() { return base.noteName(); }

    @Override
    public int octaveShift() { return base.octaveShift() + shift; }

    @Override
    public ShiftedNote higher(int octaves) { return new ShiftedNote(base, shift + octaves); }

    @Override
    public ShiftedNote lower(int octaves) { return new ShiftedNote(base, shift - octaves); }
}
