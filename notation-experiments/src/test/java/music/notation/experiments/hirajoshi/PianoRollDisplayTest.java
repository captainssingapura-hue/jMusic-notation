package music.notation.experiments.hirajoshi;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CI-safe tests for {@link PianoRollDisplay} — captures stdout, asserts on
 * structure and content. No audio.
 */
class PianoRollDisplayTest {

    @Test
    void noteNameFormatting_isPitchClassCorrect() {
        assertEquals("C4",  PianoRollDisplay.noteName(60));
        assertEquals("Eb4", PianoRollDisplay.noteName(63));
        assertEquals("G4",  PianoRollDisplay.noteName(67));
        assertEquals("Ab4", PianoRollDisplay.noteName(68));
        assertEquals("C5",  PianoRollDisplay.noteName(72));
        assertEquals("D5",  PianoRollDisplay.noteName(74));
    }

    @Test
    void columnOrderingUsesAscendingDistinctPitches() {
        var melody = HirajoshiSong.concretize(HirajoshiSong.demo(), HirajoshiConcretizer.inC());
        var roll = new PianoRollDisplay("Test", melody);

        // Demo uses: C4, D4, Eb4, G4, Ab4 = MIDI 60, 62, 63, 67, 68.
        // No D4 yet — actually line 2 has II at (III?) — confirm from the MIDI sequence test:
        //   60 63 67 63 68 67 63 62 60 62 63 67 63 60  → distinct ascending: 60 62 63 67 68
        assertEquals(List.of(60, 62, 63, 67, 68), roll.columnMidi());
    }

    @Test
    void printWhole_rendersHeaderMelodyAndFooter() {
        var melody = HirajoshiSong.concretize(HirajoshiSong.demo(), HirajoshiConcretizer.inC());
        var captured = capture(out -> new PianoRollDisplay("Demo", melody, out).printWhole(melody));

        // Header: title line
        assertTrue(captured.contains("+-- Demo"), "title should appear in header");
        // Column labels
        assertTrue(captured.contains("C4"));
        assertTrue(captured.contains("Eb4"));
        assertTrue(captured.contains("G4"));
        assertTrue(captured.contains("Ab4"));
        // Attack rows (square brackets)
        assertTrue(captured.contains("[C4]"),  "C4 attack row");
        assertTrue(captured.contains("[Eb4]"), "Eb4 attack row");
        assertTrue(captured.contains("[G4]"),  "G4 attack row");
        assertTrue(captured.contains("[Ab4]"), "Ab4 attack row");
        // Continuation row marker for the half notes at the end
        assertTrue(captured.contains(" :"), "sustained half notes should show continuation");
        // Footer
        assertTrue(captured.contains("+------"), "footer");
    }

    @Test
    void rowCount_matchesTotalRowsExpectedFromMelody() {
        // 12 quarters (1 row each) + 2 halves (2 rows each) = 16 note-related rows
        var melody = HirajoshiSong.concretize(HirajoshiSong.demo(), HirajoshiConcretizer.inC());
        var captured = capture(out -> new PianoRollDisplay("Rows", melody, out).printWhole(melody));

        long noteRows = captured.lines().filter(l -> l.contains("[") || l.contains(" :")).count();
        assertEquals(16, noteRows);
    }

    @Test
    void timeStampsAreMonotonicAndEndAtTotalDuration() {
        var melody = HirajoshiSong.concretize(HirajoshiSong.demo(), HirajoshiConcretizer.inC());
        var captured = capture(out -> new PianoRollDisplay("Time", melody, out).printWhole(melody));

        // Extract all "N.NNs" timestamps in order.
        var timestamps = captured.lines()
                .map(l -> l.trim())
                .filter(l -> l.endsWith("s") && l.matches(".*\\d+\\.\\d{2}s$"))
                .map(l -> l.substring(l.lastIndexOf(' ') + 1))
                .toList();

        double prev = -1.0;
        for (var ts : timestamps) {
            double v = Double.parseDouble(ts.substring(0, ts.length() - 1));
            assertTrue(v >= prev, "non-monotonic timestamp: " + v + " after " + prev);
            prev = v;
        }
        // Last row of the demo starts at 7.50s (the second half of the closing half note).
        assertEquals(7.50, prev, 0.001);
    }

    // ── capture helper ────────────────────────────────────────────────

    private static String capture(java.util.function.Consumer<PrintStream> action) {
        var baos = new ByteArrayOutputStream();
        var ps = new PrintStream(baos, true, StandardCharsets.UTF_8);
        action.accept(ps);
        return baos.toString(StandardCharsets.UTF_8);
    }
}
