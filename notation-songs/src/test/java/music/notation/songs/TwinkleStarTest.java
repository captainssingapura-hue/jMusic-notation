package music.notation.songs;

import music.notation.structure.PieceContentProvider;

class TwinkleStarTest extends PieceTestBase {
    @Override
    protected PieceContentProvider<?> provider() {
        return new DefaultTwinkleStar();
    }
}
