package music.notation.songs.folk.zainayaoyuan;

import music.notation.songs.PieceTestBase;
import music.notation.structure.PieceContentProvider;

class PianoZaiNaYaoYuanTest extends PieceTestBase {
    @Override
    protected PieceContentProvider<?> provider() {
        return new PianoZaiNaYaoYuan();
    }
}
