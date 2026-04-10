package music.notation.songs.nursery.twinklestar;

import music.notation.songs.PieceTestBase;

import music.notation.structure.PieceContentProvider;

class TwinkleStarTest extends PieceTestBase {
    @Override
    protected PieceContentProvider<?> provider() {
        return new DefaultTwinkleStar();
    }
}
