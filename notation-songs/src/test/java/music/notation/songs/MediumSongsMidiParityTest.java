package music.notation.songs;

import music.notation.play.MidiPlayer;
import music.notation.songs.classical.bachinvention.ColdplayBachInvention13;
import music.notation.songs.classical.bachinvention.ManualBachInvention13;
import music.notation.songs.folk.tianheihei.PianoTianHeiHei;
import music.notation.songs.folk.tianheihei.U2RockTianHeiHei;
import music.notation.structure.Piece;
import music.notation.structure.PieceContentProvider;
import org.junit.jupiter.api.Test;

import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Parity guard for the batch of "medium" multi-section songs migrated to
 * sectional form together (TianHeiHei + BachInvention13 variants).
 */
class MediumSongsMidiParityTest {

    private static final Map<String, String> EXPECTED = new LinkedHashMap<>();
    static {
        EXPECTED.put("PianoTianHeiHei",         "1a16dbb04a2e94da02b838b7c3b9452dbad109b3b7f6377ac5442dca8e3b25b7");
        EXPECTED.put("U2RockTianHeiHei",        "d3929d01bf323935ca451810220934633f287d4e47c13b873e57adb88ee30a2a");
        EXPECTED.put("ManualBachInvention13",   "575c86a7033b135fb9668ebb85472a6277c3e69fe4e95e79f9feebfbf1fe4e5d");
        EXPECTED.put("ColdplayBachInvention13", "ba590cbf095cc0ea46a89477b058b22fd84bf6fe28b769cdf659e39392078101");
    }

    private static final Map<String, PieceContentProvider<?>> VARIANTS = new LinkedHashMap<>();
    static {
        VARIANTS.put("PianoTianHeiHei",         new PianoTianHeiHei());
        VARIANTS.put("U2RockTianHeiHei",        new U2RockTianHeiHei());
        VARIANTS.put("ManualBachInvention13",   new ManualBachInvention13());
        VARIANTS.put("ColdplayBachInvention13", new ColdplayBachInvention13());
    }

    @Test
    void allSongsHaveStableMidiBytes() throws Exception {
        boolean anyMissing = false;
        for (var entry : VARIANTS.entrySet()) {
            String name = entry.getKey();
            String expected = EXPECTED.get(name);
            String actual = hashFor(entry.getValue().create());
            if (expected != null && expected.startsWith("__FILL_ME_IN")) {
                System.out.println(name + ": captured hash = " + actual);
                anyMissing = true;
                continue;
            }
            assertEquals(expected, actual,
                    name + " MIDI output changed unexpectedly.");
        }
        if (anyMissing) {
            fail("Some EXPECTED hashes are still placeholders — paste the printed values in.");
        }
    }

    private static String hashFor(Piece piece) throws Exception {
        Sequence seq = MidiPlayer.buildSequence(piece);
        var baos = new ByteArrayOutputStream();
        int[] types = MidiSystem.getMidiFileTypes(seq);
        int fileType = (types.length > 1) ? 1 : types[0];
        MidiSystem.write(seq, fileType, baos);
        var md = MessageDigest.getInstance("SHA-256");
        var hash = md.digest(baos.toByteArray());
        var sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
