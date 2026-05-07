package music.notation.play;

import music.notation.duration.BaseValue;
import music.notation.duration.Duration;
import music.notation.event.Dynamic;
import music.notation.pitch.*;

public final class MidiMapper {

    public static final int TICKS_PER_QUARTER = 480;

    private MidiMapper() {}

    public static int toMidiNote(Pitch pitch) {
        return switch (pitch) {
            case StaffPitch sp -> staffPitchToMidi(sp);
        };
    }

    private static int staffPitchToMidi(StaffPitch sp) {
        int semitone = switch (sp.noteName()) {
            case C -> 0;
            case D -> 2;
            case E -> 4;
            case F -> 5;
            case G -> 7;
            case A -> 9;
            case B -> 11;
        };
        int accidentalOffset = switch (sp.accidental()) {
            case DOUBLE_FLAT -> -2;
            case FLAT -> -1;
            case NATURAL -> 0;
            case SHARP -> 1;
            case DOUBLE_SHARP -> 2;
        };
        return (sp.octave().value() + 1) * 12 + semitone + accidentalOffset;
    }

    public static long toTicks(Duration duration) {
        // Exact rational → ticks math. PPQ = ticks per quarter, so
        // ticks per whole = 4 × PPQ. For a duration N/D of a whole:
        //   ticks = N × 4 × PPQ / D
        // This is exact for triplets, quintuplets, septuplets, etc.
        // (all integer at PPQ = 480: 480/12 = 40, 480/10 = 48, etc.).
        return duration.ticks(TICKS_PER_QUARTER);
    }

    /** MIDI note one diatonic step above (approximated as +2 semitones). */
    public static int stepAbove(int midiNote) {
        return midiNote + 2;
    }

    /** MIDI note one diatonic step below (approximated as -2 semitones). */
    public static int stepBelow(int midiNote) {
        return midiNote - 2;
    }

    /** Duration of a single ornament subdivision (1/8 of a quarter note). */
    public static final long ORNAMENT_TICK = TICKS_PER_QUARTER / 8;

    /** Duration of a grace note (1/8 of a quarter note, stolen from the next note). */
    public static final long GRACE_NOTE_TICK = TICKS_PER_QUARTER / 8;

    public static int toVelocity(Dynamic dynamic) {
        return switch (dynamic) {
            case PPP -> 20;
            case PP -> 35;
            case P -> 50;
            case MP -> 65;
            case MF -> 80;
            case F -> 100;
            case FF -> 115;
            case FFF -> 127;
            case CRESCENDO, DECRESCENDO -> -1; // marker, not a velocity
        };
    }
}
