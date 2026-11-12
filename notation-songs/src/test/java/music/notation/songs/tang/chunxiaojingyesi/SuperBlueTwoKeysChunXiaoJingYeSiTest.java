package music.notation.songs.tang.chunxiaojingyesi;

import music.notation.duration.Duration;
import music.notation.performance.ConcreteNote;
import music.notation.performance.Performance;
import music.notation.performance.PitchedNote;
import music.notation.performance.Track;
import music.notation.play.PieceConcretizer;
import music.notation.songs.tang.chunxiaojingyesi.SuperBlueTwoKeysChunXiaoJingYeSi.Sec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Super blue in two keys: 春晓 in the B♭ hexatonic, 静夜思 in the E♭
 * hexatonic; verse–chorus form for the night; a falling two-line coda.
 */
class SuperBlueTwoKeysChunXiaoJingYeSiTest {

    private static final Performance PERF =
            PieceConcretizer.concretize(new SuperBlueTwoKeysChunXiaoJingYeSi().create());

    private static final String[] NAMES =
            {"C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B"};

    /** B♭ · D♭ · E♭ · E · F · A♭ */
    private static final Set<Integer> HEX_BB = Set.of(10, 1, 3, 4, 5, 8);
    /** E♭ · G♭ · A♭ · A · B♭ · D♭ */
    private static final Set<Integer> HEX_EB = Set.of(3, 6, 8, 9, 10, 1);
    private static final int OUT_BB = 0;     // C
    private static final int OUT_EB = 5;     // F — 鄉 in the night
    private static final int NEIGHBOUR = 9;  // A♮ — 知 in 春晓

    private static Track track(String name) {
        return PERF.score().tracks().stream().filter(t -> t.id().name().equals(name)).findFirst().orElseThrow();
    }

    private static List<PitchedNote> notesIn(String trackName, int fromBar, int toBarExcl) {
        Duration from = Duration.of(fromBar, 1), to = Duration.of(toBarExcl, 1);
        return track(trackName).notes().stream()
                .filter(n -> n instanceof PitchedNote).map(n -> (PitchedNote) n)
                .filter(pn -> pn.at().compareDuration(from) >= 0 && pn.at().compareDuration(to) < 0)
                .toList();
    }

    private static List<PitchedNote> notesIn(String trackName, Sec s) { return notesIn(trackName, s.start(), s.start() + s.bars); }

    private static int barOf(PitchedNote n) { return (int) (n.at().numerator() / n.at().denominator()); }

    private static String pcsAt(String trackName, int bar) {
        var pcs = new TreeSet<String>();
        for (PitchedNote pn : notesIn(trackName, bar, bar + 1)) pcs.add(NAMES[Math.floorMod(pn.midi(), 12)]);
        return String.join(" ", pcs);
    }

    private static boolean silent(String trackName, int bar) { return notesIn(trackName, bar, bar + 1).isEmpty(); }

    private static List<Integer> midis(List<PitchedNote> ns) { return ns.stream().map(PitchedNote::midi).toList(); }

    // ── Form ─────────────────────────────────────────────────────────

    @Test
    void eightyBars_nineSections_noThirdChunxiao() {
        assertEquals(80, SuperBlueTwoKeysChunXiaoJingYeSi.TOTAL);
        assertEquals(80, Sec.CODA.start() + Sec.CODA.bars);
        for (Track t : PERF.score().tracks()) {
            assertTrue(t.notes().stream().allMatch(n -> n.endAt().compareDuration(Duration.of(80, 1)) <= 0), t.id().name() + " overruns");
        }
        // The night is verse, chorus, solo, chorus — then straight into the coda.
        assertEquals(Sec.CHORUS_2, SuperBlueTwoKeysChunXiaoJingYeSi.sectionAt(Sec.CODA.start() - 1));
        assertEquals(List.of(Sec.JINGYESI, Sec.CHORUS_1, Sec.RHODES_SOLO, Sec.CHORUS_2, Sec.CODA),
                List.of(Sec.values()).subList(4, 9));
    }

    // ── Two keys ─────────────────────────────────────────────────────

