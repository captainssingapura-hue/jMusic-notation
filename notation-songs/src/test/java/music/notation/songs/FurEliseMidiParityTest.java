package music.notation.songs;

import music.notation.play.MidiPlayer;
import music.notation.songs.classical.furelise.ManualFurElise;
import music.notation.songs.classical.furelise.SoulTechnoFurElise;
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

/** Parity guard for the FurElise pair migration to sectional form. */
class FurEliseMidiParityTest {

    private static final Map<String, String> EXPECTED = new LinkedHashMap<>();
    static {
        EXPECTED.put("ManualFurElise",     "3c4098c4c02c7bd15322922798dde0240414510f875cedef3dcd7220105388d1");
        EXPECTED.put("SoulTechnoFurElise", "af6e6ab54f606089bf51d0e0524fb8663f54e1ce01607af0816a4904be9579ca");
    }

    private static final Map<String, PieceContentProvider<?>> VARIANTS = new LinkedHashMap<>();
    static {
        VARIANTS.put("ManualFurElise",     new ManualFurElise());
        VARIANTS.put("SoulTechnoFurElise", new SoulTechnoFurElise());
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
