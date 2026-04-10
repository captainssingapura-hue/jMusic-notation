package music.notation.songs.classical.pachelbelcanon;

import music.notation.songs.PieceTestBase;

import music.notation.structure.PieceContentProvider;

class PachelbelCanonTest extends PieceTestBase {
    @Override
    protected PieceContentProvider<?> provider() {
        return new DefaultPachelbelCanon();
    }
}
