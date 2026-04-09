package music.notation.songs.rock.novemberstorm;

import music.notation.songs.PieceTestBase;

import music.notation.structure.PieceContentProvider;

class NovemberStormTest extends PieceTestBase {
    @Override
    protected PieceContentProvider<?> provider() {
        return new DefaultNovemberStorm();
    }
}
