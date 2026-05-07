package music.notation.songs.nursery.xiaohongmao;

import music.notation.songs.PieceTestBase;
import music.notation.structure.PieceContentProvider;

class XuWeiXiaoHongMaoTest extends PieceTestBase {
    @Override
    protected PieceContentProvider<?> provider() {
        return new XuWeiXiaoHongMao();
    }
}
