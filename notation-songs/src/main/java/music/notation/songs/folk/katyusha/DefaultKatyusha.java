package music.notation.songs.folk.katyusha;

import music.notation.duration.Duration;
import music.notation.play.PlayPiece;
import music.notation.structure.*;

import java.util.ArrayList;
import java.util.List;

import static music.notation.duration.BaseValue.*;
import static music.notation.event.Instrument.*;
import static music.notation.songs.PieceHelper.*;
import static music.notation.songs.folk.katyusha.KatyushaTracks.*;

/**
 * Katyusha (Катюша) — Matvei Blanter (1938), lyrics Mikhail Isakovsky.
 *
 * <p>Folk arrangement: accordion melody, acoustic guitar chords,
 * acoustic bass. Three verses, each 8 bars in 4/4.</p>
 */
public final class DefaultKatyusha implements PieceContentProvider<Katyusha> {

    private static final int VERSE_COUNT = 3;
    private static final Duration VERSE_DURATION = Duration.ofSixtyFourths(8 * 64); // 8 bars × 4/4

    @Override
    public Piece create() {
        final var id = new Katyusha();

        // Three named tracks, instruments declared once.
        final var trackDecls = List.<TrackDecl>of(
                new TrackDecl.MusicTrackDecl("Melody", ACCORDION),
                new TrackDecl.MusicTrackDecl("Chords", ACOUSTIC_GUITAR_NYLON),
                new TrackDecl.MusicTrackDecl("Bass",   ACOUSTIC_BASS)
        );

        // One section per verse. Last verse's phrases carry the end()
        // marking; earlier verses are attacca() for seamless flow.
        final var sections = new ArrayList<Section>();
        for (int v = 0; v < VERSE_COUNT; v++) {
            var marking = (v < VERSE_COUNT - 1) ? attacca() : end();
            sections.add(Section.named("Verse " + (v + 1))
                    .duration(VERSE_DURATION)
                    .timeSignature(TS)
                    .track("Melody", buildVerse(marking))
                    .track("Chords", buildChordVerse(marking))
                    .track("Bass",   buildBassVerse(marking))
                    .build());
        }

        return Piece.ofSections(id.title(), id.composer(),
                KEY, TS,
                new Tempo(108, QUARTER),
                trackDecls,
                sections);
    }

    public static void main(final String[] args) throws Exception {
        PlayPiece.play(new DefaultKatyusha());
    }
}
