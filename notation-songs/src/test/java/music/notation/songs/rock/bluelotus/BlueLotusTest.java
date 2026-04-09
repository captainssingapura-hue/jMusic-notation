package music.notation.songs.rock.bluelotus;

import music.notation.songs.PieceTestBase;

import music.notation.structure.PieceContentProvider;

class BlueLotusTest extends PieceTestBase {
    @Override
    protected PieceContentProvider<?> provider() {
        return new DefaultBlueLotus();
    }
}
