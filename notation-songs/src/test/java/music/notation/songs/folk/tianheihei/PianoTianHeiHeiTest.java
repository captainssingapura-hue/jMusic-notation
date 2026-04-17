package music.notation.songs.folk.tianheihei;

import music.notation.songs.PieceTestBase;
import music.notation.structure.PieceContentProvider;

class PianoTianHeiHeiTest extends PieceTestBase {
    @Override
    protected PieceContentProvider<?> provider() {
        return new PianoTianHeiHei();
    }
}
