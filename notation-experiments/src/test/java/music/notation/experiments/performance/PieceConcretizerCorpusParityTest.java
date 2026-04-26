package music.notation.experiments.performance;

import music.notation.performance.ConcreteNote;
import music.notation.performance.DrumNote;
import music.notation.performance.MidiCodec;
import music.notation.performance.Performance;
import music.notation.performance.PitchedNote;
import music.notation.performance.Track;
import music.notation.play.MidiPlayer;
import music.notation.play.PieceConcretizer;
import music.notation.songs.classical.odetojoy.DefaultOdeToJoy;
import music.notation.songs.classical.pachelbelcanon.DefaultPachelbelCanon;
import music.notation.songs.nursery.antsmarching.DefaultAntsGoMarching;
import music.notation.songs.nursery.marylamb.DefaultMaryHadALittleLamb;
import music.notation.songs.nursery.twinklestar.DefaultTwinkleStar;
import music.notation.structure.Piece;
import music.notation.structure.PieceContentProvider;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Phase-2 parity check: for each clean corpus song (no ornaments, no slur
 * extension, no dynamics — see {@code .docs/microtiming.md}), verify that
 * {@link PieceConcretizer#concretize} produces a {@link Performance} that is
 * structurally equivalent to the legacy {@link MidiPlayer}+{@link MidiCodec}
 * import path.
 *
 * <p>Comparison is <b>structural</b>, not strict equals: the legacy MIDI path
 * does not write Track Name meta events, so reimported tracks end up named
 * {@code track_0/track_1/...}, while the concretizer preserves authored names.
 * Likewise, the legacy MIDI path represents tempo via meta events whose ms
 * positions go through tick rounding. We assert that, in declared track order:
 * track count matches, kind matches, note count matches, and each note's
 * (tickMs, durationMs, midi/piece) matches within ±2 ms of rounding tolerance.
 * Tie flags are not asserted (the legacy path doesn't carry them).</p>
 */
class PieceConcretizerCorpusParityTest {

    @TestFactory
    Stream<DynamicTest> parityWithMidiPathOnCleanCorpus() {
        record Case(String name, PieceContentProvider<?> provider) {}

        var cases = List.of(
                new Case("TwinkleStar",     new DefaultTwinkleStar()),
                new Case("MaryLamb",        new DefaultMaryHadALittleLamb()),
                new Case("AntsGoMarching",  new DefaultAntsGoMarching()),
                new Case("OdeToJoy",        new DefaultOdeToJoy()),
                new Case("PachelbelCanon",  new DefaultPachelbelCanon())
        );

        return cases.stream().map(c ->
                DynamicTest.dynamicTest(c.name(), () -> assertParity(c.name(), c.provider())));
    }

    /** Tolerance in ms for tempo-tick-rounding artifacts in the legacy MIDI path. */
    private static final long TICK_TOLERANCE_MS = 2;

    private static void assertParity(String name, PieceContentProvider<?> provider) throws Exception {
        Piece piece = provider.create();

        Performance fromConcretizer = PieceConcretizer.concretize(piece);
        Performance fromMidiPath = importViaMidi(piece);

        List<String> diffs = diagnose(fromConcretizer, fromMidiPath);
        if (!diffs.isEmpty()) {
            fail("Parity mismatch for " + name + ":\n  " + String.join("\n  ", diffs));
        }
    }

    private static Performance importViaMidi(Piece piece) throws Exception {
        Sequence seq = MidiPlayer.buildSequence(piece);
        var baos = new ByteArrayOutputStream();
        int[] types = MidiSystem.getMidiFileTypes(seq);
        int fileType = (types.length > 1) ? 1 : types[0];
        MidiSystem.write(seq, fileType, baos);
        // Round-trip the import once more to canonicalize (matches the lossy normalization
        // semantics fromMidi imposes on first read).
        Performance first = MidiCodec.fromMidi(baos.toByteArray());
        return MidiCodec.fromMidi(MidiCodec.toMidi(first));
    }

    private static List<String> diagnose(Performance a, Performance b) {
        List<String> diffs = new ArrayList<>();
        if (a.score().tracks().size() != b.score().tracks().size()) {
            diffs.add("Track count: concretizer=" + a.score().tracks().size()
                    + ", midiPath=" + b.score().tracks().size());
            return diffs;
        }
        int n = a.score().tracks().size();
        for (int i = 0; i < n; i++) {
            Track ta = a.score().tracks().get(i);
            Track tb = b.score().tracks().get(i);
            if (ta.kind() != tb.kind()) {
                diffs.add("Track[" + i + "] (" + ta.id().name() + ") kind: concretizer="
                        + ta.kind() + ", midiPath=" + tb.kind());
                if (diffs.size() >= 3) return diffs;
            }
            if (ta.notes().size() != tb.notes().size()) {
                diffs.add("Track[" + i + "] (" + ta.id().name() + ") note count: concretizer="
                        + ta.notes().size() + ", midiPath=" + tb.notes().size());
                if (diffs.size() >= 3) return diffs;
                continue;
            }
            for (int j = 0; j < ta.notes().size(); j++) {
                ConcreteNote na = ta.notes().get(j);
                ConcreteNote nb = tb.notes().get(j);
                String diff = noteDiff(na, nb);
                if (diff != null) {
                    diffs.add("Track[" + i + "] (" + ta.id().name() + ") note[" + j + "]: " + diff);
                    if (diffs.size() >= 3) return diffs;
                }
            }
        }
        return diffs;
    }

    /** Compare two notes ignoring tied flag; allow ±TICK_TOLERANCE_MS on timing. */
    private static String noteDiff(ConcreteNote a, ConcreteNote b) {
        if (a.getClass() != b.getClass()) return "kind " + a + " vs " + b;
        if (Math.abs(a.tickMs() - b.tickMs()) > TICK_TOLERANCE_MS) {
            return "tickMs " + a.tickMs() + " vs " + b.tickMs();
        }
        if (Math.abs(a.durationMs() - b.durationMs()) > TICK_TOLERANCE_MS) {
            return "durationMs " + a.durationMs() + " vs " + b.durationMs() + " (at tickMs " + a.tickMs() + ")";
        }
        if (a instanceof PitchedNote pa && b instanceof PitchedNote pb) {
            if (pa.midi() != pb.midi()) return "midi " + pa.midi() + " vs " + pb.midi();
        } else if (a instanceof DrumNote da && b instanceof DrumNote db) {
            if (da.piece() != db.piece()) return "piece " + da.piece() + " vs " + db.piece();
        }
        return null;
    }
}
