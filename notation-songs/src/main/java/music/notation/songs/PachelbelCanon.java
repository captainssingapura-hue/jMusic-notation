package music.notation.songs;

import music.notation.structure.MusicalPiece;

public record PachelbelCanon() implements MusicalPiece {
    @Override public String title()    { return "Pachelbel's Canon"; }
    @Override public String composer() { return "Johann Pachelbel"; }
}
