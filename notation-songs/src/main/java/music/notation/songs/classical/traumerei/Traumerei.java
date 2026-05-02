package music.notation.songs.classical.traumerei;

import music.notation.structure.MusicalPiece;

public record Traumerei() implements MusicalPiece {
    @Override public String title()    { return "Träumerei (Op. 15 No. 7)"; }
    @Override public String composer() { return "Robert Schumann"; }
}
