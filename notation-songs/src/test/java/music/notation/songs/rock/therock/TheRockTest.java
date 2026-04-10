package music.notation.songs.rock.therock;

import music.notation.songs.PieceTestBase;

import music.notation.structure.PieceContentProvider;

class TheRockTest extends PieceTestBase {
    @Override
    protected PieceContentProvider<?> provider() {
        return new DefaultTheRock();
    }
}
