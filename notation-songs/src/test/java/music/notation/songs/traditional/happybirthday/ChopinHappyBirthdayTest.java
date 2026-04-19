package music.notation.songs.traditional.happybirthday;

import music.notation.songs.PieceTestBase;
import music.notation.structure.PieceContentProvider;

class ChopinHappyBirthdayTest extends PieceTestBase {
    @Override
    protected PieceContentProvider<?> provider() {
        return new ChopinHappyBirthday();
    }
}
