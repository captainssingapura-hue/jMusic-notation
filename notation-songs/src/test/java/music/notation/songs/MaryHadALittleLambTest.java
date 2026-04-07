package music.notation.songs;

import music.notation.structure.PieceContentProvider;

class MaryHadALittleLambTest extends PieceTestBase {
    @Override
    protected PieceContentProvider<?> provider() {
        return new DefaultMaryHadALittleLamb();
    }
}
