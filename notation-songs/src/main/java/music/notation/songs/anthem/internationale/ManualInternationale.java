package music.notation.songs.anthem.internationale;

import music.notation.play.PlayPiece;
import music.notation.structure.*;

import java.util.List;

import static music.notation.duration.BaseValue.*;
import static music.notation.event.Instrument.*;
import static music.notation.songs.PieceHelper.flattenMelodic;
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

        // Phase 4c.2 migration: flatten each phrase into a MelodicTrack
        // via the bar-list shape. AuthorPhrase markings and voice overlays
        // (`.aux(...)`) are dropped — see PieceHelper.flattenMelodic.
        final var melodyTrack  = flattenMelodic("Melody",  FRENCH_HORN,
                List.of(melodyPhrase()));
        final var harmonyTrack = flattenMelodic("Harmony", FRENCH_HORN,
                List.of(harmonyPhrase()));
        final var chordsTrack  = flattenMelodic("Chords",  STRING_ENSEMBLE_1,
                List.of(chordsPhrase()));

        return Piece.ofTrackKinds(id.title(), id.composer(),
                KEY, TS,
                new Tempo(104, QUARTER),
                List.of(melodyTrack, harmonyTrack, chordsTrack),
                List.of());
    }

    public static void main(final String[] args) throws Exception {
        PlayPiece.play(new ManualInternationale());
    }
}
