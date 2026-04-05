package music.notation.songs;

import music.notation.structure.MusicalPiece;

public record AntsGoMarching() implements MusicalPiece {
    @Override public String title()    { return "The Ants Go Marching"; }
    @Override public String composer() { return "Traditional"; }
}
