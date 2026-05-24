package music.notation.event;

/**
 * General MIDI instrument programs (0-based). Complete GM Level 1 set
 * — all 128 melodic programs plus the GM Level 2 standard drum-kit
 * lineup, all of which route on MIDI channel 10 (zero-indexed 9).
 *
 * <h2>Drum-kit values</h2>
 *
 * <p>{@link #DRUM_KIT} is the GM Standard Kit (program 0 on channel 10);
 * the {@code DRUM_KIT_*} variants are the GM2 / GS / XG standard set
 * (Room=8, Power=16, Electronic=24, TR-808=25, Jazz=32, Brush=40,
 * Orchestra=48). Most full-fat GM2-compliant SF2 / DLS soundbanks
 * include all of them; older minimal banks may have only Standard.
 * Use {@link #isDrumKit()} to test whether a value is any drum kit
 * rather than comparing to {@link #DRUM_KIT} directly.</p>
 *
 * <p>Soundbank-aware UI surfaces (e.g.
 * {@code SoundBankRegistry.classifyToGm}) match patches to GM families
 * by program number; missing entries here used to dump unmapped
 * patches into {@link #ACOUSTIC_GRAND_PIANO} as a fallback. With full
 * coverage every GM program has a stable enum value.</p>
 */
public enum Instrument {
    // Piano (0–7)
    ACOUSTIC_GRAND_PIANO(0),
    BRIGHT_ACOUSTIC_PIANO(1),
    ELECTRIC_GRAND_PIANO(2),
    HONKY_TONK_PIANO(3),
    ELECTRIC_PIANO_1(4),
    ELECTRIC_PIANO_2(5),
    HARPSICHORD(6),
    CLAVINET(7),

    // Chromatic Percussion (8–15)
    CELESTA(8),
    GLOCKENSPIEL(9),
    MUSIC_BOX(10),
    VIBRAPHONE(11),
    MARIMBA(12),
    XYLOPHONE(13),
    TUBULAR_BELLS(14),
    DULCIMER(15),

    // Organ (16–23)
    DRAWBAR_ORGAN(16),
    PERCUSSIVE_ORGAN(17),
    ROCK_ORGAN(18),
    CHURCH_ORGAN(19),
    REED_ORGAN(20),
    ACCORDION(21),
    HARMONICA(22),
    TANGO_ACCORDION(23),

    // Guitar (24–31)
    ACOUSTIC_GUITAR_NYLON(24),
    ACOUSTIC_GUITAR_STEEL(25),
    ELECTRIC_GUITAR_JAZZ(26),
    ELECTRIC_GUITAR_CLEAN(27),
    ELECTRIC_GUITAR_MUTED(28),
    OVERDRIVEN_GUITAR(29),
    DISTORTION_GUITAR(30),
    GUITAR_HARMONICS(31),

    // Bass (32–39)
    ACOUSTIC_BASS(32),
    ELECTRIC_BASS_FINGER(33),
    ELECTRIC_BASS_PICK(34),
    FRETLESS_BASS(35),
    SLAP_BASS(36),
    SLAP_BASS_2(37),
    SYNTH_BASS_1(38),
    SYNTH_BASS_2(39),

    // Strings (40–47)
    VIOLIN(40),
    VIOLA(41),
    CELLO(42),
    CONTRABASS(43),
    TREMOLO_STRINGS(44),
    PIZZICATO_STRINGS(45),
    ORCHESTRAL_HARP(46),
    TIMPANI(47),

    // Ensemble (48–55)
    STRING_ENSEMBLE_1(48),
    STRING_ENSEMBLE_2(49),
    SYNTH_STRINGS_1(50),
    SYNTH_STRINGS_2(51),
    CHOIR_AAHS(52),
    VOICE_OOHS(53),
    SYNTH_VOICE(54),
    ORCHESTRA_HIT(55),

    // Brass (56–63)
    TRUMPET(56),
    TROMBONE(57),
    TUBA(58),
    MUTED_TRUMPET(59),
    FRENCH_HORN(60),
    BRASS_SECTION(61),
    SYNTH_BRASS_1(62),
    SYNTH_BRASS_2(63),

