package music.notation.performance;

/**
 * General MIDI percussion piece numbers. Use as the {@code piece} argument to
 * {@link DrumNote}, e.g. {@code new DrumNote(0, 250, Drums.SNARE)}.
 */
public final class Drums {
    private Drums() {}

    public static final int BASS_DRUM = 35;
    public static final int KICK = 36;
    public static final int SNARE = 38;
    public static final int HAND_CLAP = 39;
    public static final int LO_TOM = 41;
    public static final int CLOSED_HAT = 42;
    public static final int OPEN_HAT = 46;
    public static final int MID_TOM = 47;
    public static final int HI_TOM = 50;
    public static final int CRASH = 49;
    public static final int RIDE = 51;
    public static final int TAMBOURINE = 54;
    public static final int COWBELL = 56;
}
