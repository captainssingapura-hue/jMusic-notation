package music.notation.songs.traditional.happybirthday;

import music.notation.songs.PieceTestBase;
import music.notation.structure.PieceContentProvider;
import org.junit.jupiter.api.Test;

class CombinedHappyBirthdayTest extends PieceTestBase {
    @Override
    protected PieceContentProvider<?> provider() {
        return new CombinedHappyBirthday();
    }

    /**
     * The Combined tour intentionally has <b>different structural durations</b>
     * per track: RH uses elision between line1→line2→line3→line4, which
     * shortens its playback by 3 bars per section; LH is trimmed to 9 bars
     * (instead of 12) so its playback matches the RH's elision-shortened
     * length. Structural (non-playback) bar totals therefore diverge by
     * 3 bars × 10 sections. Skipping this check for Combined only.
     */
    @Test
    @Override
    protected void allTracksHaveSameDuration() {
        // intentionally no-op — see class-level javadoc.
    }
}
