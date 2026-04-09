package music.notation.songs.classical.traumerei;

import music.notation.songs.PieceTestBase;
import music.notation.structure.PieceContentProvider;

class TraumereiTest extends PieceTestBase {
    @Override
    protected PieceContentProvider<?> provider() {
        return new DefaultTraumerei();
    }
}
