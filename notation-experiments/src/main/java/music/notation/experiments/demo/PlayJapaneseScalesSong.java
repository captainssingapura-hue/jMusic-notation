package music.notation.experiments.demo;

import music.notation.experiments.hirajoshi.HirajoshiConcretizer;
import music.notation.experiments.hirajoshi.HirajoshiNote;
import music.notation.experiments.insen.InsenConcretizer;
import music.notation.experiments.insen.InsenNote;
import music.notation.experiments.iwato.IwatoConcretizer;
import music.notation.experiments.iwato.IwatoNote;
import music.notation.experiments.ryukyu.RyukyuConcretizer;
import music.notation.experiments.ryukyu.RyukyuNote;
import music.notation.experiments.transform.ScaleTranspose;
import music.notation.experiments.yo.YoConcretizer;
import music.notation.experiments.yo.YoNote;

import java.util.function.Function;

/**
 * Plays the same four-bar motif through every Japanese pentatonic scale
 * (Hirajoshi, Yo, Insen, Iwato, Ryukyu) in C, Koto voice, with a synced
 * top-down piano-roll TUI.
 *
 * <p>Pass {@code --silent} / {@code -s} for visual-only output.</p>
 */
public final class PlayJapaneseScalesSong {

    private PlayJapaneseScalesSong() {}

    public static void main(String[] args) {
        final boolean silent = ScaleDemoPlayer.isSilent(args);
        final var motif = ScaleDemoPlayer.defaultMotif();

        ScaleDemoPlayer.playScale("Hirajoshi in C (Koto)", motif,
                Function.identity(),
                HirajoshiConcretizer.inC(),
                silent);
        if (!silent) ScaleDemoPlayer.pause(ScaleDemoPlayer.GAP_BETWEEN_SCALES_MS);

        ScaleDemoPlayer.playScale("Yo in C (Koto)", motif,
                new ScaleTranspose<>(HirajoshiNote::ofIndex, YoNote::ofIndex)::forward,
                YoConcretizer.inC(),
                silent);
        if (!silent) ScaleDemoPlayer.pause(ScaleDemoPlayer.GAP_BETWEEN_SCALES_MS);

        ScaleDemoPlayer.playScale("Insen in C (Koto)", motif,
                new ScaleTranspose<>(HirajoshiNote::ofIndex, InsenNote::ofIndex)::forward,
                InsenConcretizer.inC(),
                silent);
        if (!silent) ScaleDemoPlayer.pause(ScaleDemoPlayer.GAP_BETWEEN_SCALES_MS);

        ScaleDemoPlayer.playScale("Iwato in C (Koto)", motif,
                new ScaleTranspose<>(HirajoshiNote::ofIndex, IwatoNote::ofIndex)::forward,
                IwatoConcretizer.inC(),
                silent);
        if (!silent) ScaleDemoPlayer.pause(ScaleDemoPlayer.GAP_BETWEEN_SCALES_MS);

        ScaleDemoPlayer.playScale("Ryukyu in C (Koto)", motif,
                new ScaleTranspose<>(HirajoshiNote::ofIndex, RyukyuNote::ofIndex)::forward,
                RyukyuConcretizer.inC(),
                silent);
    }
}
