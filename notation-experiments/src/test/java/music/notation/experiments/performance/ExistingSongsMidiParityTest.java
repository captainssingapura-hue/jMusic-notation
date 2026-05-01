package music.notation.experiments.performance;

import music.notation.performance.MidiCodec;
import music.notation.performance.Performance;
import music.notation.play.MidiPlayer;
import music.notation.songs.anthem.internationale.ManualInternationale;
import music.notation.songs.classical.bachinvention.ManualBachInvention13;
import music.notation.songs.classical.furelise.ManualFurElise;
import music.notation.songs.folk.tianheihei.PianoTianHeiHei;
import music.notation.structure.Piece;
import music.notation.structure.PieceContentProvider;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Stress-tests {@link MidiCodec} against real-world complex songs from
 * the {@code notation-songs} corpus.
 *
 * <p>For each song, the test:</p>
 * <ol>
 *   <li>Builds its MIDI {@link Sequence} via the existing
 *       {@link MidiPlayer} pipeline.</li>
 *   <li>Writes the sequence to a {@code byte[]}.</li>
 *   <li>Parses those bytes via {@link MidiCodec#fromMidi} into a
 *       {@link Performance}.</li>
 *   <li>Serialises that Performance back with {@link MidiCodec#toMidi}.</li>
 *   <li>Parses the re-serialised bytes into a second Performance.</li>
 *   <li>Asserts the two compare equal.</li>
 * </ol>
 *
 * <p>The first read normalises the model (velocity stripped to default,
 * articulation discarded, CC/PB skipped); subsequent round-trips are
 * stable. A passing assertion means the codec is lossless for what the
 * model represents.</p>
 *
 * <p>Songs chosen to exercise the corners of MIDI (post Phase 4c.3
 * the corpus shrinks to the four manually-authored survivors):</p>
 * <ul>
 *   <li><b>Internationale</b> — march, multiple horn voices + chord ensemble.</li>
 *   <li><b>BachInvention13</b> — 2-voice counterpoint.</li>
 *   <li><b>FurElise</b> — rondo, dense 16ths.</li>
 *   <li><b>TianHeiHei</b> — folk arrangement with arpeggio harmony.</li>
 * </ul>
 */
class ExistingSongsMidiParityTest {

    @TestFactory
    Stream<DynamicTest> roundTripParityOnComplexSongs() {
        record Case(String name, PieceContentProvider<?> provider) {}

        var cases = List.of(
                new Case("Internationale",   new ManualInternationale()),
                new Case("BachInvention13",  new ManualBachInvention13()),
                new Case("FurElise",         new ManualFurElise()),
                new Case("TianHeiHei",       new PianoTianHeiHei())
        );

        return cases.stream().map(c ->
                DynamicTest.dynamicTest(c.name(), () -> assertRoundTrips(c.provider())));
    }

    private static void assertRoundTrips(PieceContentProvider<?> provider) throws Exception {
        Piece piece = provider.create();
        Sequence seq = MidiPlayer.buildSequence(piece);
        byte[] originalBytes = writeToBytes(seq);

        Performance first = MidiCodec.fromMidi(originalBytes);
        byte[] roundTripBytes = MidiCodec.toMidi(first);
        Performance second = MidiCodec.fromMidi(roundTripBytes);

        assertEquals(first, second, "MIDI round-trip must preserve the modelled content");
    }

    private static byte[] writeToBytes(Sequence seq) throws Exception {
        var baos = new ByteArrayOutputStream();
        int[] types = MidiSystem.getMidiFileTypes(seq);
        int fileType = (types.length > 1) ? 1 : types[0];
        MidiSystem.write(seq, fileType, baos);
        return baos.toByteArray();
    }
}
