package music.notation.structure;

import music.notation.pitch.Accidental;
import music.notation.pitch.NoteName;

public record KeySignature(NoteName tonic, Accidental accidental, Mode mode) {

    /** Convenience constructor for natural-tonic keys (C major, D minor, etc.). */
    public KeySignature(NoteName tonic, Mode mode) {
        this(tonic, Accidental.NATURAL, mode);
    }
}
