package music.notation.songs.folk.zainayaoyuan;

import music.notation.structure.MusicalPiece;

/** Identity for <em>在那遥远的地方</em> (In That Faraway Place) —
 *  Chinese folk song composed by 王洛宾 (Wang Luobin), 1939. */
public record ZaiNaYaoYuan() implements MusicalPiece {
    @Override public String title()    { return "在那遥远的地方 (In That Faraway Place)"; }
    @Override public String composer() { return "王洛宾 (Wang Luobin)"; }
}
