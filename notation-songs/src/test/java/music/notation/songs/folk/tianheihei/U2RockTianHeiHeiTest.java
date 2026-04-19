package music.notation.songs.folk.tianheihei;

import music.notation.songs.PieceTestBase;
import music.notation.structure.PieceContentProvider;

class U2RockTianHeiHeiTest extends PieceTestBase {
    @Override
    protected PieceContentProvider<?> provider() {
        return new U2RockTianHeiHei();
    }
}
