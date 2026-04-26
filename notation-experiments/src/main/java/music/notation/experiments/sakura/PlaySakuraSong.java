package music.notation.experiments.sakura;

import music.notation.experiments.chinese.gong.GongConcretizer;
import music.notation.experiments.chinese.gong.GongNote;
import music.notation.experiments.demo.ScaleDemoPlayer;
import music.notation.experiments.hirajoshi.HirajoshiConcretizer;
import music.notation.experiments.hirajoshi.HirajoshiNote;
import music.notation.experiments.transform.ScaleTranspose;
import music.notation.experiments.yo.YoConcretizer;
import music.notation.experiments.yo.YoNote;

import java.util.function.Function;

/**
 * Plays {@link SakuraSong Sakura Sakura} three times, re-coloured by the
 * abstract-level {@link ScaleTranspose}:
 *
 * <ol>
 *   <li><b>Hirajoshi in A</b> — the authentic, dark Japanese rendering.</li>
 *   <li><b>Yo in A</b>        — brighter (major-pentatonic flavour).</li>
 *   <li><b>Gong in A</b>      — Chinese folk interpretation (sharpened III).</li>
 * </ol>
 *
 * <p>The melody is defined <em>once</em> as a sequence of
 * {@code HirajoshiNote}s; the other two renderings come from transposing
 * every note's degree index into the target scale and running that scale's
 * concretizer. Same contour, three distinct pentatonic palettes.</p>
 *
 * <p>Pass {@code --silent} / {@code -s} for visual-only output.</p>
 */
public final class PlaySakuraSong {

    /** A tonic — traditional key for Sakura on koto / shamisen. */
    private static final int A_TONIC_PITCH_CLASS = 9;

    private PlaySakuraSong() {}

    public static void main(String[] args) {
        final boolean silent = ScaleDemoPlayer.isSilent(args);
        final var melody = SakuraSong.melody();

        // 1. Authentic: A Hirajoshi — the sound you expect to hear.
        ScaleDemoPlayer.playScale(
                "Sakura (authentic — A Hirajoshi, Koto)", melody,
                Function.identity(),
                new HirajoshiConcretizer(A_TONIC_PITCH_CLASS),
                silent);
        if (!silent) ScaleDemoPlayer.pause(ScaleDemoPlayer.GAP_BETWEEN_SCALES_MS);

        // 2. Yo variation: same song, major-pentatonic colouring.
        ScaleDemoPlayer.playScale(
                "Sakura (brighter — A Yo, Koto)", melody,
                new ScaleTranspose<>(HirajoshiNote::ofIndex, YoNote::ofIndex)::forward,
                new YoConcretizer(A_TONIC_PITCH_CLASS),
                silent);
        if (!silent) ScaleDemoPlayer.pause(ScaleDemoPlayer.GAP_BETWEEN_SCALES_MS);

        // 3. Gong variation: Chinese folk colouring.
        ScaleDemoPlayer.playScale(
                "Sakura (Chinese folk — A Gong, Koto)", melody,
                new ScaleTranspose<>(HirajoshiNote::ofIndex, GongNote::ofIndex)::forward,
                new GongConcretizer(A_TONIC_PITCH_CLASS),
                silent);
    }
}
