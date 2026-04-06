package music.notation.songs;

import music.notation.structure.PieceContentProvider;

class PachelbelCanonTest extends PieceTestBase {
    @Override
    protected PieceContentProvider<?> provider() {
        return new DefaultPachelbelCanon();
    }
}
