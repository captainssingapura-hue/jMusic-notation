package music.notation.experiments.performance;

import music.notation.performance.MidiCodec;
import music.notation.performance.Performance;
import music.notation.performance.PerformanceJson;
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
 * JSON round-trip parity over the real song corpus.
 *
 * <p>For each complex song, build the Performance from the existing MIDI
 * pipeline, then assert {@code fromJson(toJson(perf)).equals(perf)}.</p>
 */
class ExistingSongsJsonParityTest {

    @TestFactory
    Stream<DynamicTest> jsonRoundTripParityOnComplexSongs() {
        record Case(String name, PieceContentProvider<?> provider) {}

        var cases = List.of(
                new Case("Internationale",   new ManualInternationale()),
                new Case("BachInvention13",  new ManualBachInvention13()),
                new Case("FurElise",         new ManualFurElise()),
                new Case("TianHeiHei",       new PianoTianHeiHei())
        );

        return cases.stream().map(c ->
                DynamicTest.dynamicTest(c.name(), () -> assertJsonRoundTrips(c.provider())));
    }

    private static void assertJsonRoundTrips(PieceContentProvider<?> provider) throws Exception {
        Piece piece = provider.create();
        Sequence seq = MidiPlayer.buildSequence(piece);
        byte[] midiBytes = writeToBytes(seq);

        Performance perf = MidiCodec.fromMidi(midiBytes);
        Performance back = PerformanceJson.fromJson(PerformanceJson.toJson(perf));

        assertEquals(perf, back, "JSON round-trip must preserve the Performance");
    }

    private static byte[] writeToBytes(Sequence seq) throws Exception {
        var baos = new ByteArrayOutputStream();
        int[] types = MidiSystem.getMidiFileTypes(seq);
        int fileType = (types.length > 1) ? 1 : types[0];
        MidiSystem.write(seq, fileType, baos);
        return baos.toByteArray();
    }
}
