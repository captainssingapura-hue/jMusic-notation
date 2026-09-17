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
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Structural checks on the phase-1 arrangement: the three moves the
 * chart says the piece lives or dies on (the door, the lift, the
 * withheld tonic), plus instrument entrances and accidental spelling.
 */
class BluesChunXiaoJingYeSiTest {

    private static final Performance PERF =
            PieceConcretizer.concretize(new BluesChunXiaoJingYeSi().create());

    private static final String[] NAMES =
            {"C", "C#", "D", "Eb", "E", "F", "F#", "G", "G#", "A", "Bb", "B"};

    private static Track track(Performance p, String name) {
        return p.score().tracks().stream()
                .filter(t -> t.id().name().equals(name)).findFirst().orElseThrow();
    }

    /** Sorted pitch-class names of every note on {@code track} starting inside 0-based bar {@code bar}. */
    private static String pcsAt(Performance p, String trackName, int bar) {
        Duration from = Duration.of(bar, 1), to = Duration.of(bar + 1, 1);
        var pcs = new TreeSet<String>();
        for (ConcreteNote n : track(p, trackName).notes()) {
            if (n instanceof PitchedNote pn
                    && pn.at().compareDuration(from) >= 0
                    && pn.at().compareDuration(to) < 0) {
                pcs.add(NAMES[Math.floorMod(pn.midi(), 12)]);
            }
        }
        return String.join(" ", pcs);
    }

    private static String pcsAt(String trackName, int bar) { return pcsAt(PERF, trackName, bar); }

    private static boolean silent(String trackName, int bar) { return pcsAt(trackName, bar).isEmpty(); }

    @Test
    void formIs88Bars() {
        int sum = 0;
        for (Sec s : Sec.values()) sum += s.bars;
        assertEquals(88, sum);
        assertEquals(88, BluesChunXiaoJingYeSi.TOTAL);
        assertEquals(Sec.CODA, BluesChunXiaoJingYeSi.sectionAt(87));
    }

    @Test
    void theDoor_barFiveOfChunxiaoII_goesMinor() {
        int door = Sec.CHUNXIAO_2.start() + 4;
        assertEquals("A D F", pcsAt("Guitar", door), "Dm under 夜來風雨聲");
        assertEquals("A C E", pcsAt("Guitar", door + 2), "bar 7 already Am");
        // Same bar in the other 春晓 choruses stays major — the door is opened once.
        assertEquals("A D F#", pcsAt("Guitar", Sec.CHUNXIAO_1.start() + 4));
        assertEquals("A D F#", pcsAt("Guitar", Sec.CHUNXIAO_3.start() + 4), "§9: the door stays shut");
    }

    @Test
    void theLift_jingyesiII_barsFiveToEight() {
        int s = Sec.JINGYESI_2.start();
        assertEquals("C E G",   pcsAt("Rhodes RH", s + 4), "C — 舉頭");
        assertEquals("B C E G", pcsAt("Rhodes RH", s + 5), "Cmaj7");
        assertEquals("A C E F", pcsAt("Rhodes RH", s + 6), "Fmaj7 — 低頭");
        assertEquals("B E G",   pcsAt("Rhodes RH", s + 7), "Em");
    }

    @Test
    void tonicIsWithheldUnderGuxiang() {
        for (Sec sec : List.of(Sec.JINGYESI_1, Sec.JINGYESI_2, Sec.JINGYESI_3)) {
            assertEquals("B E G", pcsAt("Rhodes RH", sec.start() + 7), sec + " bar 8 must be Em");
        }
        // Under the Rhodes solo the guitar carries the harmony — still Em at both bar 8s.
        int r = Sec.RHODES_SOLO.start();
        assertEquals("B E G", pcsAt("Guitar", r + 7));
        assertEquals("B E G", pcsAt("Guitar", r + 15));
    }

