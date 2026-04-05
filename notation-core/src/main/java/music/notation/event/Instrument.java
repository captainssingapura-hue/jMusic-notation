package music.notation.event;

/**
 * General MIDI instrument programs (0-based).
 * Covers the most commonly used instruments.
 */
public enum Instrument {
    // Piano
    ACOUSTIC_GRAND_PIANO(0),
    BRIGHT_ACOUSTIC_PIANO(1),
    ELECTRIC_GRAND_PIANO(2),
    HONKY_TONK_PIANO(3),
    ELECTRIC_PIANO_1(4),
    ELECTRIC_PIANO_2(5),
    HARPSICHORD(6),
    CLAVINET(7),

    // Chromatic Percussion
    CELESTA(8),
    GLOCKENSPIEL(9),
    MUSIC_BOX(10),
    VIBRAPHONE(11),
    MARIMBA(12),
    XYLOPHONE(13),

    // Organ
    DRAWBAR_ORGAN(16),
    PERCUSSIVE_ORGAN(17),
    ROCK_ORGAN(18),
    CHURCH_ORGAN(19),
    ACCORDION(21),
    HARMONICA(22),

    // Guitar
    ACOUSTIC_GUITAR_NYLON(24),
    ACOUSTIC_GUITAR_STEEL(25),
    ELECTRIC_GUITAR_JAZZ(26),
    ELECTRIC_GUITAR_CLEAN(27),
    ELECTRIC_GUITAR_MUTED(28),
    OVERDRIVEN_GUITAR(29),
    DISTORTION_GUITAR(30),

    // Bass
    ACOUSTIC_BASS(32),
    ELECTRIC_BASS_FINGER(33),
    ELECTRIC_BASS_PICK(34),
    FRETLESS_BASS(35),
    SLAP_BASS(36),

    // Strings
    VIOLIN(40),
    VIOLA(41),
    CELLO(42),
    CONTRABASS(43),
    TREMOLO_STRINGS(44),
    PIZZICATO_STRINGS(45),
    ORCHESTRAL_HARP(46),
    STRING_ENSEMBLE_1(48),
    STRING_ENSEMBLE_2(49),

    // Ensemble
    CHOIR_AAHS(52),
    VOICE_OOHS(53),

    // Brass
    TRUMPET(56),
    TROMBONE(57),
    TUBA(58),
    FRENCH_HORN(60),
    BRASS_SECTION(61),

    // Reed
    SOPRANO_SAX(64),
    ALTO_SAX(65),
    TENOR_SAX(66),
    BARITONE_SAX(67),
    OBOE(68),
    ENGLISH_HORN(69),
    BASSOON(70),
    CLARINET(71),

    // Pipe
    PICCOLO(72),
    FLUTE(73),
    RECORDER(74),
    PAN_FLUTE(75),

    // Synth Lead
    SYNTH_LEAD_SQUARE(80),
    SYNTH_LEAD_SAWTOOTH(81),

    // Synth Pad
    SYNTH_PAD_NEW_AGE(88),
    SYNTH_PAD_WARM(89),
    SYNTH_PAD_CHOIR(91),

    // Percussive
    STEEL_DRUMS(114),
    WOODBLOCK(115),

    // Drum Kit (MIDI channel 10 — program value is ignored but 0 = Standard Kit)
    DRUM_KIT(0);

    private final int program;

    Instrument(int program) {
        this.program = program;
    }

    public int program() {
        return program;
    }
}
