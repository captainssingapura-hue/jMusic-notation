package music.notation.songs.game.contra;

import music.notation.structure.MusicalPiece;

public record ContraBase() implements MusicalPiece {
    @Override public String title() { return "Contra - Base Theme"; }
    @Override public String composer() { return "Kazuki Muraoka"; }
}
