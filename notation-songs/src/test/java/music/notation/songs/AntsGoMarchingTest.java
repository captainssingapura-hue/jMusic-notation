package music.notation.songs;

import music.notation.structure.PieceContentProvider;

class AntsGoMarchingTest extends PieceTestBase {
    @Override
    protected PieceContentProvider<?> provider() {
        return new DefaultAntsGoMarching();
    }
}
