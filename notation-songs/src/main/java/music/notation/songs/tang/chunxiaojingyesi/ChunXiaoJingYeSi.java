package music.notation.songs.tang.chunxiaojingyesi;

import music.notation.structure.MusicalPiece;

/**
 * Identity for <em>春晓 / 静夜思</em> — a setting of two Tang 五言绝句
 * (孟浩然《春晓》, 李白《静夜思》) in which the second poem is the
 * remembered night inside the first poem's morning.
 */
public record ChunXiaoJingYeSi() implements MusicalPiece {
    @Override public String title()    { return "春晓 / 静夜思 (Spring Dawn / Quiet Night Thoughts)"; }
    @Override public String composer() { return "孟浩然 · 李白 (text)"; }
}
