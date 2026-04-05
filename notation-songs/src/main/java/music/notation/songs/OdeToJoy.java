package music.notation.songs;

import music.notation.structure.MusicalPiece;

public record OdeToJoy() implements MusicalPiece {
    @Override public String title()    { return "Ode to Joy"; }
    @Override public String composer() { return "Beethoven"; }
}
