package music.notation.songs.nursery.twotigers;

import music.notation.songs.PieceTestBase;
import music.notation.structure.PieceContentProvider;

class RockTwoTigersTest extends PieceTestBase {
    @Override
    protected PieceContentProvider<?> provider() {
        return new RockTwoTigers();
    }
}