    @Test
    void majorSideSpellsSharps_minorSideSpellsNaturals() {
        int intro = Sec.INTRO.start();
        assertEquals("A C# E",   pcsAt("Guitar", intro));
        assertEquals("A C# E G", pcsAt("Guitar", intro + 2), "A7 takes G natural");
        assertEquals("B D E G#", pcsAt("Guitar", intro + 7), "E7 keeps G#");
        int j1 = Sec.JINGYESI_1.start();
        assertEquals("A C E",    pcsAt("Rhodes RH", j1),     "Am takes C natural");
        assertEquals("A C E G",  pcsAt("Rhodes RH", j1 + 2), "Am7");
    }

    @Test
    void bassPlaysRootsAndFifths() {
        int j1 = Sec.JINGYESI_1.start();
        assertEquals("A E", pcsAt("Bass", j1));           // Am: A + E
        assertEquals("A D", pcsAt("Bass", j1 + 4));       // Dm: D + A
        assertEquals("C F", pcsAt("Bass", j1 + 6));       // Fmaj7: F + C
        assertEquals("B E", pcsAt("Bass", j1 + 7));       // Em: E + B
    }

    @Test
    void instrumentEntrancesAndExits() {
        int intro = Sec.INTRO.start();
        int j1    = Sec.JINGYESI_1.start();
        int j3    = Sec.JINGYESI_3.start();
        int c3    = Sec.CHUNXIAO_3.start();
        int coda  = Sec.CODA.start();

        int gs = Sec.GUITAR_SOLO.start(), rs = Sec.RHODES_SOLO.start();

        assertFalse(silent("Guitar", intro), "intro: guitar alone");
        assertTrue(silent("Bass", intro) && silent("Rhodes RH", intro));

        // Vocal sections keep the code: guitar = morning, Rhodes = night.
        for (int b = j1; b < c3; b++) {
            if (b >= rs && b < rs + 16) continue;                       // swap window
            assertTrue(silent("Guitar", b), "guitar tacet under the night, bar " + (b + 1));
        }
        for (int b = 0; b < j1; b++) {
            if (b >= gs && b < gs + 8) continue;                        // swap window
            assertTrue(silent("Rhodes RH", b), "Rhodes absent before the night, bar " + (b + 1));
        }
        for (int b = c3; b < coda; b++) assertTrue(silent("Rhodes RH", b), "Rhodes absent in §9, bar " + (b + 1));
        for (int b = j3; b < j3 + 8; b++) assertTrue(silent("Bass", b), "§8 stripped: bass out, bar " + (b + 1));

        // Under each solo the other instrument carries the harmony.
        for (int b = gs; b < gs + 8; b++) {
            assertTrue(silent("Guitar", b),  "guitar comp out under its own solo, bar " + (b + 1));
            assertFalse(silent("Rhodes RH", b), "Rhodes harmony under guitar solo, bar " + (b + 1));
        }
        for (int b = rs; b < rs + 16; b++) {
            assertTrue(silent("Rhodes RH", b),  "Rhodes comp out under its own solo, bar " + (b + 1));
            assertFalse(silent("Guitar", b), "guitar harmony under Rhodes solo, bar " + (b + 1));
        }

        assertFalse(silent("Guitar", coda));
        assertFalse(silent("Rhodes RH", coda));
        assertEquals("A B D E", pcsAt("Guitar", coda + 4), "E7sus4 tail");
        assertEquals("A B D E", pcsAt("Guitar", coda + 7), "never resolves");
    }

