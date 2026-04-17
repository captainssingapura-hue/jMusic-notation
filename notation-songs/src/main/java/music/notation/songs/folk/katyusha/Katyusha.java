package music.notation.songs.folk.katyusha;

import music.notation.structure.MusicalPiece;

/** Identity for <em>Katyusha</em> (Катюша). */
public record Katyusha() implements MusicalPiece {
    @Override public String title()    { return "Katyusha (Катюша)"; }
    @Override public String composer() { return "Matvei Blanter"; }
}
