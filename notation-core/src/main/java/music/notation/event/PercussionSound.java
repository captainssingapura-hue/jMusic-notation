package music.notation.event;

/**
 * General MIDI percussion sounds, mapped to their standard note numbers.
 * These are used on MIDI channel 10 (0-indexed: 9).
 */
public enum PercussionSound {
    ACOUSTIC_BASS_DRUM(35),
    BASS_DRUM(36),
    SIDE_STICK(37),
    ACOUSTIC_SNARE(38),
    HAND_CLAP(39),
    ELECTRIC_SNARE(40),
    LOW_FLOOR_TOM(41),
    CLOSED_HI_HAT(42),
    HIGH_FLOOR_TOM(43),
    PEDAL_HI_HAT(44),
    LOW_TOM(45),
    OPEN_HI_HAT(46),
    LOW_MID_TOM(47),
    HIGH_MID_TOM(48),
    CRASH_CYMBAL(49),
    HIGH_TOM(50),
    RIDE_CYMBAL(51),
    CHINESE_CYMBAL(52),
    RIDE_BELL(53),
    TAMBOURINE(54),
    SPLASH_CYMBAL(55),
    COWBELL(56),
    CRASH_CYMBAL_2(57),
    VIBRASLAP(58),
    RIDE_CYMBAL_2(59);

    private final int midiNote;

    PercussionSound(int midiNote) {
        this.midiNote = midiNote;
    }

    public int midiNote() {
        return midiNote;
    }
}
