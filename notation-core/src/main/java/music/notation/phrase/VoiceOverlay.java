package music.notation.phrase;

import java.util.List;
import java.util.Optional;

/**
 * A parallel voice overlaid on a {@link MelodicPhrase}, expressed bar-by-bar.
 *
 * <p>Each entry in {@link #bars} corresponds to the main phrase's bar at the
 * same index:</p>
 * <ul>
 *   <li><b>Present</b> — a {@link Bar} of audible content that plays alongside
 *       the main bar at that index.</li>
 *   <li><b>Empty</b> — silence at that bar; the voice simply does not sound.</li>
 * </ul>
 *
 * <p><b>Whole-bar rule.</b> Overlays are always bar-aligned. A {@code Bar}
 * present at index {@code i} has the same {@code expectedSixtyFourths} as the
 * main phrase's bar {@code i} (validated by {@link MelodicPhrase} at
 * construction). There is no sub-bar alignment, no gap arithmetic, and no
 * rest-padding synthesis — silence is simply the absence of an override.</p>
 *
 * <p>Overlays are not themselves {@link Phrase}s — they can only appear
 * attached to a {@link MelodicPhrase}. For an independent parallel voice with
 * its own life-cycle (different instrument, different dynamics, different
 * length), use a separate {@link music.notation.structure.Track}.</p>
 */
public record VoiceOverlay(List<Optional<Bar>> bars) {

    public VoiceOverlay {
        bars = List.copyOf(bars);
    }

    /** Number of bar slots (matches the main phrase's bar count). */
    public int size() {
        return bars.size();
    }

    /** The override at bar index {@code i}, or empty for silent. */
    public Optional<Bar> at(int i) {
        return bars.get(i);
    }
}
