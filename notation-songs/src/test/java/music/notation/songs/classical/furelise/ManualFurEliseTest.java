package music.notation.songs.classical.furelise;

import music.notation.songs.PieceTestBase;
import music.notation.structure.PieceContentProvider;

class ManualFurEliseTest extends PieceTestBase {
    @Override
    protected PieceContentProvider<?> provider() {
        return new ManualFurElise();
    }
}
