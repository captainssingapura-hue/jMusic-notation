package music.notation.songs.classical.furelise;

import music.notation.structure.MusicalPiece;

/** Identity for Beethoven's <em>Für Elise</em> (WoO 59). */
public record FurElise() implements MusicalPiece {
    @Override public String title()    { return "Für Elise (WoO 59)"; }
    @Override public String composer() { return "Ludwig van Beethoven"; }
}
