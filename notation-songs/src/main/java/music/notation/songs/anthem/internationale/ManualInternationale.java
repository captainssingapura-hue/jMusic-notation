package music.notation.songs.anthem.internationale;

import music.notation.play.PlayPiece;
import music.notation.structure.*;

import java.util.List;

import static music.notation.duration.BaseValue.*;
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

        return new Piece(id.title(), id.composer(),
                KEY, TS,
                new Tempo(104, QUARTER),
                List.of(melody(), harmony(), chords()));
    }

    public static void main(final String[] args) throws Exception {
        PlayPiece.play(new ManualInternationale());
    }
}
