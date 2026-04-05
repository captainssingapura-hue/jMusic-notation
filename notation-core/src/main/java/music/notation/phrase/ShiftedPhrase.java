package music.notation.phrase;

import music.notation.structure.KeySignature;
import music.notation.structure.Mode;

/**
 * A lazy wrapper that transposes a source phrase from one key/scale to another
 * using <em>scale-degree mapping</em>.
 *
 * <p>Each note's scale degree in the source key is preserved, but sounded in the
 * target key's tonality. For example, the 3rd degree of C major (E, +4 semitones)
 * maps to the 3rd degree of D minor (F, +3 semitones from D), so the interval
 * shrinks from major 3rd to minor 3rd — a true modal shift, not just transposition.</p>
 *
 * <p>The source phrase is immutable and unmodified; the shift is applied at playback
 * time by the MIDI interpreter.</p>
 */
public record ShiftedPhrase(Phrase source, KeySignature sourceKey, KeySignature targetKey) implements Phrase {

    @Override
    public PhraseMarking marking() {
        return source.marking();
    }

    // ── Scale-degree shift logic (used by the interpreter) ──

    private static final int[] MAJOR_SCALE     = {0, 2, 4, 5, 7, 9, 11};
    private static final int[] MINOR_SCALE     = {0, 2, 3, 5, 7, 8, 10};
    private static final int[] DORIAN_SCALE    = {0, 2, 3, 5, 7, 9, 10};
    private static final int[] PHRYGIAN_SCALE  = {0, 1, 3, 5, 7, 8, 10};
    private static final int[] LYDIAN_SCALE    = {0, 2, 4, 6, 7, 9, 11};
    private static final int[] MIXOLYDIAN_SCALE= {0, 2, 4, 5, 7, 9, 10};
    private static final int[] AEOLIAN_SCALE   = {0, 2, 3, 5, 7, 8, 10}; // = natural minor
    private static final int[] LOCRIAN_SCALE   = {0, 1, 3, 5, 6, 8, 10};

    /**
     * Shift a MIDI note from the source key to the target key by scale-degree mapping.
     *
     * <p>Algorithm: express the note as
     * {@code fromRoot + octave*12 + fromScale[degree] + chromatic},
     * then rewrite as
     * {@code toRoot + octave*12 + toScale[degree] + chromatic}.</p>
     *
     * <p>The formula simplifies to:
     * {@code shifted = midiNote − fromScale[degree] − fromRoot + toScale[degree] + toRoot}</p>
     */
    public int shiftMidiNote(int midiNote) {
        int fromRoot = noteNameSemitone(sourceKey.tonic());
        int toRoot   = noteNameSemitone(targetKey.tonic());
        int[] fromScale = scaleFor(sourceKey.mode());
        int[] toScale   = scaleFor(targetKey.mode());

        // Pitch class relative to source root (0–11)
        int pc = ((midiNote % 12) - fromRoot + 12) % 12;

        // Find the highest scale degree at or below this pitch class
        int degree = 0;
        for (int d = 6; d >= 0; d--) {
            if (fromScale[d] <= pc) {
                degree = d;
                break;
            }
        }

        return midiNote - fromRoot - fromScale[degree] + toRoot + toScale[degree];
    }

    static int noteNameSemitone(music.notation.pitch.NoteName n) {
        return switch (n) {
            case C -> 0; case D -> 2; case E -> 4; case F -> 5;
            case G -> 7; case A -> 9; case B -> 11;
        };
    }

    static int[] scaleFor(Mode mode) {
        return switch (mode) {
            case MAJOR       -> MAJOR_SCALE;
            case MINOR       -> MINOR_SCALE;
            case DORIAN      -> DORIAN_SCALE;
            case PHRYGIAN    -> PHRYGIAN_SCALE;
            case LYDIAN      -> LYDIAN_SCALE;
            case MIXOLYDIAN  -> MIXOLYDIAN_SCALE;
            case AEOLIAN     -> AEOLIAN_SCALE;
            case LOCRIAN     -> LOCRIAN_SCALE;
        };
    }
}
