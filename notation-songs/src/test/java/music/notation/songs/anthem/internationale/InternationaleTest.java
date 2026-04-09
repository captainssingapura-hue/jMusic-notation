package music.notation.songs.anthem.internationale;

import music.notation.songs.PieceTestBase;

import music.notation.structure.PieceContentProvider;

class InternationaleTest extends PieceTestBase {
    @Override
    protected PieceContentProvider<?> provider() {
        return new ManualInternationale();
    }
}
