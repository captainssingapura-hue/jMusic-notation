package music.notation.songs;

import music.notation.play.MidiPlayer;
import music.notation.songs.anthem.internationale.ManualInternationale;
import music.notation.songs.anthem.internationale.RockInternationale;
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

/** Parity guard for the Internationale pair migration to sectional form. */
class InternationaleMidiParityTest {

    private static final Map<String, String> EXPECTED = new LinkedHashMap<>();
    static {
        EXPECTED.put("ManualInternationale", "83655e2a8db0494f4d981b6f1f15aa66f631cb6828571996412ce83f73d2fae9");
        EXPECTED.put("RockInternationale",   "290aa5c6430563c84e2a639fa8c50b5e9a0e4d060cc5ecb1e37947679177308e");
    }

    private static final Map<String, PieceContentProvider<?>> VARIANTS = new LinkedHashMap<>();
    static {
        VARIANTS.put("ManualInternationale", new ManualInternationale());
        VARIANTS.put("RockInternationale",   new RockInternationale());
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
