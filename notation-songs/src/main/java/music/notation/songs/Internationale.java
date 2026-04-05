package music.notation.songs;

import music.notation.structure.MusicalPiece;

/** Identity for <em>The Internationale</em>. */
public record Internationale() implements MusicalPiece {
    @Override public String title()    { return "The Internationale"; }
    @Override public String composer() { return "Pierre De Geyter"; }
}