    @Test
    void rhodesLeftHandPlaysWheneverTheRhodesIsOnStage() {
        var arr = new BluesChunXiaoJingYeSi();
        for (int b = 0; b < 88; b++) {
            Sec s = BluesChunXiaoJingYeSi.sectionAt(b);
            boolean onStage = switch (s) {
                case GUITAR_SOLO, JINGYESI_1, JINGYESI_2, RHODES_SOLO, JINGYESI_3, CODA -> true;
                default -> false;
            };
            assertEquals(onStage, !silent("Rhodes LH", b), "Rhodes LH bar " + (b + 1));
            if (!onStage) continue;
            var notes = notesIn("Rhodes LH", b, b + 1);
            assertEquals(4, notes.size(), "four quarters, bar " + (b + 1));
            assertTrue(notes.stream().allMatch(n -> n.duration().equalsDuration(Duration.of(1, 4))));
            // Low register: nothing above G4 (the tenth over D3 is F♯4); the octave-up root is exactly 12 above the first note.
            assertTrue(notes.stream().allMatch(n -> n.midi() <= 67), "bar " + (b + 1));
            assertEquals(notes.get(0).midi() + 12, notes.get(2).midi(), "root, then root an octave up, bar " + (b + 1));
            checkDiatonic("Rhodes LH", b, arr.chordAt(b));
        }
    }

    // ── Voice ────────────────────────────────────────────────────────

    @Test
    void voiceSingsOnlyWhereTheChartSaysSo() {
        for (Sec s : List.of(Sec.INTRO, Sec.GUITAR_SOLO, Sec.RHODES_SOLO)) {
            for (int b = s.start(); b < s.start() + s.bars; b++) assertTrue(silent("Voice (F)", b), s + " bar " + (b + 1));
        }
        for (Sec s : List.of(Sec.CHUNXIAO_1, Sec.CHUNXIAO_2, Sec.CHUNXIAO_3)) {
            for (int i = 0; i < 8; i++) assertFalse(silent("Voice (F)", s.start() + i), s + " all four lines, bar " + (i + 1));
        }
        for (Sec s : List.of(Sec.JINGYESI_1, Sec.JINGYESI_2, Sec.JINGYESI_3)) {
            for (int i = 0; i < 8; i++) assertFalse(silent("Voice (F)", s.start() + i), s + " bar " + (i + 1));
        }
        int coda = Sec.CODA.start();
        for (int i = 0; i < 4; i++) assertFalse(silent("Voice (F)", coda + i), "花落知多少");
        for (int i = 4; i < 8; i++) assertTrue(silent("Voice (F)", coda + i), "sus4 tail: instruments only");
    }

    @Test
    void theDoorTurnsTheMelodyToo() {
        // 花 in bar 7: C♯ over A in §2/§9, C♮ over Am in §4.
        assertTrue(pcsAt("Voice (F)", Sec.CHUNXIAO_1.start() + 6).contains("C#"));
        assertTrue(pcsAt("Voice (F)", Sec.CHUNXIAO_2.start() + 6).contains("C"));
        assertFalse(pcsAt("Voice (F)", Sec.CHUNXIAO_2.start() + 6).contains("C#"));
        // 來 in bar 5: F♯ in §2 and §9, F♮ in §4 — same line, three readings.
        assertTrue(pcsAt("Voice (F)", Sec.CHUNXIAO_1.start() + 4).contains("F#"));
        assertTrue(pcsAt("Voice (F)", Sec.CHUNXIAO_2.start() + 4).contains("F"));
        assertFalse(pcsAt("Voice (F)", Sec.CHUNXIAO_2.start() + 4).contains("F#"));
        assertTrue(pcsAt("Voice (F)", Sec.CHUNXIAO_3.start() + 4).contains("F#"));
    }

    @Test
    void jingyesiRhymesSustain_andXiangHangsOnB() {
        for (Sec s : List.of(Sec.JINGYESI_1, Sec.JINGYESI_2, Sec.JINGYESI_3)) {
            // 光 (bar 2) lands on the root, 霜 (bar 4) on the fifth; both held a half.
            PitchedNote guang = lastNote("Voice (F)", s.start() + 1);
            PitchedNote shuang = lastNote("Voice (F)", s.start() + 3);
            assertEquals(9, Math.floorMod(guang.midi(), 12), s + " 光 on A");
            assertEquals(4, Math.floorMod(shuang.midi(), 12), s + " 霜 on E");
            assertTrue(guang.duration().compareDuration(Duration.of(1, 2)) >= 0, "光 sustained");
            assertTrue(shuang.duration().compareDuration(Duration.of(1, 2)) >= 0, "霜 sustained");
            // 鄉 (bar 8): B, held a half — the fifth of Em, never the tonic.
            PitchedNote xiang = lastNote("Voice (F)", s.start() + 7);
            assertEquals(11, Math.floorMod(xiang.midi(), 12), s + " 鄉 hangs on B");
            assertTrue(xiang.duration().compareDuration(Duration.of(1, 2)) >= 0);
        }
    }