    // Reed (64–71)
    SOPRANO_SAX(64),
    ALTO_SAX(65),
    TENOR_SAX(66),
    BARITONE_SAX(67),
    OBOE(68),
    ENGLISH_HORN(69),
    BASSOON(70),
    CLARINET(71),

    // Pipe (72–79)
    PICCOLO(72),
    FLUTE(73),
    RECORDER(74),
    PAN_FLUTE(75),
    BLOWN_BOTTLE(76),
    SHAKUHACHI(77),
    WHISTLE(78),
    OCARINA(79),

    // Synth Lead (80–87)
    SYNTH_LEAD_SQUARE(80),
    SYNTH_LEAD_SAWTOOTH(81),
    SYNTH_LEAD_CALLIOPE(82),
    SYNTH_LEAD_CHIFF(83),
    SYNTH_LEAD_CHARANG(84),
    SYNTH_LEAD_VOICE(85),
    SYNTH_LEAD_FIFTHS(86),
    SYNTH_LEAD_BASS_AND_LEAD(87),

    // Synth Pad (88–95)
    SYNTH_PAD_NEW_AGE(88),
    SYNTH_PAD_WARM(89),
    SYNTH_PAD_POLYSYNTH(90),
    SYNTH_PAD_CHOIR(91),
    SYNTH_PAD_BOWED(92),
    SYNTH_PAD_METALLIC(93),
    SYNTH_PAD_HALO(94),
    SYNTH_PAD_SWEEP(95),

    // Synth FX (96–103)
    FX_RAIN(96),
    FX_SOUNDTRACK(97),
    FX_CRYSTAL(98),
    FX_ATMOSPHERE(99),
    FX_BRIGHTNESS(100),
    FX_GOBLINS(101),
    FX_ECHOES(102),
    FX_SCI_FI(103),

    // Ethnic (104–111)
    SITAR(104),
    BANJO(105),
    SHAMISEN(106),
    KOTO(107),
    KALIMBA(108),
    BAGPIPE(109),
    FIDDLE(110),
    SHANAI(111),

    // Percussive (112–119)
    TINKLE_BELL(112),
    AGOGO(113),
    STEEL_DRUMS(114),
    WOODBLOCK(115),
    TAIKO_DRUM(116),
    MELODIC_TOM(117),
    SYNTH_DRUM(118),
    REVERSE_CYMBAL(119),

    // Sound FX (120–127)
    GUITAR_FRET_NOISE(120),
    BREATH_NOISE(121),
    SEASHORE(122),
    BIRD_TWEET(123),
    TELEPHONE_RING(124),
    HELICOPTER(125),
    APPLAUSE(126),
    GUNSHOT(127),

    // Drum Kits — MIDI channel 10 (zero-indexed 9). GM Level 2 / GS / XG
    // standard set of drum-kit programs. Most modern SF2/DLS banks
    // include all of them; very small banks may only have Standard.
    DRUM_KIT(0),                 // Standard Kit
    DRUM_KIT_ROOM(8),            // Room Kit
    DRUM_KIT_POWER(16),          // Power / Rock Kit
    DRUM_KIT_ELECTRONIC(24),     // Electronic Kit
    DRUM_KIT_TR808(25),          // TR-808 / Analog Kit
    DRUM_KIT_JAZZ(32),           // Jazz Kit
    DRUM_KIT_BRUSH(40),          // Brush Kit
    DRUM_KIT_ORCHESTRA(48);      // Orchestra / Symphony Kit

    private final int program;

    Instrument(int program) {
        this.program = program;
    }

    public int program() {
        return program;
    }

    /**
     * True for any drum-kit value (Standard or one of the GM2 variants).
     * Prefer this over {@code instr == DRUM_KIT} when the intent is
     * "is this routed to channel 10 / treated as percussion."
     */
    public boolean isDrumKit() {
        return switch (this) {
            case DRUM_KIT, DRUM_KIT_ROOM, DRUM_KIT_POWER, DRUM_KIT_ELECTRONIC,
                 DRUM_KIT_TR808, DRUM_KIT_JAZZ, DRUM_KIT_BRUSH, DRUM_KIT_ORCHESTRA
                    -> true;
            default -> false;
        };
    }
}
