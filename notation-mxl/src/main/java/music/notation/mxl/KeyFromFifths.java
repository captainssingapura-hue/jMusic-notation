package music.notation.mxl;

import music.notation.pitch.Accidental;
import music.notation.pitch.NoteName;
import music.notation.structure.KeySignature;
import music.notation.structure.Mode;

/**
 * Maps MusicXML's {@code <fifths>} value (number of accidentals on the circle of
 * fifths) to a {@link KeySignature}. Negative = flats, positive = sharps; the
 * sign is interpreted relative to the major-mode tonic table.
 */
final class KeyFromFifths {

    /** Major tonic (with accidental) for fifths -7..+7. Index 0 = C major. */
    private static final TonicSpec[] MAJOR = {
            new TonicSpec(NoteName.C, Accidental.FLAT),    // -7  Cb
            new TonicSpec(NoteName.G, Accidental.FLAT),    // -6  Gb
            new TonicSpec(NoteName.D, Accidental.FLAT),    // -5  Db
            new TonicSpec(NoteName.A, Accidental.FLAT),    // -4  Ab
            new TonicSpec(NoteName.E, Accidental.FLAT),    // -3  Eb
            new TonicSpec(NoteName.B, Accidental.FLAT),    // -2  Bb
            new TonicSpec(NoteName.F, Accidental.NATURAL), // -1  F
            new TonicSpec(NoteName.C, Accidental.NATURAL), //  0  C
            new TonicSpec(NoteName.G, Accidental.NATURAL), // +1  G
            new TonicSpec(NoteName.D, Accidental.NATURAL), // +2  D
            new TonicSpec(NoteName.A, Accidental.NATURAL), // +3  A
            new TonicSpec(NoteName.E, Accidental.NATURAL), // +4  E
            new TonicSpec(NoteName.B, Accidental.NATURAL), // +5  B
            new TonicSpec(NoteName.F, Accidental.SHARP),   // +6  F#
            new TonicSpec(NoteName.C, Accidental.SHARP),   // +7  C#
    };

    /** Relative minor for fifths -7..+7 (minor 6th below major tonic). */
    private static final TonicSpec[] MINOR = {
            new TonicSpec(NoteName.A, Accidental.FLAT),    // -7
            new TonicSpec(NoteName.E, Accidental.FLAT),    // -6
            new TonicSpec(NoteName.B, Accidental.FLAT),    // -5
            new TonicSpec(NoteName.F, Accidental.NATURAL), // -4
            new TonicSpec(NoteName.C, Accidental.NATURAL), // -3
            new TonicSpec(NoteName.G, Accidental.NATURAL), // -2
            new TonicSpec(NoteName.D, Accidental.NATURAL), // -1
            new TonicSpec(NoteName.A, Accidental.NATURAL), //  0
            new TonicSpec(NoteName.E, Accidental.NATURAL), // +1
            new TonicSpec(NoteName.B, Accidental.NATURAL), // +2
            new TonicSpec(NoteName.F, Accidental.SHARP),   // +3
            new TonicSpec(NoteName.C, Accidental.SHARP),   // +4
            new TonicSpec(NoteName.G, Accidental.SHARP),   // +5
            new TonicSpec(NoteName.D, Accidental.SHARP),   // +6
            new TonicSpec(NoteName.A, Accidental.SHARP),   // +7
    };

    private KeyFromFifths() {}

    static KeySignature of(int fifths, Mode mode) {
        if (fifths < -7 || fifths > 7) {
            throw new IllegalArgumentException("fifths out of range [-7,7]: " + fifths);
        }
        // For tonic resolution, MINOR uses the relative-minor table;
        // every other mode (including NONE) reads from the major table —
        // it's the closest faithful answer when the source doesn't say.
        TonicSpec spec = (mode == Mode.MINOR ? MINOR : MAJOR)[fifths + 7];
        return new KeySignature(spec.tonic, spec.accidental, mode);
    }

    private record TonicSpec(NoteName tonic, Accidental accidental) {}
}
