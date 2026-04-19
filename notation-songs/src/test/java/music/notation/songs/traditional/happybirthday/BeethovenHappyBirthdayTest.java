package music.notation.songs.traditional.happybirthday;

import music.notation.songs.PieceTestBase;
import music.notation.structure.PieceContentProvider;

class BeethovenHappyBirthdayTest extends PieceTestBase {
    @Override
    protected PieceContentProvider<?> provider() {
        return new BeethovenHappyBirthday();
    }
}
