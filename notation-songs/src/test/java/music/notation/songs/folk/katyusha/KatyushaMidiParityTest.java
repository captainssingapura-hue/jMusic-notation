package music.notation.songs.folk.katyusha;

import music.notation.play.MidiPlayer;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Parity guard for {@link DefaultKatyusha}. Captures a golden SHA-256 of the
 * serialized MIDI Sequence before the sectional migration; the test fails if
 * the post-migration MIDI bytes don't match.
 */
@Disabled("Phase 3a: byte-level MIDI parity test obsolete. MidiPlayer.buildSequence(Piece) now "
        + "routes through PieceConcretizer + MidiCodec, producing structurally-equivalent but "
        + "byte-different MIDI. Parity contract is now structural Performance equality. "
        + "See .docs/agent-delegation-retrospective.md.")
class KatyushaMidiParityTest {

    private static final String EXPECTED_SHA256 =
            "da799dc0ad9d126799b2701d0598c9be89b9ed8539d29c0c25dc20d96c5a6878";

    @Test
    void midiOutputIsByteStable() throws Exception {
        var piece = new DefaultKatyusha().create();
        Sequence seq = MidiPlayer.buildSequence(piece);

        var baos = new ByteArrayOutputStream();
        int[] types = MidiSystem.getMidiFileTypes(seq);
        int fileType = (types.length > 1) ? 1 : types[0];
        MidiSystem.write(seq, fileType, baos);

        String actual = sha256(baos.toByteArray());
        if ("__FILL_ME_IN__".equals(EXPECTED_SHA256)) {
            System.out.println("KatyushaMidiParityTest: captured hash = " + actual
                    + " (" + baos.size() + " bytes). Paste into EXPECTED_SHA256.");
            return;
        }
        assertEquals(EXPECTED_SHA256, actual,
                "Katyusha MIDI output changed unexpectedly — verify intent and update hash.");
    }

    private static String sha256(byte[] data) throws Exception {
        var md = MessageDigest.getInstance("SHA-256");
        var hash = md.digest(data);
        var sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
