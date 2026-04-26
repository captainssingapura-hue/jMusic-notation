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
        // Re-recorded after Phase 1 ornament-rendering retirement
        // (.docs/microtiming.md / radiant-painting-swan.md). TwinkleStar uses
        // MORDENT/TURN/TRILL/TREMOLO/LOWER_MORDENT — all now play as plain notes.
        EXPECTED.put("TwinkleStar",     "42366ddfd7b354ac5b79b996d15812add458becdc8d73138d03878923282d6f9");
        // Re-recorded after Phase 1 ornament-rendering retirement.
        EXPECTED.put("MaryLamb",        "c97039449617448828579fdbc76a12eadb517f79f8354c231735b7257675a4b5");
        // Re-recorded after Phase 1 ornament-rendering retirement.
        EXPECTED.put("AntsGoMarching",  "fc9f9523c22ca608a966eb02a581f90794cd5c1d96c9ea0c56f75f6baf2a8d9c");
        EXPECTED.put("OdeToJoy",        "e5165fc69546b6fffada5301ff8147c98d4857b134a7a3672599893ccdfc9096");
        EXPECTED.put("PachelbelCanon",  "17e52e3f5f8dfab38f03d36a7417afcec35f584e47867f47b8ce5f4489cb7873");
        EXPECTED.put("BlueLotus",       "b61a01a4543fbd69589a5398d556bce59d5cdcb046af0c7954aad56c2e3c56cc");
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
