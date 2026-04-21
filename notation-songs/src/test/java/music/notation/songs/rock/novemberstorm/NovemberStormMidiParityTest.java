package music.notation.songs.rock.novemberstorm;

import music.notation.play.MidiPlayer;
import org.junit.jupiter.api.Test;

import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Parity guard for {@link DefaultNovemberStorm}. */
class NovemberStormMidiParityTest {

    private static final String EXPECTED_SHA256 =
            "f2fd25fc4767395dc5d3b492a18ff7d1c579b614a84a6cbc78a06423cc060ba1";

    @Test
    void midiOutputIsByteStable() throws Exception {
        var piece = new DefaultNovemberStorm().create();
        Sequence seq = MidiPlayer.buildSequence(piece);

        var baos = new ByteArrayOutputStream();
        int[] types = MidiSystem.getMidiFileTypes(seq);
        int fileType = (types.length > 1) ? 1 : types[0];
        MidiSystem.write(seq, fileType, baos);

        String actual = sha256(baos.toByteArray());
        if ("__FILL_ME_IN__".equals(EXPECTED_SHA256)) {
            System.out.println("NovemberStormMidiParityTest: captured hash = " + actual
                    + " (" + baos.size() + " bytes). Paste into EXPECTED_SHA256.");
            return;
        }
        assertEquals(EXPECTED_SHA256, actual,
                "NovemberStorm MIDI output changed unexpectedly — verify intent and update hash.");
    }

    private static String sha256(byte[] data) throws Exception {
        var md = MessageDigest.getInstance("SHA-256");
        var hash = md.digest(data);
        var sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
