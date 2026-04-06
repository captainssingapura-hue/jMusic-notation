package music.notation.songs;

import music.notation.structure.PieceContentProvider;

class TheRockTest extends PieceTestBase {
    @Override
    protected PieceContentProvider<?> provider() {
        return new DefaultTheRock();
    }
}
