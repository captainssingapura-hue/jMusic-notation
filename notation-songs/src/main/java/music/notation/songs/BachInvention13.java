package music.notation.songs;

import music.notation.structure.MusicalPiece;

public record BachInvention13() implements MusicalPiece {
    @Override public String title()    { return "Invention No. 13 in A Minor (BWV 784)"; }
    @Override public String composer() { return "J.S. Bach"; }
}
