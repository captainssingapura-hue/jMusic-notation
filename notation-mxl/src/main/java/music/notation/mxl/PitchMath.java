package music.notation.mxl;

/**
 * MusicXML {@code <pitch>} arithmetic. {@code step+alter+octave} → MIDI 0..127,
 * matching the MIDI convention where MIDI 60 is middle C (C4).
 */
final class PitchMath {

    private PitchMath() {}

    /** Semitone offset of each diatonic step from C within an octave. */
    private static int stepSemitones(String step) {
        return switch (step) {
            case "C" -> 0;
            case "D" -> 2;
            case "E" -> 4;
            case "F" -> 5;
            case "G" -> 7;
            case "A" -> 9;
            case "B" -> 11;
            default -> throw new IllegalArgumentException("unknown step: " + step);
        };
    }

    /**
     * @param step    one of {@code C..B}
     * @param alter   semitone offset (sharps positive, flats negative); {@code 0} for natural
     * @param octave  scientific octave number, where C4 = middle C
     */
    static int toMidi(String step, int alter, int octave) {
        int midi = (octave + 1) * 12 + stepSemitones(step) + alter;
        if (midi < 0 || midi > 127) {
            throw new IllegalArgumentException(
                    "pitch out of MIDI range: " + step + " alter=" + alter +
                    " octave=" + octave + " -> " + midi);
        }
        return midi;
    }
}
