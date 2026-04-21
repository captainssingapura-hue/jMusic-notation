package music.notation.songs.anthem.internationale;

import music.notation.duration.Duration;
import music.notation.play.PlayPiece;
import music.notation.structure.*;

import java.util.List;

import static music.notation.duration.BaseValue.*;
import static music.notation.event.Instrument.*;
import static music.notation.songs.anthem.internationale.InternationaleTracks.*;

/**
 * The Internationale — Pierre De Geyter (1888), lyrics Eugène Pottier (1871).
 *
 * <p>March arrangement in A major, 4/4.</p>
 */
public final class ManualInternationale implements PieceContentProvider<Internationale> {

    @Override
    public Piece create() {
        final var id = new Internationale();

        final var trackDecls = List.<TrackDecl>of(
                new TrackDecl.MusicTrackDecl("Melody",  FRENCH_HORN),
                new TrackDecl.MusicTrackDecl("Harmony", FRENCH_HORN),
                new TrackDecl.MusicTrackDecl("Chords",  STRING_ENSEMBLE_1)
        );

        final Duration SONG_DURATION = Duration.ofSixtyFourths(BAR_COUNT * TS.barSixtyFourths());

        final var anthem = Section.named("Anthem")
                .duration(SONG_DURATION)
                .timeSignature(TS)
                .track("Melody",  melodyPhrase())
                .track("Harmony", harmonyPhrase())
                .track("Chords",  chordsPhrase())
                .build();

        return Piece.ofSections(id.title(), id.composer(),
                KEY, TS,
                new Tempo(104, QUARTER),
                trackDecls,
                List.of(anthem));
    }

    public static void main(final String[] args) throws Exception {
        PlayPiece.play(new ManualInternationale());
    }
}
