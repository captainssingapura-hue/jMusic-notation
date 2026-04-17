package music.notation.songs.folk.katyusha;

import music.notation.songs.PieceTestBase;
import music.notation.structure.PieceContentProvider;

class KatyushaTest extends PieceTestBase {
    @Override
    protected PieceContentProvider<?> provider() {
        return new DefaultKatyusha();
    }
}
