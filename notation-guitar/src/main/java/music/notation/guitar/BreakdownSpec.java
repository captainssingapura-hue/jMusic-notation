package music.notation.guitar;

/**
 * How the fingered passage is cut into practice ranges. Ranges are always
 * whole bars — they become loop points.
 *
 * <ul>
 *   <li>{@link Mode#BAR} — one range per bar.</li>
 *   <li>{@link Mode#PHRASE} — a new range starts at the bar of any note that
 *       follows a silence of at least {@code phraseGapBeats}.</li>
 *   <li>{@link Mode#POSITION} — a new range starts at the bar where the
 *       hand moves to a new position.</li>
 * </ul>
 * Every range is then capped at {@code maxBars}.
 */
public record BreakdownSpec(Mode mode, int maxBars, double phraseGapBeats) {

    public enum Mode { BAR, PHRASE, POSITION }

    public static final BreakdownSpec DEFAULT = new BreakdownSpec(Mode.PHRASE, 2, 1.0);

    public BreakdownSpec {
        if (mode == null) throw new IllegalArgumentException("mode required");
        if (maxBars < 1) throw new IllegalArgumentException("maxBars must be >= 1");
        if (phraseGapBeats <= 0) throw new IllegalArgumentException("phraseGapBeats must be > 0");
    }
}
