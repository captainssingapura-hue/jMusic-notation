package music.notation.songs;

import music.notation.structure.PieceContentProvider;

class TwoTigersCanonTest extends PieceTestBase {
    @Override
    protected PieceContentProvider<?> provider() {
        return new DefaultTwoTigersCanon();
    }
}
