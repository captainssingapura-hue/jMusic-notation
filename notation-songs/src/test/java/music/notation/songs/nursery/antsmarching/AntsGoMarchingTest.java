package music.notation.songs.nursery.antsmarching;

import music.notation.songs.PieceTestBase;

import music.notation.structure.PieceContentProvider;

class AntsGoMarchingTest extends PieceTestBase {
    @Override
    protected PieceContentProvider<?> provider() {
        return new DefaultAntsGoMarching();
    }
}
