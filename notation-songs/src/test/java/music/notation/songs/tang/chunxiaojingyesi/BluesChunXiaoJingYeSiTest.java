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
        assertEquals("C E G",   pcsAt("Rhodes", s + 4), "C — 舉頭");
        assertEquals("B C E G", pcsAt("Rhodes", s + 5), "Cmaj7");
        assertEquals("A C E F", pcsAt("Rhodes", s + 6), "Fmaj7 — 低頭");
        assertEquals("B E G",   pcsAt("Rhodes", s + 7), "Em");
    }

    @Test
    void tonicIsWithheldUnderGuxiang() {
        for (Sec sec : List.of(Sec.JINGYESI_1, Sec.JINGYESI_2, Sec.JINGYESI_3)) {
            assertEquals("B E G", pcsAt("Rhodes", sec.start() + 7), sec + " bar 8 must be Em");
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
        assertEquals("A C E",    pcsAt("Rhodes", j1),     "Am takes C natural");
        assertEquals("A C E G",  pcsAt("Rhodes", j1 + 2), "Am7");
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
        assertTrue(silent("Bass", intro) && silent("Rhodes", intro));

        // Vocal sections keep the code: guitar = morning, Rhodes = night.
        for (int b = j1; b < c3; b++) {
            if (b >= rs && b < rs + 16) continue;                       // swap window
            assertTrue(silent("Guitar", b), "guitar tacet under the night, bar " + (b + 1));
        }
        for (int b = 0; b < j1; b++) {
            if (b >= gs && b < gs + 8) continue;                        // swap window
            assertTrue(silent("Rhodes", b), "Rhodes absent before the night, bar " + (b + 1));
        }
        for (int b = c3; b < coda; b++) assertTrue(silent("Rhodes", b), "Rhodes absent in §9, bar " + (b + 1));
        for (int b = j3; b < j3 + 8; b++) assertTrue(silent("Bass", b), "§8 stripped: bass out, bar " + (b + 1));

        // Under each solo the other instrument carries the harmony.
        for (int b = gs; b < gs + 8; b++) {
            assertTrue(silent("Guitar", b),  "guitar comp out under its own solo, bar " + (b + 1));
            assertFalse(silent("Rhodes", b), "Rhodes harmony under guitar solo, bar " + (b + 1));
        }
        for (int b = rs; b < rs + 16; b++) {
            assertTrue(silent("Rhodes", b),  "Rhodes comp out under its own solo, bar " + (b + 1));
            assertFalse(silent("Guitar", b), "guitar harmony under Rhodes solo, bar " + (b + 1));
        }

        assertFalse(silent("Guitar", coda));
        assertFalse(silent("Rhodes", coda));
        assertEquals("A B D E", pcsAt("Guitar", coda + 4), "E7sus4 tail");
        assertEquals("A B D E", pcsAt("Guitar", coda + 7), "never resolves");
    }

    // ── Voice ────────────────────────────────────────────────────────

    @Test
    void voiceSingsOnlyWhereTheChartSaysSo() {
        for (Sec s : List.of(Sec.INTRO, Sec.GUITAR_SOLO, Sec.RHODES_SOLO)) {
            for (int b = s.start(); b < s.start() + s.bars; b++) assertTrue(silent("Voice", b), s + " bar " + (b + 1));
        }
        for (Sec s : List.of(Sec.CHUNXIAO_1, Sec.CHUNXIAO_2, Sec.CHUNXIAO_3)) {
            for (int i = 0; i < 6; i++) assertFalse(silent("Voice", s.start() + i), s + " lines 1–3");
            assertTrue(silent("Voice", s.start() + 6), s + " bar 7: guitar answers");
            assertTrue(silent("Voice", s.start() + 7), s + " bar 8: line 4 withheld");
        }
        for (Sec s : List.of(Sec.JINGYESI_1, Sec.JINGYESI_2, Sec.JINGYESI_3)) {
            for (int i = 0; i < 8; i++) assertFalse(silent("Voice", s.start() + i), s + " bar " + (i + 1));
        }
        int coda = Sec.CODA.start();
        for (int i = 0; i < 4; i++) assertFalse(silent("Voice", coda + i), "花落知多少");
        for (int i = 4; i < 8; i++) assertTrue(silent("Voice", coda + i), "sus4 tail: instruments only");
    }

    @Test
    void theDoorTurnsTheMelodyToo() {
        // 來 in bar 5: F♯ in §2 and §9, F♮ in §4 — same line, three readings.
        assertTrue(pcsAt("Voice", Sec.CHUNXIAO_1.start() + 4).contains("F#"));
        assertTrue(pcsAt("Voice", Sec.CHUNXIAO_2.start() + 4).contains("F"));
        assertFalse(pcsAt("Voice", Sec.CHUNXIAO_2.start() + 4).contains("F#"));
        assertTrue(pcsAt("Voice", Sec.CHUNXIAO_3.start() + 4).contains("F#"));
    }

    @Test
    void jingyesiRhymesSustainOnTheFifth_andXiangHangsOnB() {
        for (Sec s : List.of(Sec.JINGYESI_1, Sec.JINGYESI_2, Sec.JINGYESI_3)) {
            // 光 (bar 2) and 霜 (bar 4): the last note is E held a half.
            for (int barIdx : new int[]{1, 3}) {
                PitchedNote last = lastNote("Voice", s.start() + barIdx);
                assertEquals(4, Math.floorMod(last.midi(), 12), s + " bar " + (barIdx + 1) + " ends on E");
                assertTrue(last.duration().compareDuration(Duration.of(1, 2)) >= 0, "sustained");
            }
            // 鄉 (bar 8): B, held a half — the fifth of Em, never the tonic.
            PitchedNote xiang = lastNote("Voice", s.start() + 7);
            assertEquals(11, Math.floorMod(xiang.midi(), 12), s + " 鄉 hangs on B");
            assertTrue(xiang.duration().compareDuration(Duration.of(1, 2)) >= 0);
        }
    }

    @Test
    void theLiftIsInTheVoice() {
        int j1 = Sec.JINGYESI_1.start(), j2 = Sec.JINGYESI_2.start(), j3 = Sec.JINGYESI_3.start();
        int peak1 = maxMidi("Voice", j1, j1 + 8);
        int peak2 = maxMidi("Voice", j2, j2 + 8);
        int peak3 = maxMidi("Voice", j3, j3 + 8);
        assertEquals(76, peak1, "§5 peaks at E5 on 望");
        assertEquals(79, peak2, "§6 舉頭 lifts to G5 — the moon");
        assertTrue(peak3 < peak1, "§8: the head never lifts again");
        // Lines 1–2 sit an octave below line 3.
        assertTrue(maxMidi("Voice", j1, j1 + 4) <= 69, "lines 1–2 stay at or below A4");
    }

    @Test
    void chunxiaoRhymesAreShortAndScooped() {
        int s = Sec.CHUNXIAO_1.start();
        // 曉 (bar 2) lands on A, 鳥 (bar 4) on the blue ♭3 (C♮); both a quarter or shorter.
        PitchedNote xiao = lastNote("Voice", s + 1);
        PitchedNote niao = lastNote("Voice", s + 3);
        assertEquals(9, Math.floorMod(xiao.midi(), 12), "曉 → root");
        assertEquals(0, Math.floorMod(niao.midi(), 12), "鳥 → ♭3");
        assertTrue(xiao.duration().compareDuration(Duration.of(1, 4)) <= 0);
        assertTrue(niao.duration().compareDuration(Duration.of(1, 4)) <= 0);
        // The scoop: a grace note sits immediately before each rhyme, one step below it.
        assertTrue(hasGraceBefore("Voice", xiao), "曉 approached from below");
        assertTrue(hasGraceBefore("Voice", niao), "鳥 approached from below");
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
        assertEquals("A C E F", pcsAt(alt, "Rhodes", coda + 7));
    }
}
