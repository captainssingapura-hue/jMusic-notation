package music.notation.performance;

/**
 * General MIDI drum kit program numbers, used as the {@code program} argument to
 * {@link InstrumentControl} on a DRUM track.
 */
public final class DrumKit {
    private DrumKit() {}

    public static final int STANDARD = 0;
    public static final int ROOM = 8;
    public static final int POWER = 16;
    public static final int ELECTRONIC = 24;
    public static final int JAZZ = 32;
    public static final int BRUSH = 40;
    public static final int ORCHESTRA = 48;
}
