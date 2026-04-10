package music.notation.songs.classical.odetojoy;

import music.notation.songs.PieceTestBase;

import music.notation.structure.PieceContentProvider;

class OdeToJoyTest extends PieceTestBase {
    @Override
    protected PieceContentProvider<?> provider() {
        return new DefaultOdeToJoy();
    }
}
