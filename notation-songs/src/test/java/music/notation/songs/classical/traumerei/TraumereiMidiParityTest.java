package music.notation.songs.classical.traumerei;

import music.notation.play.MidiPlayer;
import org.junit.jupiter.api.Test;

import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Parity guard for {@link DefaultTraumerei}. Captures a golden SHA-256 of
 * the serialized MIDI Sequence. Any change to the builder / interpreter /
 * renderer that shifts the piece's MIDI bytes will fail this test.
 *
 * <p>The golden hash was captured before the sectional-structure migration
 * and must remain stable through the migration and any follow-up changes
 * that aren't intended to alter audible output.</p>
 */
class TraumereiMidiParityTest {

    /** Golden hash of MIDI file type 1, captured against the flat-constructed piece. */
    private static final String EXPECTED_SHA256 =
            "4b87132068102f865ff26cdc1d60a3d45d2faff38c010cfc6b4967bc4f0bccc6";

    @Test
    void midiOutputIsByteStable() throws Exception {
        var piece = new DefaultTraumerei().create();
        Sequence seq = MidiPlayer.buildSequence(piece);

        var baos = new ByteArrayOutputStream();
        int[] types = MidiSystem.getMidiFileTypes(seq);
        int fileType = (types.length > 1) ? 1 : types[0];
        MidiSystem.write(seq, fileType, baos);

        String actual = sha256(baos.toByteArray());
        // First run: print the hash, then fill it into EXPECTED_SHA256 above.
        if ("__FILL_ME_IN__".equals(EXPECTED_SHA256)) {
            System.out.println("TraumereiMidiParityTest: captured hash = " + actual
                    + " (" + baos.size() + " bytes). Paste this into EXPECTED_SHA256.");
            return; // skip assertion on first run
        }
        assertEquals(EXPECTED_SHA256, actual,
                "Traumerei MIDI output changed unexpectedly — verify intent and update hash.");
    }

    private static String sha256(byte[] data) throws Exception {
        var md = MessageDigest.getInstance("SHA-256");
        var hash = md.digest(data);
        var sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
