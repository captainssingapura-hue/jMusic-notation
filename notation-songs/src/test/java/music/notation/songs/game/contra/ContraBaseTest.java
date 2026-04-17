package music.notation.songs.game.contra;

import music.notation.songs.PieceTestBase;
import music.notation.structure.PieceContentProvider;

class ContraBaseTest extends PieceTestBase {

    @Override
    protected PieceContentProvider<?> provider() {
        return new DefaultContraBase();
    }
}
