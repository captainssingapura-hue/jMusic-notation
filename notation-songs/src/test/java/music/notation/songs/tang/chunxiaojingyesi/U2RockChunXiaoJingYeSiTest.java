package music.notation.songs.tang.chunxiaojingyesi;

import music.notation.duration.Duration;
import music.notation.performance.ConcreteNote;
import music.notation.performance.DrumNote;
import music.notation.performance.Performance;
import music.notation.performance.PitchedNote;
import music.notation.performance.Track;
import music.notation.play.PieceConcretizer;
import music.notation.songs.tang.chunxiaojingyesi.BluesChunXiaoJingYeSi.Sec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The rock arrangement must keep the piece's dramatic plan (shared
 * form and harmony with the blues version) while speaking the U2
 * idiom: Edge delay figure, picked eighth bass, straightened vocal
 * line, half-time → anthem drums.
 */
class U2RockChunXiaoJingYeSiTest {

    private static final Performance PERF =
            PieceConcretizer.concretize(new U2RockChunXiaoJingYeSi().create());

    private static final String[] NAMES =
            {"C", "C#", "D", "Eb", "E", "F", "F#", "G", "G#", "A", "Bb", "B"};

    private static Track track(String name) {
        return PERF.score().tracks().stream()
                .filter(t -> t.id().name().equals(name)).findFirst().orElseThrow();
    }

    private static List<PitchedNote> notesIn(String trackName, int fromBar, int toBarExcl) {
        Duration from = Duration.of(fromBar, 1), to = Duration.of(toBarExcl, 1);
        return track(trackName).notes().stream()
                .filter(n -> n instanceof PitchedNote).map(n -> (PitchedNote) n)
                .filter(pn -> pn.at().compareDuration(from) >= 0 && pn.at().compareDuration(to) < 0)
                .toList();
    }

    private static String pcsAt(String trackName, int bar) {
        var pcs = new TreeSet<String>();
        for (PitchedNote pn : notesIn(trackName, bar, bar + 1)) pcs.add(NAMES[Math.floorMod(pn.midi(), 12)]);
        return String.join(" ", pcs);
    }

    private static boolean silent(String trackName, int bar) { return notesIn(trackName, bar, bar + 1).isEmpty(); }

    private static PitchedNote lastNote(String trackName, int bar) {
        var ns = notesIn(trackName, bar, bar + 1);
        return ns.get(ns.size() - 1);
    }

    // ── Shared drama ─────────────────────────────────────────────────

    @Test
    void sameFormHarmonyAndTempoIdiom() {
        assertEquals(116, PERF.tempo().changes().get(0).bpm());
        int door = Sec.CHUNXIAO_2.start() + 4;
        assertEquals("A D F",  pcsAt("Edge Guitar", door), "Dm at the door");
        assertEquals("A D F#", pcsAt("Edge Guitar", Sec.CHUNXIAO_1.start() + 4));
        int j2 = Sec.JINGYESI_2.start();
        assertEquals("C E G",   pcsAt("Edge Guitar", j2 + 4), "the lift to C");
        for (Sec s : List.of(Sec.JINGYESI_1, Sec.JINGYESI_2, Sec.JINGYESI_3)) {
            assertEquals("B E G", pcsAt("Organ", s.start() + 7), "Em under 故鄉 — tonic withheld");
        }
        int coda = Sec.CODA.start();
        assertEquals("B E", pcsAt("Edge Guitar", coda + 7), "open fifth E–B on the sus4 tail, never A major");
        assertEquals("A B D E", pcsAt("Organ", coda + 7));
    }

    // ── Idiom ────────────────────────────────────────────────────────

    @Test
    void edgeGuitarPlaysTheDelayFigure() {
        // ♩. ♪ ♩. ♪ in every non-special bar, root · fifth · third · octave.
        for (int b = 0; b < 8; b++) {
            var ns = notesIn("Edge Guitar", b, b + 1);
            if (BluesChunXiaoJingYeSi.breathAt(b)) { assertEquals(3, ns.size(), "breath bar " + (b + 1)); continue; }
            assertEquals(4, ns.size(), "bar " + (b + 1));
            assertTrue(ns.get(0).duration().equalsDuration(Duration.of(3, 8)));
            assertTrue(ns.get(1).duration().equalsDuration(Duration.of(1, 8)));
            assertTrue(ns.get(2).duration().equalsDuration(Duration.of(3, 8)));
            assertTrue(ns.get(3).duration().equalsDuration(Duration.of(1, 8)));
            // Triads end on the octave; seventh chords end on the seventh — either way it climbs.
            assertTrue(ns.get(3).midi() > ns.get(2).midi(), "figure climbs to its last eighth, bar " + (b + 1));
            if (b < 2) assertEquals(ns.get(0).midi() + 12, ns.get(3).midi(), "octave on A, bar " + (b + 1));
            assertTrue(ns.get(0).midi() >= 64, "chimes from E4 up, bar " + (b + 1));
        }
        // Intro: Edge alone for four bars.
        for (int b = 0; b < 4; b++) {
            assertTrue(silent("Bass", b) && silent("Organ", b) && silent("Piano", b) && silent("Voice (F)", b));
        }
    }

