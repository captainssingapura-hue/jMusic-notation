package music.notation.songs;

import music.notation.structure.PieceContentProvider;

class InternationaleTest extends PieceTestBase {
    @Override
    protected PieceContentProvider<?> provider() {
        return new ManualInternationale();
    }
}
