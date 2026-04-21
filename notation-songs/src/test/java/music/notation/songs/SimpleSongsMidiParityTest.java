package music.notation.songs;

import music.notation.play.MidiPlayer;
import music.notation.songs.classical.odetojoy.DefaultOdeToJoy;
import music.notation.songs.classical.pachelbelcanon.DefaultPachelbelCanon;
import music.notation.songs.game.contra.DefaultContraBase;
import music.notation.songs.nursery.antsmarching.DefaultAntsGoMarching;
import music.notation.songs.nursery.marylamb.DefaultMaryHadALittleLamb;
import music.notation.songs.nursery.twinklestar.DefaultTwinkleStar;
import music.notation.songs.rock.bluelotus.DefaultBlueLotus;
import music.notation.songs.rock.therock.DefaultTheRock;
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
 * Parity guard for the batch of "simple" single-arrangement songs migrated to
 * sectional form together. Each entry must hash-match its pre-migration MIDI.
 */
class SimpleSongsMidiParityTest {

    private static final Map<String, String> EXPECTED = new LinkedHashMap<>();
    static {
        EXPECTED.put("TwinkleStar",     "e2866722e73fa6a2c3227a298e7fe57c50a4940f1f7d2592c4693f7b4ab4421f");
        EXPECTED.put("MaryLamb",        "9db03f9fcf1682994126d8fa4d6a7c82d0f55914cd21450f1edaa0382cba0238");
        EXPECTED.put("AntsGoMarching",  "6756f4e85d87f8b866407649e6dda1d0e7aa1b48405ece072720f5dd8abebcf8");
        EXPECTED.put("OdeToJoy",        "4be9f709b437e83f5967c8f96f72df35d4c3c53371bb67d83e4c2fafea198e7b");
        EXPECTED.put("PachelbelCanon",  "42b0d0ad52c0a503918002eb4a74ce6c06423244d25be3d6a39c90bfe316d3a7");
        EXPECTED.put("BlueLotus",       "e7e9e5ac91f35b4d6f09e8f99b473f03c0ca1ae5c710c98c4d5e2ab1652b69d0");
        EXPECTED.put("TheRock",         "aa9849bbc114b0b121a374b38a1c4bc821c370ebfce743f7d4b75428feaab399");
        EXPECTED.put("ContraBase",      "894b6fbce12509e25b80ff72ca71d9751908982a5addf2e334414c07ab85057a");
    }

    private static final Map<String, PieceContentProvider<?>> VARIANTS = new LinkedHashMap<>();
    static {
        VARIANTS.put("TwinkleStar",     new DefaultTwinkleStar());
        VARIANTS.put("MaryLamb",        new DefaultMaryHadALittleLamb());
        VARIANTS.put("AntsGoMarching",  new DefaultAntsGoMarching());
        VARIANTS.put("OdeToJoy",        new DefaultOdeToJoy());
        VARIANTS.put("PachelbelCanon",  new DefaultPachelbelCanon());
        VARIANTS.put("BlueLotus",       new DefaultBlueLotus());
        VARIANTS.put("TheRock",         new DefaultTheRock());
        VARIANTS.put("ContraBase",      new DefaultContraBase());
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
