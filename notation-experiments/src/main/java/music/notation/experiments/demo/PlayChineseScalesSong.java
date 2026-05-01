package music.notation.experiments.demo;

import music.notation.experiments.chinese.gong.GongConcretizer;
import music.notation.experiments.chinese.gong.GongNote;
import music.notation.experiments.chinese.jue.JueConcretizer;
import music.notation.experiments.chinese.jue.JueNote;
import music.notation.experiments.chinese.shang.ShangConcretizer;
import music.notation.experiments.chinese.shang.ShangNote;
import music.notation.experiments.chinese.yu.YuConcretizer;
import music.notation.experiments.chinese.yu.YuNote;
import music.notation.experiments.chinese.zhi.ZhiConcretizer;
import music.notation.experiments.chinese.zhi.ZhiNote;
import music.notation.experiments.hirajoshi.HirajoshiNote;
import music.notation.experiments.transform.ScaleTranspose;

/**
 * Plays the same four-bar motif through every traditional Chinese
 * pentatonic mode (Gong, Shang, Jue, Zhi, Yu) in C, Koto voice, with a
 * synced top-down piano-roll TUI.
 *
 * <p>The motif is authored in {@code HirajoshiNote} and reinterpreted into
 * each Chinese mode purely by degree-index mapping via
 * {@link ScaleTranspose}. Same contour, five different modal colours.</p>
 *
 * <p>Pass {@code --silent} / {@code -s} for visual-only output.</p>
 */
public final class PlayChineseScalesSong {

    private PlayChineseScalesSong() {}

    public static void main(String[] args) {
        final boolean silent = ScaleDemoPlayer.isSilent(args);
        final var motif = ScaleDemoPlayer.defaultMotif();

        ScaleDemoPlayer.playScale("Gong (宫) in C (Koto)", motif,
                new ScaleTranspose<>(HirajoshiNote::ofIndex, GongNote::ofIndex)::forward,
                GongConcretizer.inC(),
                silent);
        if (!silent) ScaleDemoPlayer.pause(ScaleDemoPlayer.GAP_BETWEEN_SCALES_MS);

        ScaleDemoPlayer.playScale("Shang (商) in C (Koto)", motif,
                new ScaleTranspose<>(HirajoshiNote::ofIndex, ShangNote::ofIndex)::forward,
                ShangConcretizer.inC(),
                silent);
        if (!silent) ScaleDemoPlayer.pause(ScaleDemoPlayer.GAP_BETWEEN_SCALES_MS);

        ScaleDemoPlayer.playScale("Jue (角) in C (Koto)", motif,
                new ScaleTranspose<>(HirajoshiNote::ofIndex, JueNote::ofIndex)::forward,
                JueConcretizer.inC(),
                silent);
        if (!silent) ScaleDemoPlayer.pause(ScaleDemoPlayer.GAP_BETWEEN_SCALES_MS);

        ScaleDemoPlayer.playScale("Zhi (徵) in C (Koto)", motif,
                new ScaleTranspose<>(HirajoshiNote::ofIndex, ZhiNote::ofIndex)::forward,
                ZhiConcretizer.inC(),
                silent);
        if (!silent) ScaleDemoPlayer.pause(ScaleDemoPlayer.GAP_BETWEEN_SCALES_MS);

        ScaleDemoPlayer.playScale("Yu (羽) in C (Koto)", motif,
                new ScaleTranspose<>(HirajoshiNote::ofIndex, YuNote::ofIndex)::forward,
                YuConcretizer.inC(),
                silent);
    }
}
