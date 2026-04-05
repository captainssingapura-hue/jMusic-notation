package music.notation.songs;

import music.notation.structure.MusicalPiece;

public record TwoTigersCanon() implements MusicalPiece {
    @Override public String title()    { return "Two Tigers Canon (\u4e24\u53ea\u8001\u864e\u5361\u519c)"; }
    @Override public String composer() { return "Traditional"; }
}
