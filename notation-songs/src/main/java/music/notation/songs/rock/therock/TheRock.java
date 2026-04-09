package music.notation.songs.rock.therock;

import music.notation.structure.MusicalPiece;

public record TheRock() implements MusicalPiece {
    @Override public String title()    { return "The Rock (Main Theme)"; }
    @Override public String composer() { return "Hans Zimmer"; }
}