    @Test
    void theLiftIsInTheVoice() {
        int j1 = Sec.JINGYESI_1.start(), j2 = Sec.JINGYESI_2.start(), j3 = Sec.JINGYESI_3.start();
        int peak1 = maxMidi("Voice (F)", j1, j1 + 8);
        int peak2 = maxMidi("Voice (F)", j2, j2 + 8);
        int peak3 = maxMidi("Voice (F)", j3, j3 + 8);
        assertEquals(76, peak1, "§5 peaks at E5 on 望");
        assertEquals(79, peak2, "§6 舉頭 lifts to G5 — the moon");
        assertTrue(peak3 < peak1, "§8: the head never lifts again");
        // Lines 1–2 stay at or below A4; line 3 rises above them.
        assertTrue(maxMidi("Voice (F)", j1, j1 + 4) <= 69, "lines 1–2 stay at or below A4");
    }

    @Test
    void chunxiaoRhymesAreShortAndScooped() {
        int s = Sec.CHUNXIAO_1.start();
        // 曉 (bar 2) lands on A, 鳥 (bar 4) on the blue ♭3 (C♮); both a quarter or shorter.
        PitchedNote xiao = lastNote("Voice (F)", s + 1);
        PitchedNote niao = lastNote("Voice (F)", s + 3);
        assertEquals(9, Math.floorMod(xiao.midi(), 12), "曉 → root");
        assertEquals(0, Math.floorMod(niao.midi(), 12), "鳥 → ♭3");
        assertTrue(xiao.duration().compareDuration(Duration.of(1, 4)) <= 0);
        assertTrue(niao.duration().compareDuration(Duration.of(1, 4)) <= 0);
        // The scoop: a grace note sits immediately before each rhyme, one step below it.
        assertTrue(hasGraceBefore("Voice (F)", xiao), "曉 approached from below");
        assertTrue(hasGraceBefore("Voice (F)", niao), "鳥 approached from below");
        // 少 (bar 8) lands on the root over E7, scooped from G♯, a quarter long.
        PitchedNote shao = lastNote("Voice (F)", s + 7);
        assertEquals(9, Math.floorMod(shao.midi(), 12), "少 → root");
        assertTrue(shao.duration().compareDuration(Duration.of(1, 4)) <= 0);
        assertTrue(hasGraceBefore("Voice (F)", shao), "少 approached from below");
    }

    @Test
    void maleVoiceIsTheFemaleLineAnOctaveDown() {
        var f = track(PERF, "Voice (F)").notes().stream().filter(n -> n instanceof PitchedNote).map(n -> (PitchedNote) n).toList();
        var m = track(PERF, "Voice (M)").notes().stream().filter(n -> n instanceof PitchedNote).map(n -> (PitchedNote) n).toList();
        assertEquals(f.size(), m.size(), "same number of notes");
        for (int i = 0; i < f.size(); i++) {
            assertTrue(f.get(i).at().equalsDuration(m.get(i).at()), "same onset at note " + i);
            assertTrue(f.get(i).duration().equalsDuration(m.get(i).duration()), "same length at note " + i);
            assertEquals(f.get(i).midi() - 12, m.get(i).midi(), "octave below at note " + i);
        }
        // The grace-note scoops survive the shift too.
        int s = Sec.CHUNXIAO_1.start();
        assertTrue(hasGraceBefore("Voice (M)", lastNote("Voice (M)", s + 1)), "曉 scooped in the male part");
    }

