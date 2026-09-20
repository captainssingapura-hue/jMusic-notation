package music.notation.guitar;

import music.notation.duration.Duration;

import java.util.List;

/**
 * Plain-text output: ASCII tab plus a note list. Every column is one
 * grid step (a sixteenth, or a thirty-second when the passage needs it),
 * three characters wide, so tab lines stay aligned in any monospace font.
 *
 * <pre>
 *        17              18
 *  hand  6               10
 *  e|------------6--8-|10-13-----------|
 *  B|--------6--8-----|----------------|
 *  …
 * </pre>
 */
public final class TabRenderer {

    private static final int COL_WIDTH = 3;
    private static final int LINE_WIDTH = 110;
    private static final String[] NAMES = {"C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B"};

    private TabRenderer() {}

    // ── documents ────────────────────────────────────────────────────

    /** The whole arrangement: header, full tab, range summaries, note list. */
    public static String render(Arrangement a) {
        StringBuilder sb = new StringBuilder();
        header(sb, a);
        sb.append('\n').append("── Tab ─────────────────────────────────────────────────────\n\n");
        sb.append(tab(a.fingering().notes(), a.bars(), a.meter(), a.request().tuning()));
        sb.append('\n').append("── Practice ranges ─────────────────────────────────────────\n\n");
        for (PracticeRange r : a.ranges()) sb.append(r.summary()).append('\n');
        sb.append('\n').append("── Notes ───────────────────────────────────────────────────\n\n");
        noteList(sb, a.fingering().notes());
        return sb.toString();
    }

    /** One practice range: its summary, its tab, its notes. */
    public static String renderRange(Arrangement a, PracticeRange r) {
        StringBuilder sb = new StringBuilder();
        sb.append(a.title()).append(" · ").append(a.track()).append('\n');
        sb.append(r.summary()).append("\n\n");
        sb.append(tab(r.notes(), r.bars(), a.meter(), a.request().tuning()));
        sb.append('\n');
        noteList(sb, r.notes());
        return sb.toString();
    }

    private static void header(StringBuilder sb, Arrangement a) {
        var t = a.request().tuning();
        var h = a.request().hand();
        sb.append(a.title()).append('\n');
        sb.append("Track: ").append(a.track()).append(" · ").append(a.bars()).append(" · ")
          .append(a.meter().timeSignature().beats()).append('/').append(a.meter().timeSignature().beatValue()).append('\n');
        sb.append("Tuning: ").append(t.name()).append(" (");
        for (int s = t.stringCount(); s >= 1; s--) sb.append(NAMES[Math.floorMod(t.openStrings().get(s - 1), 12)]).append(s > 1 ? " " : "");
        sb.append(")");
        if (t.capo() > 0) sb.append(", capo ").append(t.capo());
        sb.append(" · frets ≤ ").append(h.maxFret()).append(", hand span ").append(h.span())
          .append(" · ").append(a.fingering().notes().size()).append(" notes, ")
          .append(a.fingering().shifts()).append(" position shifts, cost ")
          .append(String.format("%.1f", a.fingering().cost())).append('\n');
    }

    private static void noteList(StringBuilder sb, List<FingeredNote> notes) {
        sb.append(String.format("%-9s %-5s %-6s %-4s%n", "bar.beat", "note", "string", "fret"));
        for (FingeredNote n : notes) {
            sb.append(String.format("%3d.%-5s %-5s %-6d %-4d%s%n",
                    n.bar(), beat(n.beat()), n.pitchName(), n.position().string(), n.position().fret(),
                    n.folded() ? "  (octave folded)" : ""));
        }
    }

    private static String beat(double b) {
        return b == Math.rint(b) ? String.valueOf((int) b) : String.format("%.2f", b).replaceAll("0+$", "");
    }

    // ── tab ──────────────────────────────────────────────────────────

