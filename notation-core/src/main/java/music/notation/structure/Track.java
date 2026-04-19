package music.notation.structure;

import music.notation.event.Instrument;
import music.notation.phrase.Phrase;
import music.notation.phrase.PhraseConnection;
import music.notation.phrase.PhraseMetrics;

import java.util.List;

/**
 * A named track carrying musical phrases and a default instrument suggestion.
 *
 * <p>The {@code defaultInstrument} is a hint for cold-start playback.
 * At runtime the user may assign any number of instruments to a track —
 * that mapping lives outside the {@code Track} (e.g. in the player/UI).</p>
 *
 * <p>A track may contain {@code auxTracks} — auxiliary voices that play
 * simultaneously with the main content, sharing the parent's instrument
 * (or using their own). This mirrors the {@link music.notation.phrase.Bar} /
 * {@link music.notation.phrase.AuxBar} relationship at the track level.</p>
 *
 * <p>The constructor validates elision boundaries: whenever a phrase marked
 * {@code ELISION} is followed by another phrase, their trailing and leading
 * paddings must not cause audible-content overlap in the merged bar. A clear
 * {@link IllegalStateException} identifies the offending phrase pair so
 * composition bugs surface up-front, not at playback time.</p>
 */
public record Track(String name, Instrument defaultInstrument, List<Phrase> phrases, List<Track> auxTracks) {
    public Track {
        phrases = List.copyOf(phrases);
        auxTracks = List.copyOf(auxTracks);
        validateElisionBoundaries(name, phrases);
    }

    /** Convenience factory — no aux tracks. */
    public static Track of(String name, Instrument defaultInstrument, List<Phrase> phrases) {
        return new Track(name, defaultInstrument, phrases, List.of());
    }

    /**
     * Validate that every ELISION boundary in the phrase sequence fits within a
     * single bar: {@code trailing_padding_prev + leading_padding_next >= bar_size}.
     * If not, the merged bar would have audible contents colliding — throw with
     * a message identifying the track and the offending phrase indices.
     */
    private static void validateElisionBoundaries(String trackName, List<Phrase> phrases) {
        for (int i = 0; i + 1 < phrases.size(); i++) {
            Phrase prev = phrases.get(i);
            if (prev.marking().connection() != PhraseConnection.ELISION) continue;

            Phrase next = phrases.get(i + 1);
            int barSize  = PhraseMetrics.lastBarSixtyFourths(prev);
            if (barSize == 0) continue; // no bar structure to validate against (e.g. drum phrase)

            int trailing = PhraseMetrics.trailingPaddingSixtyFourths(prev);
            int leading  = PhraseMetrics.leadingPaddingSixtyFourths(next);
            int filler   = trailing + leading - barSize;

            if (filler < 0) {
                throw new IllegalStateException(String.format(
                        "Track '%s': elision overlap between phrase[%d] and phrase[%d]. "
                                + "Previous trailing padding (%d/64) + next leading padding (%d/64) = %d/64, "
                                + "but bar size is %d/64 — audible contents overlap by %d/64. "
                                + "Either shorten the ending bar's audible content, shorten the pickup bar's audible content, "
                                + "or change the phrase's marking from elision() to attacca().",
                        trackName, i, i + 1, trailing, leading, trailing + leading, barSize, -filler));
            }
        }
    }
}