    @Test
    void vocalRangesAreSingable() {
        var f = notesIn("Voice (F)", 0, 88).stream().mapToInt(PitchedNote::midi).summaryStatistics();
        var m = notesIn("Voice (M)", 0, 88).stream().mapToInt(PitchedNote::midi).summaryStatistics();
        assertEquals(61, f.getMin(), "female floor C♯4 (眠)");
        assertEquals(79, f.getMax(), "female peak G5 — the moon");
        assertEquals(49, m.getMin(), "male floor C♯3");
        assertEquals(67, m.getMax(), "male peak G4");
    }

    // ── Choir ────────────────────────────────────────────────────────

    @Test
    void choirEntersOnLineTwoOfJingyesiI_inUnisonWithTheVoice() {
        int j1 = Sec.JINGYESI_1.start();
        for (int b = 0; b < j1 + 2; b++) assertTrue(silent("Choir", b), "choir silent until §5 line 2, bar " + (b + 1));
        for (int b = j1 + 2; b < j1 + 8; b++) {
            assertEquals(pcsAt("Voice (F)", b), pcsAt("Choir", b), "unison, bar " + (b + 1));
            assertEquals(notesIn("Voice (F)", b, b + 1).size(), notesIn("Choir", b, b + 1).size(), "same rhythm, bar " + (b + 1));
        }
    }

    @Test
    void choirHarmonyInJingyesiII_isAntiphonal() {
        int j2 = Sec.JINGYESI_2.start();
        // Under 舉頭望明月 the choir holds a high chord — one chord per bar, no melodic motion.
        assertEquals(3, notesIn("Choir", j2 + 4, j2 + 5).size(), "one triad under 舉頭");
        assertEquals(3, notesIn("Choir", j2 + 5, j2 + 6).size(), "one triad under 望明月");
        assertTrue(notesIn("Choir", j2 + 4, j2 + 5).stream().allMatch(n -> n.duration().equalsDuration(Duration.of(1, 1))));
        // The answer: 低頭思故鄉 in three parts, ending on Em with B on top …
        var xiang = notesIn("Choir", j2 + 7, j2 + 8);
        int topOfLast = xiang.stream().filter(n -> n.at().equalsDuration(Duration.of(j2 + 7, 1).plus(Duration.of(1, 2))))
                .mapToInt(PitchedNote::midi).max().orElseThrow();
        assertEquals(71, topOfLast, "鄉: B4 on top of Em");
        // … and a register below the moon (G5 in the solo two bars earlier).
        assertTrue(maxMidi("Choir", j2 + 6, j2 + 8) < 79);
        // The male voice answers from beneath the choir: at every male onset,
        // every choir note sounding at that instant is at or above it.
        var choirNotes = notesIn("Choir", j2 + 6, j2 + 8);
        for (PitchedNote v : notesIn("Voice (M)", j2 + 6, j2 + 8)) {
            for (PitchedNote c : choirNotes) {
                boolean sounding = c.at().compareDuration(v.at()) <= 0 && c.endAt().compareDuration(v.at()) > 0;
                if (sounding) assertTrue(v.midi() <= c.midi(),
                        "male " + v.midi() + " should sit at or below choir " + c.midi() + " at " + v.at());
            }
        }
        // Every choir note is diatonic to its chord.
        var arr = new BluesChunXiaoJingYeSi();
        for (int b = j2; b < j2 + 8; b++) checkDiatonic("Choir", b, arr.chordAt(b));
    }