    @Test
    void chunxiaoSingsTheBFlatHexatonic_jingyesiSingsTheEFlatHexatonic() {
        for (Sec s : List.of(Sec.CHUNXIAO_1, Sec.CHUNXIAO_2)) {
            for (PitchedNote n : notesIn("Voice (F)", s)) {
                int pc = Math.floorMod(n.midi(), 12);
                if (pc == NEIGHBOUR) {   // 知 — an eighth in bar 7 of the 春晓 chorus
                    assertEquals(6, SuperBlueTwoKeysChunXiaoJingYeSi.chorusBar(barOf(n)));
                    assertTrue(n.duration().equalsDuration(Duration.of(1, 8)));
                    continue;
                }
                assertTrue(HEX_BB.contains(pc), s + ": " + NAMES[pc] + " is not in the B♭ hexatonic at " + n.at());
            }
        }
        var xiang = new java.util.ArrayList<PitchedNote>();
        for (Sec s : List.of(Sec.JINGYESI, Sec.CHORUS_1, Sec.CHORUS_2)) {
            for (PitchedNote n : notesIn("Voice (F)", s)) {
                int pc = Math.floorMod(n.midi(), 12);
                if (HEX_EB.contains(pc)) continue;
                assertEquals(OUT_EB, pc, s + ": " + NAMES[pc] + " outside the E♭ hexatonic at " + n.at());
                xiang.add(n);
            }
        }
        // F is sung only as 鄉: the end of every 低頭思故鄉 line (bars 7–8 of the verse, 3–4 and 7–8 of each chorus).
        assertFalse(xiang.isEmpty());
        for (PitchedNote f : xiang) {
            int cb = SuperBlueTwoKeysChunXiaoJingYeSi.chorusBar(barOf(f));
            Sec s = SuperBlueTwoKeysChunXiaoJingYeSi.sectionAt(barOf(f));
            boolean ok = s == Sec.JINGYESI ? (cb == 6 || cb == 7) : (cb == 2 || cb == 3 || cb == 6 || cb == 7);
            assertTrue(ok, "F outside a 鄉 bar: " + f.at() + " in " + s);
        }
        // The verse opens a fourth above the morning: 床前明月光 = E♭ G♭ A♭ A B♭.
        var ns = notesIn("Voice (F)", Sec.JINGYESI.start(), Sec.JINGYESI.start() + 1);
        assertEquals("Eb Gb Ab A Bb", ns.stream().map(n -> NAMES[Math.floorMod(n.midi(), 12)]).collect(Collectors.joining(" ")));
        assertEquals(63, ns.get(0).midi(), "E♭4");
    }

    @Test
    void harmonyPivotsOnBFlat() {
        // Day: dominant B♭ blues.
        assertEquals("Ab Bb D F", pcsAt("Guitar", Sec.INTRO.start()), "B♭7");
        assertEquals("Bb Db Eb G", pcsAt("Guitar", Sec.INTRO.start() + 4), "E♭7");
        assertEquals("A C Eb F", pcsAt("Guitar", Sec.INTRO.start() + 7), "F7");
        // The door: E♭m7 at bar 5, B♭m7 at 7, and the turnaround is B♭7 — V of the coming E♭m.
        int door = Sec.CHUNXIAO_2.start();
        assertEquals("Bb Db Eb Gb", pcsAt("Guitar", door + 4), "E♭m7 at the door");
        assertEquals("Ab Bb Db F", pcsAt("Guitar", door + 6), "B♭m7");
        assertEquals("Ab Bb D F", pcsAt("Guitar", door + 7), "B♭7 — the pivot");
        // Night: E♭ minor blues.
        int v = Sec.JINGYESI.start();
        assertEquals("Bb Eb Gb", pcsAt("Rhodes RH", v), "E♭m");
        assertEquals("Bb Db Eb Gb", pcsAt("Rhodes RH", v + 2), "E♭m7");
        assertEquals("Ab B Eb Gb", pcsAt("Rhodes RH", v + 4), "A♭m7");
        assertEquals("B Bb Eb Gb", pcsAt("Rhodes RH", v + 6), "C♭maj7");
        // Every night section ends on B♭m — the v, tonic withheld; the chorus lifts to G♭ at bar 5.
        for (Sec s : List.of(Sec.JINGYESI, Sec.CHORUS_1, Sec.CHORUS_2)) {
            assertEquals("Bb Db F", pcsAt("Rhodes RH", s.start() + 7), s + " ends on B♭m");
        }
        assertEquals("Bb Db Gb", pcsAt("Rhodes RH", Sec.CHORUS_1.start() + 4), "the lift");
        assertEquals("Bb Db F Gb", pcsAt("Rhodes RH", Sec.CHORUS_1.start() + 5), "G♭maj7");
        // Coda back in B♭, tail on F7sus4; the bass roots follow the same plan.
        int coda = Sec.CODA.start();
        assertEquals("Ab Bb D F", pcsAt("Guitar", coda), "B♭7 opens the coda");
        assertEquals("Bb C Eb F", pcsAt("Guitar", coda + 7), "F7sus4 tail");
        assertEquals(10, Math.floorMod(notesIn("Bass", door + 7, door + 8).get(0).midi(), 12), "bass B♭ under the pivot");
        assertEquals(3,  Math.floorMod(notesIn("Bass", v, v + 1).get(0).midi(), 12), "bass E♭ opens the night");
    }

