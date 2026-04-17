package music.notation.songs.folk.katyusha;

import music.notation.play.PlayPiece;
import music.notation.structure.*;

import java.util.List;

import static music.notation.duration.BaseValue.*;
import static music.notation.songs.folk.katyusha.KatyushaTracks.*;

/**
 * Katyusha (Катюша) — Matvei Blanter (1938), lyrics Mikhail Isakovsky.
 *
 * <p>Folk arrangement: accordion melody, acoustic guitar chords,
 * acoustic bass. Three verses.</p>
 */
public final class DefaultKatyusha implements PieceContentProvider<Katyusha> {

    @Override
    public Piece create() {
        final var id = new Katyusha();

        return new Piece(id.title(), id.composer(),
                KEY, TS,
                new Tempo(108, QUARTER),
                List.of(melody(3), chords(3), bass(3)));
    }

    public static void main(final String[] args) throws Exception {
        PlayPiece.play(new DefaultKatyusha());
    }
}