    @Test
    void choirTacetForStrippedChorus_andHumsUnderChunxiaoIII() {
        int j3 = Sec.JINGYESI_3.start(), c3 = Sec.CHUNXIAO_3.start(), coda = Sec.CODA.start();
        for (int b = Sec.RHODES_SOLO.start(); b < j3 + 8; b++) assertTrue(silent("Choir", b), "tacet, bar " + (b + 1));
        for (int b = c3; b < c3 + 7; b++) {
            assertEquals(pcsAt("Guitar", b).replace(" ", ""), pcsAt("Choir", b).replace(" ", ""), "hum matches the change, bar " + (b + 1));
            assertTrue(notesIn("Choir", b, b + 1).stream().allMatch(n -> n.duration().equalsDuration(Duration.of(1, 1))), "held");
        }
        assertTrue(silent("Choir", c3 + 7), "stop-time");
        for (int b = coda; b < coda + 4; b++) assertTrue(silent("Choir", b), "coda: the solo voice asks the question alone");
        for (int b = coda + 4; b < coda + 8; b++) assertFalse(silent("Choir", b), "coda tail: the choir closes, bar " + (b + 1));
        // Every choir note on the tail is a tone of E7sus4 (E A B D) — the chord stays clean.
        for (PitchedNote n : notesIn("Choir", coda + 4, coda + 8)) {
            assertTrue(java.util.Set.of(4, 9, 11, 2).contains(Math.floorMod(n.midi(), 12)), "non-chord tone " + n.midi());
        }
        // 鄉 (bar 6) hangs on B; 少 — the last word of the song — is A, the suspended fourth, held to the final bar line.
        assertEquals(11, Math.floorMod(topAtEnd("Choir", coda + 5), 12), "鄉 on B");
        PitchedNote shao = lastNote("Choir", coda + 7);
        assertEquals(9, Math.floorMod(shao.midi(), 12), "少 on A");
        assertTrue(shao.endAt().equalsDuration(Duration.of(88, 1)), "held to the end of the piece");
    }

    /** Highest note among those starting last in the bar. */
    private static int topAtEnd(String trackName, int bar) {
        var ns = notesIn(trackName, bar, bar + 1);
        Duration lastAt = ns.get(ns.size() - 1).at();
        return ns.stream().filter(n -> n.at().equalsDuration(lastAt)).mapToInt(PitchedNote::midi).max().orElseThrow();
    }

    private static PitchedNote lastNote(String trackName, int bar) {
        var ns = notesIn(trackName, bar, bar + 1);
        return ns.get(ns.size() - 1);
    }

    private static int maxMidi(String trackName, int fromBar, int toBarExcl) {
        return notesIn(trackName, fromBar, toBarExcl).stream().mapToInt(PitchedNote::midi).max().orElseThrow();
    }

    /** True when a very short note ends exactly where {@code main} starts and sits 1–2 semitones below it. */
    private static boolean hasGraceBefore(String trackName, PitchedNote main) {
        return track(PERF, trackName).notes().stream()
                .filter(n -> n instanceof PitchedNote)
                .map(n -> (PitchedNote) n)
                .anyMatch(g -> g.endAt().equalsDuration(main.at())
                        && g.duration().compareDuration(Duration.of(1, 16)) <= 0
                        && main.midi() - g.midi() >= 1 && main.midi() - g.midi() <= 2);
    }

    // ── Solos ────────────────────────────────────────────────────────

    private static List<PitchedNote> notesIn(String trackName, int fromBar, int toBarExcl) {
        Duration from = Duration.of(fromBar, 1), to = Duration.of(toBarExcl, 1);
        return track(PERF, trackName).notes().stream()
                .filter(n -> n instanceof PitchedNote)
                .map(n -> (PitchedNote) n)
                .filter(pn -> pn.at().compareDuration(from) >= 0 && pn.at().compareDuration(to) < 0)
                .toList();
    }

    @Test
    void solosSitOnlyInTheirWindows() {
        int g0 = Sec.GUITAR_SOLO.start(), r0 = Sec.RHODES_SOLO.start();
        for (int b = 0; b < 88; b++) {
            boolean inG = b >= g0 && b < g0 + 8;
            boolean inR = b >= r0 && b < r0 + 16;
            assertEquals(inG, !silent("Guitar Solo", b), "guitar solo bar " + (b + 1));
            assertEquals(inR, !silent("Rhodes Solo", b), "rhodes solo bar " + (b + 1));
        }
    }

