package music.notation.songs.traditional.happybirthday;

import music.notation.phrase.Bar;
import music.notation.phrase.ChordPhrase;
import music.notation.phrase.MelodicPhrase;
import music.notation.phrase.Phrase;
import music.notation.phrase.PhraseConnection;
import music.notation.phrase.PhraseMarking;
import music.notation.phrase.TempoChangeNode;
import music.notation.play.PlayPiece;
import music.notation.structure.*;

import java.util.ArrayList;
import java.util.List;

import static music.notation.duration.BaseValue.QUARTER;
import static music.notation.event.Instrument.ACOUSTIC_GRAND_PIANO;

/**
 * "Happy Birthday" — all five style arrangements played back-to-back, twice.
 *
 * <p>Default → Mozart → Chopin → Beethoven → Brahms, then the whole sequence
 * once more. Tempo changes on-the-fly at every style boundary via embedded
 * {@link TempoChangeNode} markers, so the listener hears the full stylistic
 * tour (~10 minutes total) without stopping.</p>
 *
 * <p>Each style's existing phrases are reused directly: we instantiate the
 * five style providers, extract their Melody / Accompaniment tracks from
 * {@code create()}, and concatenate the phrase lists. A zero-duration
 * "tempo marker" phrase is inserted before each section to shift BPM.</p>
 */
public final class CombinedHappyBirthday implements PieceContentProvider<HappyBirthday> {

    private static final KeySignature KEY = DefaultHappyBirthday.KEY;
    private static final TimeSignature TS = DefaultHappyBirthday.TS;

    private static final int[] TEMPOS = {112, 132, 76, 120, 88};
    //                                    Def  Moz  Cho  Bee  Bra

    /**
     * Each style's melody uses 3 elisions (line1→line2→line3→line4) which
     * shrinks its playback length by 3 bars. To keep the left hand from
     * running past the melody at every section boundary (and compounding
     * drift across 10 sections), we trim each LH phrase to 9 bars — matching
     * the RH's elision-shortened playback length.
     */
    private static final int LH_PLAYBACK_BARS = 9;
    private static final int BAR_SIXTY_FOURTHS = 48;  // 3/4 bar

    @Override public String subtitle() { return "Tour of Styles (×2)"; }

    @Override
    public Piece create() {
        final var id = new HappyBirthday();

        // Fresh instances per call so each pass gets fresh Phrase objects.
        final List<PieceContentProvider<HappyBirthday>> styles = List.of(
                new DefaultHappyBirthday(),
                new MozartHappyBirthday(),
                new ChopinHappyBirthday(),
                new BeethovenHappyBirthday(),
                new BrahmsHappyBirthday()
        );

        final List<Phrase> melodyPhrases = new ArrayList<>();
        final List<Phrase> accompanimentPhrases = new ArrayList<>();

        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < styles.size(); i++) {
                // Each create() call yields a fresh Piece with fresh Phrase objects,
                // so repeated passes don't share mutable state.
                final Piece stylePiece = styles.get(i).create();
                final Track melody = stylePiece.tracks().get(0);
                final Track accomp = stylePiece.tracks().get(1);

                // Insert a zero-duration tempo marker before each section's content.
                melodyPhrases.add(tempoMarker(TEMPOS[i]));
                accompanimentPhrases.add(tempoMarker(TEMPOS[i]));

                melodyPhrases.addAll(melody.phrases());
                // Trim each LH phrase to 9 bars to match the RH's elision-shortened
                // playback length; otherwise LH drifts 3 bars past RH every section.
                for (Phrase p : accomp.phrases()) {
                    accompanimentPhrases.add(trimToPlaybackBars(p));
                }
            }
        }

        // Starting tempo matches Default (the first section). Subsequent sections
        // shift via the embedded TempoChangeNode markers.
        return new Piece(id.title(), id.composer(), KEY, TS,
                new Tempo(TEMPOS[0], QUARTER),
                List.of(
                        Track.of("Melody",        ACOUSTIC_GRAND_PIANO, melodyPhrases),
                        Track.of("Accompaniment", ACOUSTIC_GRAND_PIANO, accompanimentPhrases)
                ));
    }

    /** Zero-duration phrase carrying only a {@link TempoChangeNode}. */
    private static MelodicPhrase tempoMarker(int bpm) {
        return new MelodicPhrase(
                List.of(new TempoChangeNode(bpm)),
                new PhraseMarking(PhraseConnection.ATTACCA, false)
        );
    }

    /**
     * Trim a LH phrase to {@value #LH_PLAYBACK_BARS} bars so its playback length
     * matches the RH's elision-shortened length. Preserves the original phrase's
     * marking (typically {@code end()} for CAESURA between sections).
     */
    private static Phrase trimToPlaybackBars(Phrase phrase) {
        // MelodicPhrase with populated bar structure — sublist to first N bars.
        if (phrase instanceof MelodicPhrase mp && !mp.bars().isEmpty()) {
            List<Bar> bars = mp.bars();
            if (bars.size() <= LH_PLAYBACK_BARS) return phrase;
            return MelodicPhrase.fromBars(
                    TS, phrase.marking(),
                    bars.subList(0, LH_PLAYBACK_BARS).toArray(new Bar[0]));
        }
        // ChordPhrase — trim by accumulated duration (chord events are dotted-half
        // stabs = 48sf each, so the first LH_PLAYBACK_BARS chords map 1:1 to bars).
        if (phrase instanceof ChordPhrase cp) {
            final int targetSf = LH_PLAYBACK_BARS * BAR_SIXTY_FOURTHS;
            int count = 0, accumSf = 0;
            for (var c : cp.chords()) {
                if (accumSf >= targetSf) break;
                accumSf += c.duration().sixtyFourths();
                count++;
            }
            return new ChordPhrase(cp.chords().subList(0, count), cp.marking());
        }
        return phrase;
    }

    public static void main(String[] args) throws Exception {
        PlayPiece.play(new CombinedHappyBirthday());
    }
}
