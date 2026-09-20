package music.notation.songs.tang.chunxiaojingyesi;

import music.notation.duration.Duration;
import music.notation.performance.ConcreteNote;
import music.notation.performance.Performance;
import music.notation.performance.PitchedNote;
import music.notation.performance.Track;
import music.notation.play.PieceConcretizer;
import music.notation.songs.tang.chunxiaojingyesi.BluesChunXiaoJingYeSi.Sec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The super-blue arrangement: the base's plan in B♭, the singer locked
 * to the blue hexatonic, call-and-response phrasing, solo 1 switchable.
 */
class SuperBlueChunXiaoJingYeSiTest {

    private static final Performance PERF =
            PieceConcretizer.concretize(new SuperBlueChunXiaoJingYeSi().create());

    private static final String[] NAMES =
            {"C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B"};

    /** B♭ · D♭ · E♭ · E · F · A♭ as pitch classes. */
    private static final Set<Integer> HEXATONIC = Set.of(10, 1, 3, 4, 5, 8);
    private static final int OUTSIDE = 0;   // C — 鄉
    private static final int NEIGHBOUR = 9; // A♮ — 知, the lower neighbour worrying the root

    private static Track track(Performance p, String name) {
        return p.score().tracks().stream().filter(t -> t.id().name().equals(name)).findFirst().orElseThrow();
    }

