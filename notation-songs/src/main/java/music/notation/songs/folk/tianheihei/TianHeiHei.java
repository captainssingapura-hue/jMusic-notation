package music.notation.songs.folk.tianheihei;

import music.notation.structure.MusicalPiece;

/** Identity for <em>天黑黑</em> (Tian Hei Hei) — Taiwanese folk song. */
public record TianHeiHei() implements MusicalPiece {
    @Override public String title()    { return "天黑黑 (Tian Hei Hei)"; }
    @Override public String composer() { return "Stefanie Sun"; }
}
