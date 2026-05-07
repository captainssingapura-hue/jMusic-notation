package music.notation.mxl;

/**
 * MusicXML {@code <transpose>} payload for a part. Combined offset =
 * {@code chromatic + 12 * octaveChange}; that's what we apply to a written
 * MIDI value to get the sounding (concert) pitch.
 *
 * <p>{@code diatonic} is preserved alongside {@code chromatic} only so a
 * notation renderer can reconstruct the original written pitch with the
 * correct enharmonic spelling. The MIDI codec ignores it.</p>
 */
public record Transpose(int chromatic, int octaveChange, int diatonic) {

    /** No transposition (concert pitch). */
    public static final Transpose NONE = new Transpose(0, 0, 0);

    public Transpose {
        if (chromatic < -36 || chromatic > 36) {
            throw new IllegalArgumentException("chromatic out of plausible range: " + chromatic);
        }
    }

    /** Total semitone offset to add to written MIDI to obtain sounding MIDI. */
    public int totalSemitones() { return chromatic + 12 * octaveChange; }

    /** True iff this transpose has no audible effect. */
    public boolean isIdentity() { return totalSemitones() == 0; }
}
