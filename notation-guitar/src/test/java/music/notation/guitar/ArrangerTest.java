package music.notation.guitar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** End to end on a library piece: the super-blue "Solo 1" (B♭3–B♭5, written for guitar or trumpet). */
class ArrangerTest {

    private static final String TITLE = "春晓 / 静夜思 (Spring Dawn / Quiet Night Thoughts)";
    private static final PieceSource SOURCE = new PieceSource.Library(TITLE, "Super Blue in B♭");

    private static Arrangement soloOne() {
        return Arranger.arrange(ArrangementRequest.of(SOURCE, "Solo 1"));
    }

    @Test
    void wholeTrackDefaultsToTheBarsThatHaveNotes() {
        var a = soloOne();
        assertEquals(new BarRange(17, 24), a.bars(), "Solo 1 is §3, bars 17–24");
        assertEquals("Solo 1", a.track());
        assertTrue(a.title().startsWith(TITLE));
        assertTrue(a.title().contains("Super Blue"));
        assertEquals(4, a.meter().timeSignature().beats());
    }

    @Test
    void everyNoteIsPlacedAndSoundsRight() {
        var a = soloOne();
        var t = a.request().tuning();
        assertTrue(a.fingering().notes().size() >= 100);
        for (FingeredNote n : a.fingering().notes()) {
            assertEquals(n.midi(), t.pitchAt(n.position().string(), n.position().fret()));
            assertTrue(n.bar() >= 17 && n.bar() <= 24);
            assertFalse(n.folded());
        }
        // Simultaneous notes (none expected here, but the rule must hold) never share a string.
        for (int i = 1; i < a.fingering().notes().size(); i++) {
            var p = a.fingering().notes().get(i - 1); var c = a.fingering().notes().get(i);
            if (p.at().equalsDuration(c.at())) assertNotEquals(p.position().string(), c.position().string());
        }
        // A sweep-arpeggio solo should not need a shift on every bar.
        assertTrue(a.fingering().shifts() <= 16, "shifts: " + a.fingering().shifts());
    }

    @Test
    void barRangeAndBreakdownAreHonoured() {
        var req = ArrangementRequest.of(SOURCE, "Solo 1").withBars(new BarRange(21, 22));
        var a = Arranger.arrange(new ArrangementRequest(req.source(), req.track(), req.bars(), null, null, null,
                new BreakdownSpec(BreakdownSpec.Mode.BAR, 1, 1.0)));
        assertEquals(new BarRange(21, 22), a.bars());
        assertEquals(2, a.ranges().size());
        assertEquals(new BarRange(21, 21), a.ranges().get(0).bars());
        assertEquals(new BarRange(22, 22), a.ranges().get(1).bars());
        for (FingeredNote n : a.fingering().notes()) assertTrue(n.bar() == 21 || n.bar() == 22);
        int total = a.ranges().stream().mapToInt(r -> r.notes().size()).sum();
        assertEquals(a.fingering().notes().size(), total, "ranges partition the notes");
    }

    @Test
    void rangesCoverEveryNoteOnce() {
        var a = soloOne();
        var seen = new HashSet<FingeredNote>();
        for (PracticeRange r : a.ranges()) for (FingeredNote n : r.notes()) assertTrue(seen.add(n), "note in two ranges");
        assertEquals(a.fingering().notes().size(), seen.size());
        for (PracticeRange r : a.ranges()) assertTrue(r.bars().bars() <= BreakdownSpec.DEFAULT.maxBars());
    }

    @Test
    void unknownTrackListsTheOthers() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> Arranger.arrange(ArrangementRequest.of(SOURCE, "Kazoo")));
        assertTrue(ex.getMessage().contains("Solo 1"), ex.getMessage());
        assertTrue(ex.getMessage().contains("Rhodes Solo"), ex.getMessage());
    }

    @Test
    void unknownTitleOrVariantListsTheChoices() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> Arranger.arrange(ArrangementRequest.of(new PieceSource.Library("Nope", ""), "Solo 1")));
        assertTrue(ex.getMessage().contains(TITLE));
        var ex2 = assertThrows(IllegalArgumentException.class,
                () -> Arranger.arrange(ArrangementRequest.of(new PieceSource.Library(TITLE, "Polka"), "Solo 1")));
        assertTrue(ex2.getMessage().contains("Super Blue in B♭"), ex2.getMessage());
        // Variant by index works too.
        assertNotNull(Arranger.arrange(ArrangementRequest.of(new PieceSource.Library(TITLE, "2"), "Solo 1")));
    }

    @Test
    void tabTextHasSixStringsBarNumbersAndEveryNote() {
        var a = soloOne();
        String text = TabRenderer.render(a);
        for (String s : List.of("   E|", "   A|", "   D|", "   G|", "   B|")) assertTrue(text.contains(s), "missing string line " + s);
        assertTrue(text.contains("17") && text.contains("24"));
        assertTrue(text.contains("Practice ranges"));
        assertTrue(text.contains("bar.beat"));
        // The note list has one line per note.
        long noteLines = text.lines().filter(l -> l.matches("\\s*\\d+\\.\\S+\\s+\\S+\\s+\\d\\s+\\d+.*")).count();
        assertEquals(a.fingering().notes().size(), noteLines);
    }

    @Test
    void cliWritesTextAndJson(@TempDir Path dir) throws Exception {
        Path req = dir.resolve("req.json");
        ArrangementJson.write(req, ArrangementRequest.of(SOURCE, "Solo 1").withBars(new BarRange(17, 18)));
        Path out = dir.resolve("out");
        var buf = new java.io.ByteArrayOutputStream();
        int code = GuitarArrangeCli.run(new String[]{ req.toString(), "--out", out.toString() }, new java.io.PrintStream(buf, true, "UTF-8"));
        assertEquals(0, code, buf.toString("UTF-8"));
        assertTrue(Files.exists(out.resolve("Solo-1-arrangement.txt")));
        assertTrue(Files.exists(out.resolve("Solo-1-range-01.txt")));
        assertTrue(Files.exists(out.resolve("Solo-1-arrangement.json")));
        // The JSON reads back into the same arrangement.
        var back = ArrangementJson.arrangementFromJson(Files.readString(out.resolve("Solo-1-arrangement.json")));
        assertEquals(new BarRange(17, 18), back.bars());
        assertEquals(back.fingering().notes().size(), Arranger.arrange(back.request()).fingering().notes().size());
        assertTrue(buf.toString("UTF-8").contains("practice ranges"));
    }
}