    @Test
    void solosAreVirtuosic() {
        var g = notesIn("Guitar Solo", Sec.GUITAR_SOLO.start(), Sec.GUITAR_SOLO.start() + 8);
        var r = notesIn("Rhodes Solo", Sec.RHODES_SOLO.start(), Sec.RHODES_SOLO.start() + 16);
        assertTrue(g.size() >= 100, "guitar solo is sixteenth-driven: " + g.size());
        assertTrue(r.size() >= 200, "rhodes solo is sixteenth-driven: " + r.size());
        // Guitar tops out at E6; Rhodes climbs to A6 in the second chorus.
        assertEquals(88, g.stream().mapToInt(PitchedNote::midi).max().orElseThrow(), "E6");
        assertEquals(93, r.stream().mapToInt(PitchedNote::midi).max().orElseThrow(), "A6");
        // Both spend real time across the range.
        assertTrue(g.stream().mapToInt(PitchedNote::midi).min().orElseThrow() <= 57, "guitar reaches A3");
        assertTrue(r.stream().mapToInt(PitchedNote::midi).min().orElseThrow() <= 52, "rhodes reaches E3");
    }

    /** Every solo note must belong to the diatonic set of its bar's chord (chord tones + colour tones). */
    @Test
    void soloNotesAreDiatonicToTheirChord() {
        var arr = new BluesChunXiaoJingYeSi();
        for (int b = Sec.GUITAR_SOLO.start(); b < Sec.GUITAR_SOLO.start() + 8; b++) {
            checkDiatonic("Guitar Solo", b, arr.chordAt(b));
        }
        for (int b = Sec.RHODES_SOLO.start(); b < Sec.RHODES_SOLO.start() + 16; b++) {
            checkDiatonic("Rhodes Solo", b, arr.chordAt(b));
        }
    }

    private static void checkDiatonic(String trackName, int bar, BluesChunXiaoJingYeSi.Ch ch) {
        // A major (morning) / A natural minor (night) scale sets; the
        // chromatic walk-up in guitar bar 3 admits F♮ over A7.
        java.util.Set<Integer> allowed = switch (ch) {
            case A_MAJ, E7        -> java.util.Set.of(9, 11, 1, 2, 4, 6, 8);            // A B C# D E F# G#
            case D_MAJ            -> java.util.Set.of(2, 4, 6, 7, 9, 11, 1);            // D E F# G A B C# (IV's own scale)
            case A7               -> java.util.Set.of(9, 11, 1, 2, 4, 6, 7, 5);         // + G♮, F♮ (chromatic approach)
            default               -> java.util.Set.of(9, 11, 0, 2, 4, 5, 7, 10);        // A B C D E F G + B♭
        };
        for (PitchedNote pn : notesIn(trackName, bar, bar + 1)) {
            int pc = Math.floorMod(pn.midi(), 12);
            assertTrue(allowed.contains(pc),
                    trackName + " bar " + (bar + 1) + " (" + ch + "): " + NAMES[pc] + " not diatonic");
        }
    }

    @Test
    void rhodesSoloEndsOffTheTonic() {
        var r = notesIn("Rhodes Solo", Sec.RHODES_SOLO.start() + 15, Sec.RHODES_SOLO.start() + 16);
        PitchedNote last = r.get(r.size() - 1);
        assertEquals(11, Math.floorMod(last.midi(), 12), "solo hangs on B, the fifth of Em");
    }

    @Test
    void fmaj7CodaVariant() {
        var alt = PieceConcretizer.concretize(
                new BluesChunXiaoJingYeSi(BluesChunXiaoJingYeSi.CodaEnding.FMAJ7).create());
        int coda = Sec.CODA.start();
        assertEquals("A C E F", pcsAt(alt, "Guitar", coda + 4));
        assertEquals("A C E F", pcsAt(alt, "Rhodes RH", coda + 7));
    }
}
