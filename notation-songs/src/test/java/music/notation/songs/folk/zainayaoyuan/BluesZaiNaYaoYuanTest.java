package music.notation.songs.folk.zainayaoyuan;

import music.notation.songs.PieceTestBase;
import music.notation.structure.PieceContentProvider;

class BluesZaiNaYaoYuanTest extends PieceTestBase {
    @Override
    protected PieceContentProvider<?> provider() {
        return new BluesZaiNaYaoYuan();
    }
}
