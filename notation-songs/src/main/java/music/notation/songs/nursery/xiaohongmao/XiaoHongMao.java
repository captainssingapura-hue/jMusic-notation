package music.notation.songs.nursery.xiaohongmao;

import music.notation.structure.MusicalPiece;

/** Identity for <em>小红帽</em> (Little Red Riding Hood) — Brazilian children's song
 *  ("Chapeuzinho Vermelho"), Chinese-language adaptation widely sung in PRC schools. */
public record XiaoHongMao() implements MusicalPiece {
    @Override public String title()    { return "小红帽 (Little Red Riding Hood)"; }
    @Override public String composer() { return "Chinese folk adaptation"; }
}
