package music.notation.ui;

import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;

/**
 * One contiguous tempo segment in the piano-roll's tick space.
 *
 * <p>Visual encoding: piece-relative continuous gradient on a fixed
 * sky-blue hue. The slowest bpm in the loaded data renders as a pale
 * tint; the fastest as a deep tint. Within-piece contrast is therefore
 * always strong regardless of how narrow or wide the source's tempo
 * range is. Single-tempo pieces render mid-tone.</p>
 */
record TempoSegment(long startTick, long endTick, int bpm) {

    private static final double HUE = 210.0;
    private static final double SATURATION = 0.65;
    /** Lightness stretches from pale (slow) to deep (fast). */
    private static final double L_SLOW = 0.85;
    private static final double L_FAST = 0.30;
    /** Single-tempo pieces land here — mid of the slow/fast range. */
    private static final double L_SINGLE = 0.55;

    /**
     * Base colour for {@code bpm} given the piece's {@code minBpm} /
     * {@code maxBpm} range. When min == max (single-tempo piece),
     * returns the single mid-tone shade.
     */
    static Color baseColour(int bpm, int minBpm, int maxBpm) {
        double lightness;
        if (maxBpm <= minBpm) {
            lightness = L_SINGLE;
        } else {
            double t = (double) (bpm - minBpm) / (double) (maxBpm - minBpm);
            t = Math.max(0.0, Math.min(1.0, t));
            lightness = L_SLOW - (L_SLOW - L_FAST) * t;
        }
        return Color.hsb(HUE, SATURATION, lightness);
    }

    /**
     * 5-stop "brushed metal" gradient stops based on a segment's base
     * colour. Top edge is darker, ~20% has a shine line, the band fades
     * back to a shadow at the bottom edge.
     */
    static LinearGradient metalGradient(double topY, double bottomY, Color base) {
        double h = base.getHue();
        double s = base.getSaturation();
        double v = base.getBrightness();
        Color top    = Color.hsb(h, s, clamp(v * 0.55));
        Color shine  = Color.hsb(h, Math.max(0.0, s - 0.15), clamp(v * 1.20));
        Color middle = base;
        Color shadow = Color.hsb(h, s, clamp(v * 0.65));
        Color bottom = Color.hsb(h, s, clamp(v * 0.45));
        return new LinearGradient(
                0, topY, 0, bottomY, false, javafx.scene.paint.CycleMethod.NO_CYCLE,
                new Stop(0.00, top),
                new Stop(0.20, shine),
                new Stop(0.45, middle),
                new Stop(0.80, shadow),
                new Stop(1.00, bottom));
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
