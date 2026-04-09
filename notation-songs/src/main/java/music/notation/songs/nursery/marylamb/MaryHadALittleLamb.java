package music.notation.songs.nursery.marylamb;

import music.notation.structure.MusicalPiece;

public record MaryHadALittleLamb() implements MusicalPiece {
    @Override public String title()    { return "Mary Had a Little Lamb"; }
    @Override public String composer() { return "Traditional"; }
}
