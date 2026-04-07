package music.notation.songs;

import music.notation.structure.PieceContentProvider;

class OdeToJoyTest extends PieceTestBase {
    @Override
    protected PieceContentProvider<?> provider() {
        return new DefaultOdeToJoy();
    }
}
