package music.notation.experiments.pi;

import music.notation.performance.ConcreteNote;
import music.notation.performance.Performance;
import music.notation.performance.PitchedNote;
import music.notation.performance.Track;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * A multi-voice piano-roll TUI for canon-style {@link Performance}s.
 *
 * <p>Columns are the distinct pitches used across <em>all</em> tracks
 * (sorted ascending). Rows are {@link #ROW_MILLIS}-ms slices, time
 * flowing downward. Each voice is rendered with a distinct bracket
 * style so simultaneous voices in different pitches are easy to read at
 * a glance:</p>
 *
 * <pre>
 *   voice 0  attack [Eb4]   sustain  :
 *   voice 1  attack &lt;Eb4&gt;   sustain  ~
 *   voice 2  attack (Eb4)   sustain  .
 *   voice 3+ attack {Eb4}   sustain  -
 * </pre>
 *
 * <p>If two voices land in the same column on the same row, the
 * lower-numbered voice wins display (rare in canons except at unison
 * climaxes). Drum notes are not rendered.</p>
 */
public final class CanonRollDisplay {

    /** Duration represented by each printed row, in milliseconds. */
    public static final int ROW_MILLIS = 250;

    private static final String[] FLAT_NAMES = {
            "C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B"
    };
    private static final String[] OPEN_BRACKETS  = { "[", "<", "(", "{" };
    private static final String[] CLOSE_BRACKETS = { "]", ">", ")", "}" };
    private static final char[]   SUSTAIN_GLYPH  = { ':', '~', '.', '-' };

    /** Column width in characters. Enough for "[Eb4]" + 1 spacer. */
    private static final int COL_WIDTH = 6;

    private final String title;
    private final List<Integer> columnMidi;   // distinct pitches across all tracks, ascending
    private final long totalMs;
    private final PrintStream out;

    public CanonRollDisplay(String title, Performance performance, PrintStream out) {
        this.title = title;
        this.out = out;
        this.columnMidi = performance.score().tracks().stream()
                .flatMap(t -> t.notes().stream())
                .filter(n -> n instanceof PitchedNote)
                .map(n -> ((PitchedNote) n).midi())
                .distinct()
                .sorted()
                .toList();
        long end = 0;
        for (Track t : performance.score().tracks()) {
            for (ConcreteNote n : t.notes()) {
                if (n.offTickMs() > end) end = n.offTickMs();
            }
        }
        this.totalMs = end;
    }

    public CanonRollDisplay(String title, Performance performance) {
        this(title, performance, System.out);
    }

    // ── Public API ─────────────────────────────────────────────────

    public void printHeader() {
        final int laneWidth = columnMidi.size() * COL_WIDTH;
        final String rule = "-".repeat(laneWidth + 14);
        out.println();
        out.println("  +-- " + title + " " + "-".repeat(Math.max(3, 60 - title.length() - 5)));
        out.println("  |");
        out.println("  |  " + pitchHeader() + "    time");
        out.println("  |  " + rule);
    }

    public void printFooter() {
        out.println("  |");
        out.println("  +" + "-".repeat(70));
        out.println();
    }

    /** Render the entire performance row-by-row, no real-time pause. */
    public void printWhole(Performance performance) {
        printHeader();
        for (long t = 0; t <= totalMs; t += ROW_MILLIS) {
            out.println(renderRow(performance, t));
        }
        printFooter();
    }

    /** Render the single row at {@code atMillis} (no header/footer). */
    public void printRow(Performance performance, long atMillis) {
        out.println(renderRow(performance, atMillis));
    }

    // ── Row rendering ──────────────────────────────────────────────

    private String renderRow(Performance performance, long atMillis) {
        // For each pitch column, compute (voiceIndex, isAttack) of the
        // lowest-numbered voice that has a note sounding here. -1 = empty.
        int[] voiceForCol  = new int[columnMidi.size()];
        boolean[] attackForCol = new boolean[columnMidi.size()];
        for (int i = 0; i < voiceForCol.length; i++) voiceForCol[i] = -1;

        List<Track> tracks = performance.score().tracks();
        for (int v = 0; v < tracks.size(); v++) {
            for (ConcreteNote n : tracks.get(v).notes()) {
                if (!(n instanceof PitchedNote pn)) continue;
                long on = pn.tickMs();
                long off = pn.offTickMs();
                // sounding during this row if [on, off) intersects [atMillis, atMillis + ROW_MILLIS)
                long rowEnd = atMillis + ROW_MILLIS;
                if (on < rowEnd && off > atMillis) {
                    int col = columnMidi.indexOf(pn.midi());
                    if (col < 0) continue;
                    boolean attack = (on >= atMillis && on < rowEnd);
                    if (voiceForCol[col] == -1
                            || (attack && !attackForCol[col])
                            || (attack == attackForCol[col] && v < voiceForCol[col])) {
                        voiceForCol[col]  = v;
                        attackForCol[col] = attack;
                    }
                }
            }
        }

        StringBuilder sb = new StringBuilder("  |  ");
        for (int c = 0; c < columnMidi.size(); c++) {
            int v = voiceForCol[c];
            if (v < 0) {
                sb.append(" ".repeat(COL_WIDTH));
            } else {
                int style = Math.min(v, OPEN_BRACKETS.length - 1);
                if (attackForCol[c]) {
                    sb.append(padCell(OPEN_BRACKETS[style] + noteName(columnMidi.get(c))
                            + CLOSE_BRACKETS[style]));
                } else {
                    sb.append(padCell(" " + SUSTAIN_GLYPH[style]));
                }
            }
        }
        sb.append(String.format("    %5.2fs", atMillis / 1000.0));
        return sb.toString();
    }

    // ── Helpers ────────────────────────────────────────────────────

    private static String padCell(String content) {
        if (content.length() >= COL_WIDTH) return content.substring(0, COL_WIDTH);
        return content + " ".repeat(COL_WIDTH - content.length());
    }

    private String pitchHeader() {
        StringBuilder sb = new StringBuilder();
        for (int m : columnMidi) sb.append(padCell(noteName(m)));
        return sb.toString();
    }

    static String noteName(int midi) {
        int pitchClass = Math.floorMod(midi, 12);
        int octave = Math.floorDiv(midi, 12) - 1;
        return FLAT_NAMES[pitchClass] + octave;
    }

    /** Total length in ms across all voices (including delayed entries). */
    public long totalMs() { return totalMs; }

    /** For tests. */
    List<Integer> columnMidi() { return new ArrayList<>(columnMidi); }
}
