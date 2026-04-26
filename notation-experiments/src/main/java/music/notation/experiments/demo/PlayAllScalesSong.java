package music.notation.experiments.demo;

/**
 * Grand tour: runs the Japanese scales demo immediately followed by the
 * Chinese scales demo, so one run contrasts all ten pentatonic colours
 * back-to-back.
 *
 * <p>Pass {@code --silent} / {@code -s} for visual-only output.</p>
 */
public final class PlayAllScalesSong {

    private PlayAllScalesSong() {}

    public static void main(String[] args) {
        PlayJapaneseScalesSong.main(args);
        if (!ScaleDemoPlayer.isSilent(args)) {
            ScaleDemoPlayer.pause(ScaleDemoPlayer.GAP_BETWEEN_SCALES_MS * 2);
        }
        PlayChineseScalesSong.main(args);
    }
}
