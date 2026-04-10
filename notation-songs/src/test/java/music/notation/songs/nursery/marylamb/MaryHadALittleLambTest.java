package music.notation.songs.nursery.marylamb;

import music.notation.songs.PieceTestBase;

import music.notation.structure.PieceContentProvider;

class MaryHadALittleLambTest extends PieceTestBase {
    @Override
    protected PieceContentProvider<?> provider() {
        return new DefaultMaryHadALittleLamb();
    }
}
