package music.notation.songs;

import music.notation.structure.MusicalPiece;

public record TwoTigers() implements MusicalPiece {
    @Override public String title()    { return "Two Tigers (\u4e24\u53ea\u8001\u864e)"; }
    @Override public String composer() { return "Traditional"; }
}