    @Test
    void bassDrivesPickedEighthsOnRootAndFifth() {
        int j1 = Sec.JINGYESI_1.start();
        var ns = notesIn("Bass", j1, j1 + 1);
        assertEquals(8, ns.size());
        assertTrue(ns.stream().allMatch(n -> n.duration().equalsDuration(Duration.of(1, 8))));
        assertEquals("A E", pcsAt("Bass", j1));
        assertTrue(silent("Bass", Sec.JINGYESI_3.start() + 3), "stripped: bass out");
    }

    @Test
    void vocalLineIsStraightened() {
        int s = Sec.CHUNXIAO_1.start();
        // No grace-note scoops anywhere in the voice.
        assertTrue(track("Voice (F)").notes().stream()
                .filter(n -> n instanceof PitchedNote).map(n -> (PitchedNote) n)
                .noneMatch(n -> n.duration().compareDuration(Duration.of(1, 16)) < 0), "no graces");
        // 曉 held, 鳥 on the major third and sustained, 少 on E5 held.
        PitchedNote xiao = lastNote("Voice (F)", s + 1);
        PitchedNote niao = lastNote("Voice (F)", s + 3);
        PitchedNote shao = lastNote("Voice (F)", s + 7);
        assertEquals(9, Math.floorMod(xiao.midi(), 12));
        assertEquals(1, Math.floorMod(niao.midi(), 12), "鳥 on C♯ — no blue third in the rock version");
        assertTrue(niao.duration().compareDuration(Duration.of(1, 2)) >= 0, "鳥 sustained");
        assertEquals(76, shao.midi(), "少 rings on E5");
        assertTrue(shao.duration().compareDuration(Duration.of(3, 8)) >= 0, "held to the caesura");
        // The door still turns the melody: 鳥 and 花 go to C♮, 來 to F♮, in §4.
        int d = Sec.CHUNXIAO_2.start();
        assertEquals(0, Math.floorMod(lastNote("Voice (F)", d + 3).midi(), 12));
        assertTrue(pcsAt("Voice (F)", d + 4).contains("F") && !pcsAt("Voice (F)", d + 4).contains("F#"));
        // Male is the female an octave down.
        var f = notesIn("Voice (F)", 0, 88); var m = notesIn("Voice (M)", 0, 88);
        assertEquals(f.size(), m.size());
        for (int i = 0; i < f.size(); i++) assertEquals(f.get(i).midi() - 12, m.get(i).midi());
    }

    @Test
    void drumsGoHalfTimeThenAnthem() {
        var drums = PERF.score().tracks().stream().filter(t -> t.id().name().equals("Drums")).findFirst().orElseThrow();
        assertEquals(0, hitsBetween(drums, 0, 4), "intro: Edge alone");
        assertTrue(hitsBetween(drums, 4, 8) > 0, "ticks from bar 5");
        long verse  = hitsBetween(drums, Sec.CHUNXIAO_1.start(), Sec.CHUNXIAO_1.start() + 7);
        long anthem = hitsBetween(drums, Sec.JINGYESI_2.start(), Sec.JINGYESI_2.start() + 7);
        long night  = hitsBetween(drums, Sec.JINGYESI_1.start(), Sec.JINGYESI_1.start() + 7);
        assertTrue(night < verse, "the night hushes the kit");
        assertTrue(anthem >= verse, "the lift opens it up");
        assertEquals(2, hitsBetween(drums, Sec.CHUNXIAO_3.start() + 7, Sec.CHUNXIAO_3.start() + 8), "stop-time: kick + crash on 1, nothing else");
    }

    private static long hitsBetween(Track drums, int fromBar, int toBarExcl) {
        Duration from = Duration.of(fromBar, 1), to = Duration.of(toBarExcl, 1);
        return drums.notes().stream()
                .filter(n -> n instanceof DrumNote)
                .filter(n -> n.at().compareDuration(from) >= 0 && n.at().compareDuration(to) < 0)
                .count();
    }

    @Test
    void everySectionEndsWithACaesura() {
        for (Sec s : Sec.values()) {
            if (s == Sec.CODA) continue;
            int last = s.start() + s.bars - 1;
            Duration beat4 = Duration.of(last, 1).plus(Duration.of(3, 4));
            for (Track t : PERF.score().tracks()) {
                for (ConcreteNote n : t.notes()) {
                    boolean onBeat4 = n.at().compareDuration(beat4) >= 0
                            && n.at().compareDuration(Duration.of(last + 1, 1)) < 0;
                    assertFalse(onBeat4, t.id().name() + " sounds on beat 4 of bar " + (last + 1) + " (" + s + ")");
                }
            }
        }
    }

    @Test
    void choirClosesOnTheSuspendedFourth() {
        int coda = Sec.CODA.start();
        for (int b = coda; b < coda + 4; b++) assertTrue(silent("Choir", b));
        PitchedNote shao = lastNote("Choir", coda + 7);
        assertEquals(9, Math.floorMod(shao.midi(), 12), "少 on A");
        assertTrue(shao.endAt().equalsDuration(Duration.of(88, 1)));
    }
}
