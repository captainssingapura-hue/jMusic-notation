package music.notation.songs.rock.bluelotus;

import music.notation.structure.MusicalPiece;

public record BlueLotus() implements MusicalPiece {
    @Override public String title()    { return "Blue Lotus (\u84dd\u83b2\u82b1)"; }
    @Override public String composer() { return "Xu Wei"; }
}