    /** Tab for the given bars; notes outside them are ignored. */
    public static String tab(List<FingeredNote> notes, BarRange bars, Meter meter, Tuning tuning) {
        int colsPerBeat = colsPerBeat(notes, meter);
        int beats = meter.timeSignature().beats();
        int barCols = beats * colsPerBeat;
        int barWidth = barCols * COL_WIDTH + 1;                       // + bar line
        int barsPerLine = Math.max(1, (LINE_WIDTH - 4) / barWidth);
        int strings = tuning.stringCount();

        StringBuilder out = new StringBuilder();
        for (int first = bars.fromBar(); first <= bars.toBar(); first += barsPerLine) {
            int last = Math.min(bars.toBar(), first + barsPerLine - 1);
            int lineBars = last - first + 1;
            int cols = lineBars * barCols;

            // Grid of fret labels per string, plus hand positions.
            String[][] cell = new String[strings][cols];
            String[] handRow = new String[cols];
            int prevHand = Integer.MIN_VALUE;
            for (FingeredNote n : notes) {
                if (n.bar() < first || n.bar() > last) continue;
                int col = column(n, first, barCols, colsPerBeat, meter);
                if (col < 0 || col >= cols) continue;
                int row = n.position().string() - 1;
                cell[row][col] = String.valueOf(n.position().fret());
                if (n.handPosition() != prevHand) { handRow[col] = String.valueOf(n.handPosition()); prevHand = n.handPosition(); }
            }

            // Bar-number row and hand row.
            StringBuilder nums = new StringBuilder("      ");
            for (int b = 0; b < lineBars; b++) nums.append(padRight(String.valueOf(first + b), barWidth));
            out.append(nums.toString().stripTrailing()).append('\n');
            StringBuilder hand = new StringBuilder(" hand ");
            for (int c = 0; c < cols; c++) {
                if (c % barCols == 0 && c > 0) hand.append(' ');
                hand.append(padRight(handRow[c] == null ? "" : handRow[c], COL_WIDTH));
            }
            out.append(hand.toString().stripTrailing()).append('\n');

            // String rows.
            for (int s = 0; s < strings; s++) {
                StringBuilder line = new StringBuilder();
                line.append(padLeft(NAMES[Math.floorMod(tuning.openStrings().get(s), 12)], 4)).append('|');
                for (int c = 0; c < cols; c++) {
                    if (c % barCols == 0 && c > 0) line.append('|');
                    line.append(cell[s][c] == null ? "---" : padDash(cell[s][c]));
                }
                line.append('|');
                out.append(line).append('\n');
            }
            out.append('\n');
        }
        return out.toString();
    }

    /** Sixteenths unless some onset needs thirty-seconds. */
    private static int colsPerBeat(List<FingeredNote> notes, Meter meter) {
        Duration step16 = meter.beatLength().dividedBy(4);
        for (FingeredNote n : notes) {
            if (!onGrid(n.at(), step16)) return 8;
        }
        return 4;
    }

    private static boolean onGrid(Duration at, Duration step) {
        long num = at.numerator() * step.denominator();
        long den = at.denominator() * step.numerator();
        return num % den == 0;
    }

    private static int column(FingeredNote n, int firstBar, int barCols, int colsPerBeat, Meter meter) {
        Duration offset = n.at().minus(meter.startOfBar(firstBar - 1));
        Duration step = meter.beatLength().dividedBy(colsPerBeat);
        long col = Math.floorDiv(offset.numerator() * step.denominator(), offset.denominator() * step.numerator());
        return Math.toIntExact(col);
    }

    private static String padDash(String s) {
        StringBuilder b = new StringBuilder(s);
        while (b.length() < COL_WIDTH) b.append('-');
        return b.toString();
    }

    private static String padRight(String s, int width) {
        StringBuilder b = new StringBuilder(s);
        while (b.length() < width) b.append(' ');
        return b.toString();
    }

    private static String padLeft(String s, int width) {
        StringBuilder b = new StringBuilder();
        while (b.length() + s.length() < width) b.append(' ');
        return b.append(s).toString();
    }
}