    // ── Chorus ───────────────────────────────────────────────────────

    @Test
    void chorusClimbs_secondPassHigher_xiangOnTheOutsideNoteAbove() {
        for (Sec s : List.of(Sec.CHORUS_1, Sec.CHORUS_2)) {
            int c = s.start();
            // Each call bar rises: five syllables, strictly ascending until 鄉.
            for (int call : List.of(c, c + 2, c + 4, c + 6)) {
                var ns = notesIn("Voice (F)", call, call + 1);
                assertEquals(5, ns.size(), s + " bar " + (call + 1));
                int rising = (call == c + 2 || call == c + 6) ? 4 : 5;   // 低頭思故鄉 bends onto F at the end
                for (int i = 1; i < rising; i++) assertTrue(ns.get(i).midi() > ns.get(i - 1).midi(), s + " bar " + (call + 1) + " must climb");
            }
            // Pass 2 sits above pass 1: 望明月 tops out on D♭5, then on G♭5.
            int peak1 = notesIn("Voice (F)", c, c + 1).stream().mapToInt(PitchedNote::midi).max().orElseThrow();
            int peak2 = notesIn("Voice (F)", c + 4, c + 5).stream().mapToInt(PitchedNote::midi).max().orElseThrow();
            assertEquals(73, peak1, "D♭5 in the first pass");
            assertEquals(78, peak2, "G♭5 at the top of the second");
            // 鄉 on F5 both times, held into the answer bar.
            for (int x : List.of(c + 2, c + 6)) {
                var line = notesIn("Voice (F)", x, x + 1);
                assertEquals(77, line.get(4).midi(), "鄉 on F5");
                var held = notesIn("Voice (F)", x + 1, x + 2);
                assertEquals(1, held.size()); assertEquals(77, held.get(0).midi());
            }
        }
    }

    @Test
    void choirEchoesTheLastThreeSyllablesInTheAnswerBars() {
        for (Sec s : List.of(Sec.CHORUS_1, Sec.CHORUS_2)) {
            int c = s.start();
            for (int i = 0; i < 8; i += 2) {
                int call = c + i, answer = call + 1;
                assertTrue(silent("Choir", call), s + " choir out of call bar " + (call + 1));
                var voice = notesIn("Voice (F)", call, call + 1);
                var echo = notesIn("Choir", answer, answer + 1);
                var echoPcs = echo.stream().map(n -> Math.floorMod(n.midi(), 12)).collect(Collectors.toSet());
                var lastThree = voice.subList(2, 5).stream().map(n -> Math.floorMod(n.midi(), 12)).collect(Collectors.toSet());
                assertEquals(lastThree, echoPcs, s + " echo in bar " + (answer + 1));
                // On beats 2–3 only: ♪ ♪ ♩, nothing on 1 or 4.
                Duration beat2 = Duration.of(answer, 1).plus(Duration.of(1, 4)), beat4 = Duration.of(answer, 1).plus(Duration.of(3, 4));
                for (PitchedNote n : echo) {
                    assertTrue(n.at().compareDuration(beat2) >= 0 && n.endAt().compareDuration(beat4) <= 0, "echo lands on beats 2–3");
                }
                // The second chorus doubles the echo an octave below.
                int voices = s == Sec.CHORUS_2 ? 2 : 1;
                assertEquals(3 * voices, echo.size(), s + " echo voices");
            }
        }
        // The verse: the choir joins only for 低頭思故鄉.
        int v = Sec.JINGYESI.start();
        for (int i = 0; i < 6; i++) assertTrue(silent("Choir", v + i));
        assertFalse(silent("Choir", v + 6));
    }

    // ── Coda ─────────────────────────────────────────────────────────

    @Test
    void codaIsTheTwoClosingLines_softAndFalling() {
        int c = Sec.CODA.start();
        var voice = notesIn("Voice (F)", c, c + 8);
        // 夜來風雨聲 in bars 1–3, a bar of rest, 花落知多少 on the sus4 tail.
        assertEquals(List.of(68, 65, 63, 61, 58), midis(notesIn("Voice (F)", c, c + 3)), "夜 來 風 雨 聲: A♭4 F4 E♭4 D♭4 B♭3");
        assertTrue(silent("Voice (F)", c + 3), "breath before the tail");
        assertEquals(List.of(63, 61, 60, 61, 58), midis(notesIn("Voice (F)", c + 4, c + 6)), "花 落 知 多 少: E♭4 D♭4 C4 D♭4 B♭3");
        // Falling: the first note is the highest, the last the lowest; 少 holds to the end.
        assertEquals(68, voice.get(0).midi());
        assertEquals(58, voice.get(voice.size() - 1).midi());
        assertTrue(voice.get(voice.size() - 1).endAt().equalsDuration(Duration.of(80, 1)), "少 held to bar 80");
        // Everything in B♭ again.
        for (PitchedNote n : voice) assertTrue(HEX_BB.contains(Math.floorMod(n.midi(), 12)) || Math.floorMod(n.midi(), 12) == OUT_BB);
        // The choir: pads under the first line, rests with the voice, then the same words in unison.
        for (int i = 0; i < 3; i++) assertFalse(silent("Choir", c + i));
        assertTrue(silent("Choir", c + 3));
        assertEquals(midis(notesIn("Voice (F)", c + 4, c + 8)), midis(notesIn("Choir", c + 4, c + 8)), "choir in unison on 花落知多少");
        // Soft: the guitar picks (no riff), the drums drop away after bar 5.
        assertTrue(notesIn("Guitar", c, c + 1).size() >= 8, "fingerstyle eighths");
        assertTrue(track("Drums").notes().stream().noneMatch(n -> n.at().compareDuration(Duration.of(c + 5, 1)) >= 0));
    }

