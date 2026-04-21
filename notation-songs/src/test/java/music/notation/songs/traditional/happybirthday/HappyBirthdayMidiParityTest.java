package music.notation.songs.traditional.happybirthday;

import music.notation.play.MidiPlayer;
import music.notation.structure.Piece;
import music.notation.structure.PieceContentProvider;
import org.junit.jupiter.api.Test;

import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Parity guard for all five Happy Birthday variants. Each provider renders
 * the same MIDI bytes before and after the sectional migration. Any change
 * that alters audible output fails here.
 */
class HappyBirthdayMidiParityTest {

    /** Golden hashes captured from the flat-constructed pieces. */
    private static final Map<String, String> EXPECTED = Map.of(
            "Default",   "87afed2f4bf8edf1109df79e02af72fe5f28d99a76ec2ab4816a2b397dd4b41f",
            "Mozart",    "7fd21a81dcad11593447b45c2995f73f4f0222eaf660d25d45daa700b813ada9",
            "Chopin",    "6ac79d69c335806f60a90da27787a2a86b8fd913f210960bdc26ef8144c0314c",
            "Beethoven", "b150b7ded5a66bd856c22d0baff5d700523e1dcc26e0e0d9cb23b075d43b1bc0",
            "Brahms",    "fdc22c07140e0dec1184cb0ed82c0bf9fa1442252f6477ba69a82c3a2a4f3e5a"
    );

    private static final Map<String, PieceContentProvider<HappyBirthday>> VARIANTS = Map.of(
            "Default",   new DefaultHappyBirthday(),
            "Mozart",    new MozartHappyBirthday(),
            "Chopin",    new ChopinHappyBirthday(),
            "Beethoven", new BeethovenHappyBirthday(),
            "Brahms",    new BrahmsHappyBirthday()
    );

    @Test
    void allVariantsHaveStableMidiBytes() throws Exception {
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
                    name + " Happy Birthday MIDI output changed unexpectedly.");
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
