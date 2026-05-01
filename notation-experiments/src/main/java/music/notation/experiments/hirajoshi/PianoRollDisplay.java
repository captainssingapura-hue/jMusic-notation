package music.notation.experiments.hirajoshi;

import music.notation.performance.ConcreteNote;
import music.notation.performance.Performance;
import music.notation.performance.PitchedNote;
import music.notation.performance.Track;

import java.io.PrintStream;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A top-down piano-roll TUI driven by a {@link Performance}.
 *
 * <p>Columns are the distinct pitches the performance actually uses, ordered
 * ascending. Rows are 500 ms time slices. A note-on prints its name in
 * square brackets; if the note sustains across further slices, a
 * continuation character is printed in the same column for each extra row.
 * Time flows <b>down</b> the screen.</p>
 *
 * <p>Output is ASCII-only. Pitch spelling uses flats. Drum notes are skipped
 * (this display only renders pitched content).</p>
 */
public final class PianoRollDisplay {

    /** Duration represented by each printed row, in milliseconds. */
    public static final int ROW_MILLIS = 500;

    private static final String[] FLAT_NAMES = {
            "C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B"
    };

    /** Column width in characters. Enough for "[Eb4]" + 1 spacer. */
    private static final int COL_WIDTH = 6;

    private final String title;
    private final List<Integer> columnMidi;   // distinct pitches, ascending
    private final PrintStream out;

    public PianoRollDisplay(String title, Performance performance, PrintStream out) {
        this.title = title;
        this.columnMidi = performance.score().tracks().stream()
                .flatMap(t -> t.notes().stream())
                .filter(n -> n instanceof PitchedNote)
                .map(n -> ((PitchedNote) n).midi())
                .distinct()
                .sorted()
                .toList();
        this.out = out;
    }

    public PianoRollDisplay(String title, Performance performance) {
        this(title, performance, System.out);
    }

    // -- Public API --

    /** Draw the frame + column labels. Call once before any rows. */
    public void printHeader() {
        final int pitchLaneWidth = columnMidi.size() * COL_WIDTH;
        final String rule = "-".repeat(pitchLaneWidth + 14);

        out.println();
        out.println("  +-- " + title + " " + "-".repeat(Math.max(3, 60 - title.length() - 5)));
        out.println("  |");
        out.println("  |  " + pitchHeader() + "    time");
        out.println("  |  " + rule);
    }

    /** Draw the closing border. */
    public void printFooter() {
        out.println("  |");
        out.println("  +" + "-".repeat(70));
        out.println();
    }

    /**
     * Print the entire performance in one shot - no real-time pauses. Useful
     * for tests and for quick visual inspection without waiting for
     * playback.
     */
    public void printWhole(Performance performance) {
        printHeader();
        for (Track track : performance.score().tracks()) {
            for (ConcreteNote n : track.notes()) {
                if (n instanceof PitchedNote note) {
                    printNoteRows(note);
                }
            }
        }
        printFooter();
    }

    private void printNoteRows(PitchedNote note) {
        final int rows = Math.max(1, (int) note.durationMs() / ROW_MILLIS);
        for (int r = 0; r < rows; r++) {
            out.println(renderRow(note.midi(), r == 0, note.tickMs() + (long) r * ROW_MILLIS));
        }
    }

    // -- Real-time staged API (for synced playback) --

    /** Print only the attack row - useful when synchronising with audio. */
    public void printAttack(PitchedNote note) {
        out.println(renderRow(note.midi(), true, note.tickMs()));
    }

    /** Print a continuation row - call once per extra {@link #ROW_MILLIS}. */
    public void printSustain(PitchedNote note, long atMillis) {
        out.println(renderRow(note.midi(), false, atMillis));
    }

    // -- Helpers --

    private String renderRow(int midi, boolean isAttack, long atMillis) {
        final int colIndex = columnMidi.indexOf(midi);
        StringBuilder sb = new StringBuilder("  |  ");
        for (int c = 0; c < columnMidi.size(); c++) {
            if (c == colIndex) {
                sb.append(padCell(isAttack
                        ? "[" + noteName(midi) + "]"
                        : " :"));
            } else {
                sb.append(" ".repeat(COL_WIDTH));
            }
        }
        sb.append(String.format("    %5.2fs", atMillis / 1000.0));
        return sb.toString();
    }

    private static String padCell(String content) {
        if (content.length() >= COL_WIDTH) return content.substring(0, COL_WIDTH);
        return content + " ".repeat(COL_WIDTH - content.length());
    }

    private String pitchHeader() {
        return columnMidi.stream()
                .map(m -> padCell(noteName(m)))
                .collect(Collectors.joining(""));
    }

    static String noteName(int midi) {
        int pitchClass = Math.floorMod(midi, 12);
        int octave = Math.floorDiv(midi, 12) - 1;
        return FLAT_NAMES[pitchClass] + octave;
    }

    /** Exposed for tests. */
    List<Integer> columnMidi() {
        return columnMidi;
    }
}
