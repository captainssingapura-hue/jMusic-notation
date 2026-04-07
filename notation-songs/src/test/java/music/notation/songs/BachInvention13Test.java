package music.notation.songs;

import music.notation.structure.PieceContentProvider;

class BachInvention13Test extends PieceTestBase {
    @Override
    protected PieceContentProvider<?> provider() {
        return new DefaultBachInvention13();
    }
}
