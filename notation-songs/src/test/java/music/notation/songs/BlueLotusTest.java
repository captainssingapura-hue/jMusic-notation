package music.notation.songs;

import music.notation.structure.PieceContentProvider;

class BlueLotusTest extends PieceTestBase {
    @Override
    protected PieceContentProvider<?> provider() {
        return new DefaultBlueLotus();
    }
}