    private static List<PitchedNote> notesIn(String trackName, int fromBar, int toBarExcl) {
        Duration from = Duration.of(fromBar, 1), to = Duration.of(toBarExcl, 1);
        return track(PERF, trackName).notes().stream()
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

    // ── The key ──────────────────────────────────────────────────────

    @Test
    void everySungNoteIsInTheBlueHexatonic_exceptXiangAndZhi() {
        var outside = new java.util.ArrayList<PitchedNote>();
        for (PitchedNote n : notesIn("Voice (F)", 0, 88)) {
            int pc = Math.floorMod(n.midi(), 12);
            if (HEXATONIC.contains(pc)) continue;
            if (pc == NEIGHBOUR) {
                // 知 in 花落知多少: bar 7 of every 春晓 chorus, an eighth, between two B♭s.
                int bar = (int) (n.at().numerator() / n.at().denominator());
                Sec s = BluesChunXiaoJingYeSi.sectionAt(bar);
                assertTrue((s == Sec.CHUNXIAO_1 || s == Sec.CHUNXIAO_2 || s == Sec.CHUNXIAO_3)
                        && BluesChunXiaoJingYeSi.chorusBar(bar) == 6, "A♮ outside 知: " + n.at() + " in " + s);
                assertTrue(n.duration().equalsDuration(Duration.of(1, 8)), "知 is an eighth");
                continue;
            }
            assertEquals(OUTSIDE, pc, "non-hexatonic note " + NAMES[pc] + " at " + n.at());
            outside.add(n);
        }
        // The only C's are 鄉: the last sung note of each 静夜思 line 4 (plus its tie-over),
        // i.e. in bars 7 and 8 of §5, §6, and twice in §8.
        assertFalse(outside.isEmpty(), "鄉 must sing the outside note");
        for (PitchedNote c : outside) {
            int bar = (int) (c.at().numerator() / c.at().denominator());
            Sec s = BluesChunXiaoJingYeSi.sectionAt(bar);
            int cb = BluesChunXiaoJingYeSi.chorusBar(bar);
            boolean xiangBar = switch (s) {
                case JINGYESI_1, JINGYESI_2 -> cb == 6 || cb == 7;
                case JINGYESI_3             -> cb >= 4;        // lines 4, 4
                default -> false;
            };
            assertTrue(xiangBar, "C outside a 鄉 bar: " + c.at() + " in " + s);
        }
    }

    @Test
    void solosAreTheShowcaseShapes_withBlueHints() {
        var s1 = notesIn("Solo 1", Sec.GUITAR_SOLO.start(), Sec.GUITAR_SOLO.start() + 8);
        var s2 = notesIn("Rhodes Solo", Sec.RHODES_SOLO.start(), Sec.RHODES_SOLO.start() + 16);
        // Sixteenth-driven like the base arrangement.
        assertTrue(s1.size() >= 100, "solo 1 density: " + s1.size());
        assertTrue(s2.size() >= 200, "rhodes solo density: " + s2.size());
        // Solo 1 stays inside B♭3–B♭5 — guitar and trumpet both own that range.
        int lo = s1.stream().mapToInt(PitchedNote::midi).min().orElseThrow(), hi = s1.stream().mapToInt(PitchedNote::midi).max().orElseThrow();
        assertEquals(58, lo, "B♭3"); assertEquals(82, hi, "B♭5");
        // The Rhodes climbs to B♭6.
        assertEquals(94, s2.stream().mapToInt(PitchedNote::midi).max().orElseThrow());
        // Blue hints: the blue third, the flat five and the flat seventh each appear in both solos.
        for (var solo : List.of(s1, s2)) {
            var pcs = solo.stream().map(n -> Math.floorMod(n.midi(), 12)).collect(java.util.stream.Collectors.toSet());
            assertTrue(pcs.contains(1), "♭3 D♭"); assertTrue(pcs.contains(4), "♭5 E♮"); assertTrue(pcs.contains(8), "♭7 A♭");
        }
        // The Rhodes solo ends on the outside note, over Fm7 — echoing 鄉.
        assertEquals(OUTSIDE, Math.floorMod(s2.get(s2.size() - 1).midi(), 12));
    }

    // ── Call and response ────────────────────────────────────────────

    @Test
    void voiceSingsOddBarsAndTiesLevelRhymesIntoTheAnswer() {
        for (Sec s : List.of(Sec.CHUNXIAO_1, Sec.CHUNXIAO_2, Sec.CHUNXIAO_3, Sec.JINGYESI_1, Sec.JINGYESI_2, Sec.JINGYESI_3)) {
            for (int i = 0; i < 8; i += 2) {
                int call = s.start() + i, answer = call + 1;
                assertFalse(silent("Voice (F)", call), s + " call bar " + (call + 1));
                // The call fills its bar: first note on the downbeat.
                assertTrue(notesIn("Voice (F)", call, call + 1).get(0).at().equalsDuration(Duration.of(call, 1)));
                // In the answer bar the voice is either silent or only holding a tied rhyme from beat 1.
                var held = notesIn("Voice (F)", answer, answer + 1);
                assertTrue(held.size() <= 1, s + " answer bar " + (answer + 1) + " should hold at most one tied note");
                if (!held.isEmpty()) assertTrue(held.get(0).at().equalsDuration(Duration.of(answer, 1)), "tie lands on the downbeat");
            }
        }
        // 光 and 霜 tie over a half; 曉 and 鳥 carry just one beat into their answer bars.
        int j1 = Sec.JINGYESI_1.start(), c1 = Sec.CHUNXIAO_1.start();
        assertFalse(silent("Voice (F)", j1 + 1), "光 held into the answer");
        assertFalse(silent("Voice (F)", j1 + 3), "霜 held into the answer");
        assertTrue(notesIn("Voice (F)", c1 + 1, c1 + 2).get(0).duration().equalsDuration(Duration.of(1, 4)), "曉 carries one beat");
        assertTrue(notesIn("Voice (F)", c1 + 3, c1 + 4).get(0).duration().equalsDuration(Duration.of(1, 4)), "鳥 carries one beat");
        assertFalse(silent("Voice (F)", c1 + 5), "聲 held into the answer");
    }

    @Test
    void theRhythmIsOneOneAndAHalfOneAndAHalf() {
        int j1 = Sec.JINGYESI_1.start();
        var ns = notesIn("Voice (F)", j1, j1 + 1);
        assertEquals(5, ns.size(), "five syllables in one bar");
        assertTrue(ns.get(0).duration().equalsDuration(Duration.of(1, 4)),  "床 one beat");
        assertTrue(ns.get(1).duration().equalsDuration(Duration.of(3, 8)),  "前 a beat and a half");
        for (int i = 2; i < 5; i++) assertTrue(ns.get(i).duration().equalsDuration(Duration.of(1, 8)), "明月光 in eighths");
        // 1 · ♭3 · 4 · ♭5 · 5 in B♭
        assertEquals("Bb Db Eb E F", String.join(" ", ns.stream().map(n -> NAMES[Math.floorMod(n.midi(), 12)]).toList()));
    }

    // ── The plan survives transposition ──────────────────────────────

    @Test
    void harmonyIsTheBasePlanInBFlat_allDominantMorning() {
        int intro = Sec.INTRO.start();
        assertEquals("Ab Bb D F", pcsAt("Guitar", intro), "B♭7 from bar 1");
        assertEquals("Bb Db Eb G", pcsAt("Guitar", intro + 4), "E♭7 on the IV");
        assertEquals("A C Eb F",   pcsAt("Guitar", intro + 7), "F7 on the V");
        int door = Sec.CHUNXIAO_2.start() + 4;
        assertEquals("Bb Db Eb Gb", pcsAt("Guitar", door), "E♭m7 at the door");
        int j2 = Sec.JINGYESI_2.start();
        assertEquals("Ab Db F", pcsAt("Rhodes RH", j2 + 4), "the lift to D♭");
        for (Sec s : List.of(Sec.JINGYESI_1, Sec.JINGYESI_2, Sec.JINGYESI_3)) {
            assertEquals("Ab C F", pcsAt("Rhodes RH", s.start() + 7), "Fm under 故鄉 — tonic withheld");
        }
        assertEquals("Bb C Eb F", pcsAt("Guitar", Sec.CODA.start() + 7), "F7sus4 tail");
    }

    @Test
    void theDoorIsHarmonic_theVoiceDoesNotChange() {
        // Same D♭ sung over E♭7 (§2) and over E♭m7 (§4).
        int a = Sec.CHUNXIAO_1.start() + 4, b = Sec.CHUNXIAO_2.start() + 4;
        assertEquals(pcsAt("Voice (F)", a), pcsAt("Voice (F)", b));
        assertTrue(pcsAt("Voice (F)", a).contains("Db"));
        assertNotEquals(pcsAt("Guitar", a), pcsAt("Guitar", b));
    }

    @Test
    void xiangHangsOnTheOutsideNote_andShaoClosesOnTheFourth() {
        int j1 = Sec.JINGYESI_1.start();
        var xiang = notesIn("Voice (F)", j1 + 6, j1 + 7);
        assertEquals(OUTSIDE, Math.floorMod(xiang.get(xiang.size() - 1).midi(), 12), "鄉 on C");
        int coda = Sec.CODA.start();
        var choirEnd = notesIn("Choir", coda + 7, coda + 8);
        assertEquals(10, Math.floorMod(choirEnd.get(choirEnd.size() - 1).midi(), 12), "少 on B♭, the suspended fourth of F7sus4");
        assertTrue(choirEnd.get(choirEnd.size() - 1).endAt().equalsDuration(Duration.of(88, 1)));
    }

    @Test
    void vocalRanges() {
        // Graces (the scoops) are excluded — they are approaches, not sung floor.
        var f = notesIn("Voice (F)", 0, 88).stream().filter(n -> n.duration().compareDuration(Duration.of(1, 16)) >= 0).mapToInt(PitchedNote::midi).summaryStatistics();
        assertEquals(58, f.getMin(), "female floor B♭3");
        assertEquals(73, f.getMax(), "female peak D♭5 — the moon");
        var m = notesIn("Voice (M)", 0, 88).stream().filter(n -> n.duration().compareDuration(Duration.of(1, 16)) >= 0).mapToInt(PitchedNote::midi).summaryStatistics();
        assertEquals(46, m.getMin()); assertEquals(61, m.getMax());
    }

    @Test
    void everySectionEndsWithACaesura() {
        for (Sec s : Sec.values()) {
            if (s == Sec.CODA) continue;
            int last = s.start() + s.bars - 1;
            Duration beat4 = Duration.of(last, 1).plus(Duration.of(3, 4));
            for (Track t : PERF.score().tracks()) {
                for (ConcreteNote n : t.notes()) {
                    boolean onBeat4 = n.at().compareDuration(beat4) >= 0 && n.at().compareDuration(Duration.of(last + 1, 1)) < 0;
                    assertFalse(onBeat4, t.id().name() + " sounds on beat 4 of bar " + (last + 1));
                }
            }
        }
    }
}
