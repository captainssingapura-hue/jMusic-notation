package music.notation.songs.traditional.happybirthday;

import music.notation.songs.PieceTestBase;
import music.notation.structure.PieceContentProvider;

class MozartHappyBirthdayTest extends PieceTestBase {
    @Override
    protected PieceContentProvider<?> provider() {
        return new MozartHappyBirthday();
    }
}
