package music.notation.phrase;

import music.notation.duration.Duration;
import music.notation.structure.TimeSignature;

/**
 * A phrase that is intentionally silent for a fixed number of bars.
 *
 * <p>Distinct from {@link RestPhrase}: a {@code RestPhrase} represents
 * <em>notated rest(s)</em> at arbitrary durations — an active musical gesture
 * of "don't play, by duration". A {@code VoidPhrase} represents a
 * <em>structural silence</em> over a whole number of bars — a length-only
 * shell that something else (e.g. a {@link VoiceOverlay} on a
 * {@link MelodicPhrase}, or a deliberate gap in a {@link music.notation.structure.Track})
 * is defined against.</p>
 *
 * <p>Construct with {@link #ofBars(TimeSignature, int)} /
 * {@link #ofBars(TimeSignature, int, PhraseMarking)} — the stored
 * {@code duration} is always a whole number of bars.</p>
 */
public record VoidPhrase(int bars, int barSixtyFourths, PhraseMarking marking) implements Phrase {

    public VoidPhrase {
        if (bars < 0) {
            throw new IllegalArgumentException("VoidPhrase.bars must be non-negative, got " + bars);
        }
        if (barSixtyFourths <= 0) {
            throw new IllegalArgumentException(
                    "VoidPhrase.barSixtyFourths must be positive, got " + barSixtyFourths);
        }
    }

    /** Total duration of this phrase in 64th-note units. */
    public int totalSixtyFourths() {
        return bars * barSixtyFourths;
    }

    /** Total duration as a {@link Duration}. */
    public Duration duration() {
        return Duration.ofSixtyFourths(totalSixtyFourths());
    }

    /** An N-bar silent phrase in the given time signature, marked {@code attacca}. */
    public static VoidPhrase ofBars(TimeSignature ts, int bars) {
        return ofBars(ts, bars, new PhraseMarking(PhraseConnection.ATTACCA, false));
    }

    /** An N-bar silent phrase with an explicit marking. */
    public static VoidPhrase ofBars(TimeSignature ts, int bars, PhraseMarking marking) {
        return new VoidPhrase(bars, ts.barSixtyFourths(), marking);
    }
}
