package music.notation.songs.traditional.happybirthday;

import music.notation.structure.MusicalPiece;

/** Identity for the traditional "Happy Birthday to You" song. */
public record HappyBirthday() implements MusicalPiece {
    @Override public String title()    { return "Happy Birthday to You"; }
    @Override public String composer() { return "Traditional (Patty & Mildred Hill)"; }
}
