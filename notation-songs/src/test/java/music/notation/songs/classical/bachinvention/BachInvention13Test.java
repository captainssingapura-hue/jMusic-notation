package music.notation.songs.classical.bachinvention;

import music.notation.songs.PieceTestBase;

import music.notation.structure.PieceContentProvider;

class BachInvention13Test extends PieceTestBase {
    @Override
    protected PieceContentProvider<?> provider() {
        return new ManualBachInvention13();
    }
}
