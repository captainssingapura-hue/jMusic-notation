package music.notation.autodrum;

import music.notation.autodrum.strategies.DiscoStrategy;
import music.notation.autodrum.strategies.FunkStrategy;
import music.notation.autodrum.strategies.GentleClassicalStrategy;
import music.notation.autodrum.strategies.JazzStrategy;
import music.notation.autodrum.strategies.MetalStrategy;
import music.notation.autodrum.strategies.NoStrategy;
import music.notation.autodrum.strategies.RockBeatStrategy;
import music.notation.autodrum.strategies.ShuffleStrategy;
import music.notation.structure.Piece;

import java.util.List;

/**
 * Registry of built-in {@link DrumStrategy} instances.
 *
 * <p>{@link #NONE} is a sentinel that never produces a drum track — used
 * as the "off" choice in pickers so the rest of the code can treat it
 * uniformly with other strategies.</p>
 *
 * <p>Adding a new strategy: implement {@link DrumStrategy}, instantiate
 * a singleton, append to the {@link #available()} list. (If the strategy
 * library grows beyond this module, promote registration to a
 * {@code ServiceLoader}.)</p>
 */
public final class DrumStrategies {

    public static final DrumStrategy NONE              = new NoStrategy();
    public static final DrumStrategy GENTLE_CLASSICAL  = new GentleClassicalStrategy();
    public static final DrumStrategy ROCK_8TH          = new RockBeatStrategy();
    public static final DrumStrategy DISCO             = new DiscoStrategy();
    public static final DrumStrategy SHUFFLE           = new ShuffleStrategy();
    public static final DrumStrategy FUNK              = new FunkStrategy();
    public static final DrumStrategy JAZZ              = new JazzStrategy();
    public static final DrumStrategy METAL             = new MetalStrategy();

    private static final List<DrumStrategy> AVAILABLE = List.of(
            NONE,
            GENTLE_CLASSICAL,
            ROCK_8TH,
            DISCO,
            SHUFFLE,
            FUNK,
            JAZZ,
            METAL
    );

    private DrumStrategies() {}

    /** All built-in strategies, in display order. */
    public static List<DrumStrategy> available() {
        return AVAILABLE;
    }

    /** Default strategy for a freshly-loaded piece — currently always {@link #NONE}. */
    public static DrumStrategy defaultFor(Piece source) {
        return NONE;
    }
}
