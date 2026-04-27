package music.notation.songs.nursery.twotigers;

import music.notation.play.MidiPlayer;
import music.notation.structure.Piece;
import music.notation.structure.PieceContentProvider;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/** Parity guard for all three Two Tigers variants. */
@Disabled("Phase 3a: byte-level MIDI parity test obsolete. MidiPlayer.buildSequence(Piece) now "
        + "routes through PieceConcretizer + MidiCodec, producing structurally-equivalent but "
        + "byte-different MIDI. Parity contract is now structural Performance equality. "
        + "See .docs/agent-delegation-retrospective.md.")
class TwoTigersMidiParityTest {

    private static final Map<String, String> EXPECTED = Map.of(
            "Default", "1a1b63affea916a2b7a071960007f9d31489285bff82a3e8fa7777e7b5c6f01a",
            "Canon",   "999888198f3e85836a8e86fc628ada3dd33b2f5388643c142132b0ccc272da15",
            "Rock",    "0bafea3b96aa09c735dbc60957da08d2cef0ea878a533b6a3ccf8ff3db98b900"
    );

    private static final Map<String, PieceContentProvider<TwoTigers>> VARIANTS = Map.of(
            "Default", new DefaultTwoTigers(),
            "Canon",   new DefaultTwoTigersCanon(),
            "Rock",    new RockTwoTigers()
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
                    name + " Two Tigers MIDI output changed unexpectedly.");
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