    // ── Solos ────────────────────────────────────────────────────────

    @Test
    void soloOneIsBFlat_withTheIVBarsInEFlat() {
        int s = Sec.SOLO_1.start();
        var all = notesIn("Solo 1", Sec.SOLO_1);
        assertTrue(all.size() >= 100, "density " + all.size());
        assertEquals(58, all.stream().mapToInt(PitchedNote::midi).min().orElseThrow(), "B♭3");
        assertEquals(82, all.stream().mapToInt(PitchedNote::midi).max().orElseThrow(), "B♭5 — guitar or trumpet");
        // Bars 5–6 (E♭7): the E♭ hexatonic plus the chord's G — and E♭'s blue notes G♭ and A♮ are there.
        var iv = notesIn("Solo 1", s + 4, s + 6).stream().map(n -> Math.floorMod(n.midi(), 12)).collect(Collectors.toSet());
        var allowed = new java.util.HashSet<>(HEX_EB); allowed.add(7);
        assertTrue(allowed.containsAll(iv), "IV bars stray outside E♭: " + iv);
        assertTrue(iv.contains(6) && iv.contains(9), "G♭ and A♮ — E♭'s blue third and ♭5");
        // The rest of the solo carries B♭'s own blue notes: D♭, E♮, A♭.
        var rest = notesIn("Solo 1", s, s + 4).stream().map(n -> Math.floorMod(n.midi(), 12)).collect(Collectors.toSet());
        assertTrue(rest.containsAll(Set.of(1, 4, 8)));
        assertFalse(rest.contains(6), "no G♭ in the B♭ bars");
    }

    @Test
    void rhodesSoloIsEFlat_withTheVBarsInBFlat_endsOnF() {
        int s = Sec.RHODES_SOLO.start();
        var all = notesIn("Rhodes Solo", Sec.RHODES_SOLO);
        assertTrue(all.size() >= 200, "density " + all.size());
        assertEquals(94, all.stream().mapToInt(PitchedNote::midi).max().orElseThrow(), "B♭6");
        for (int chorus = 0; chorus < 2; chorus++) {
            int c = s + chorus * 8;
            // Bars 1–7: E♭ material — E♭'s ♭5 (A♮) appears, B♭'s ♭5 (E♮) and major third (D) do not.
            var eb = notesIn("Rhodes Solo", c, c + 7).stream().map(n -> Math.floorMod(n.midi(), 12)).collect(Collectors.toSet());
            assertTrue(eb.contains(9), "A♮ — E♭'s flat five");
            assertFalse(eb.contains(4) || eb.contains(2), "no E♮ / D in the E♭ bars: " + eb);
            // Bar 8 (B♭m): the B♭ hexatonic.
            var v = notesIn("Rhodes Solo", c + 7, c + 8).stream().map(n -> Math.floorMod(n.midi(), 12)).collect(Collectors.toSet());
            assertTrue(HEX_BB.containsAll(v), "v bar strays outside B♭: " + v);
            assertTrue(v.contains(4), "E♮ against B♭m");
        }
        assertEquals(OUT_EB, Math.floorMod(all.get(all.size() - 1).midi(), 12), "ends on F — the night's outside note");
    }

    // ── Shared discipline ────────────────────────────────────────────

    @Test
    void vocalRanges() {
        var f = notesIn("Voice (F)", 0, 80).stream().mapToInt(PitchedNote::midi).summaryStatistics();
        assertEquals(58, f.getMin(), "B♭3"); assertEquals(78, f.getMax(), "G♭5 — the chorus peak");
        var m = notesIn("Voice (M)", 0, 80).stream().mapToInt(PitchedNote::midi).summaryStatistics();
        assertEquals(46, m.getMin()); assertEquals(66, m.getMax());
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
