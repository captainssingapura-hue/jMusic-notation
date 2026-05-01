package music.notation.experiments.blues;

import music.notation.experiments.blues.major.BluesMajorConcretizer;
import music.notation.experiments.blues.major.BluesMajorNote;
import music.notation.experiments.blues.minor.BluesMinorConcretizer;
import music.notation.experiments.blues.minor.BluesMinorNote;
import music.notation.experiments.demo.ScaleDemoPlayer;
import music.notation.experiments.transform.ScaleTranspose;

import java.util.function.Function;

/**
 * Plays {@link BluesSong the blues motif} twice:
 *
 * <ol>
 *   <li><b>C Minor Blues</b> — authentic blues colouring with the ♭V
 *       "blue note" clashing against V.</li>
 *   <li><b>C Major Blues</b> — same motif, re-routed through the major
 *       blues' intervals via {@link ScaleTranspose} (degree-index
 *       preserved). ♭V becomes III, ♭VII becomes VI, ♭III becomes II —
 *       a completely different mood from identical contour.</li>
 * </ol>
 *
 * <p>Pass {@code --silent} / {@code -s} for visual-only output.</p>
 *
 * <p>Because both blues scales are hexatonic (6 degrees), cross-transpose
 * works via the standard {@code ScaleTranspose}. The degree-count
 * safety check there prevents accidental blues↔pentatonic transposes
 * that would be ambiguous.</p>
 */
public final class PlayBluesSong {

    // Note: ScaleDemoPlayer currently hardcodes Koto as the voice. A future
    // refactor could make instrument selectable per call (blues → clean
    // electric guitar would be more idiomatic).

    private PlayBluesSong() {}

    public static void main(String[] args) {
        final boolean silent = ScaleDemoPlayer.isSilent(args);
        final var motif = BluesSong.demo();

        // 1. Authentic: C minor blues (the "classic" blues sound).
        ScaleDemoPlayer.playScale(
                "Blues in C minor (♭V blue note)", motif,
                Function.identity(),
                BluesMinorConcretizer.inC(),
                silent);
        if (!silent) ScaleDemoPlayer.pause(ScaleDemoPlayer.GAP_BETWEEN_SCALES_MS);

        // 2. Major variation: same motif, major-blues colouring.
        ScaleDemoPlayer.playScale(
                "Blues in C major (♭III blue note)", motif,
                new ScaleTranspose<>(BluesMinorNote::ofIndex, BluesMajorNote::ofIndex)::forward,
                BluesMajorConcretizer.inC(),
                silent);
    }
}
