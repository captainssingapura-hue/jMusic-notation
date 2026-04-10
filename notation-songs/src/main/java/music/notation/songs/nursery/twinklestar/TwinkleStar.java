package music.notation.songs.nursery.twinklestar;

import music.notation.structure.MusicalPiece;

/** Identity for <em>Twinkle, Twinkle, Little Star</em>. */
public record TwinkleStar() implements MusicalPiece {
    @Override public String title()    { return "Twinkle, Twinkle, Little Star"; }
    @Override public String composer() { return "Traditional"; }
}
