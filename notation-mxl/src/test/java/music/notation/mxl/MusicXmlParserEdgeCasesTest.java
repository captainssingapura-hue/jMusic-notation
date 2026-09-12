package music.notation.mxl;

import music.notation.performance.PitchedNote;
import music.notation.performance.Track;
import music.notation.structure.Mode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Landing 1g — robustness on malformed / non-canonical MusicXML inputs:
 * the parser should not crash, should warn through SLF4J where useful,
 * and should produce sensible output for downstream consumers.
 */
class MusicXmlParserEdgeCasesTest {

    /** Helper: wrap a single one-bar 4/4 part body in a partwise score. */
    private static String wrap(String partBody) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <score-partwise>
                  <part-list><score-part id="P1"><part-name>P</part-name></score-part></part-list>
                  <part id="P1">
                    %s
                  </part>
                </score-partwise>
                """.formatted(partBody);
    }

    @Test
    void keyWithoutModeReturnsModeNone() {
        // <key> has <fifths> but no <mode>. Parser must not throw and
        // surfaces Mode.NONE — staying honest about what the source said
        // rather than fabricating a major/minor label. Tonic still
        // resolves via the relative-major table (Bb for fifths=-2).
        String xml = wrap("""
                <measure number="1">
                  <attributes>
                    <divisions>4</divisions>
                    <key><fifths>-2</fifths></key>
                    <time><beats>4</beats><beat-type>4</beat-type></time>
                    <staves>1</staves>
                  </attributes>
                  <note><pitch><step>C</step><octave>4</octave></pitch>
                        <duration>16</duration><voice>1</voice><staff>1</staff></note>
                </measure>
                """);
        var result = MusicXmlParser.parse(xml);
        assertEquals(Mode.NONE, result.key().mode());
        assertEquals(music.notation.pitch.NoteName.B, result.key().tonic());
        assertEquals(music.notation.pitch.Accidental.FLAT, result.key().accidental());
    }

    @Test
    void midPieceTimeAndKeyChangesAreIgnoredGracefully() {
        // First measure declares 4/4 + C major. Second measure declares
        // 3/4 + G major. Expected: parser keeps the initial 4/4 + C major,
        // logs warnings for the change, and continues parsing notes.
        String xml = wrap("""
                <measure number="1">
                  <attributes>
                    <divisions>4</divisions>
                    <key><fifths>0</fifths><mode>major</mode></key>
                    <time><beats>4</beats><beat-type>4</beat-type></time>
                    <staves>1</staves>
                  </attributes>
                  <note><pitch><step>C</step><octave>4</octave></pitch>
                        <duration>16</duration><voice>1</voice><staff>1</staff></note>
                </measure>
                <measure number="2">
                  <attributes>
                    <key><fifths>1</fifths><mode>major</mode></key>
                    <time><beats>3</beats><beat-type>4</beat-type></time>
                  </attributes>
                  <note><pitch><step>D</step><octave>4</octave></pitch>
                        <duration>16</duration><voice>1</voice><staff>1</staff></note>
                </measure>
                """);
        var result = MusicXmlParser.parse(xml);

        // Initial 4/4 retained.
        assertEquals(4, result.timeSig().beats());
        assertEquals(4, result.timeSig().beatValue());
        // Initial C major retained.
        assertEquals(Mode.MAJOR, result.key().mode());
        // Both notes were parsed.
        Track t = result.performance().score().tracks().get(0);
        assertEquals(2, t.notes().size());
    }

    @Test
    void malformedTempoAttributeIsIgnoredNotFatal() {
        // <sound tempo="abc"> — parse should not throw; tempo defaults to 120.
        String xml = wrap("""
                <measure number="1">
                  <attributes>
                    <divisions>4</divisions>
                    <key><fifths>0</fifths><mode>major</mode></key>
                    <time><beats>4</beats><beat-type>4</beat-type></time>
                    <staves>1</staves>
                  </attributes>
                  <direction><sound tempo="abc"/></direction>
                  <note><pitch><step>C</step><octave>4</octave></pitch>
                        <duration>16</duration><voice>1</voice><staff>1</staff></note>
                </measure>
                """);
        var result = MusicXmlParser.parse(xml);
        // No tempo events recorded; falls back to default-bpm-only tempo track.
        assertEquals(1, result.performance().tempo().changes().size());
        assertEquals(MusicXmlParser.DEFAULT_BPM,
                result.performance().tempo().changes().get(0).bpm());
    }

    @Test
    void malformedFifthsAttributeFallsBackToCMajor() {
        String xml = wrap("""
                <measure number="1">
                  <attributes>
                    <divisions>4</divisions>
                    <key><fifths>not-a-number</fifths><mode>major</mode></key>
                    <time><beats>4</beats><beat-type>4</beat-type></time>
                    <staves>1</staves>
                  </attributes>
                  <note><pitch><step>C</step><octave>4</octave></pitch>
                        <duration>16</duration><voice>1</voice><staff>1</staff></note>
                </measure>
                """);
        var result = MusicXmlParser.parse(xml);
        assertNotNull(result.key());
        // Falls back to fifths=0 → C major.
        assertEquals(music.notation.pitch.NoteName.C, result.key().tonic());
        assertEquals(Mode.MAJOR, result.key().mode());
    }

    @Test
    void graceNoteEmitsAsPreBeatAcciaccatura() {
        // A grace D4 before a C4 quarter. Place a leading rest (16 divisions
        // at the default 120 bpm = 500 ms quarter) so the grace has room
        // to genuinely precede the main note's onset rather than being
        // clamped to tick 0.
        String xml = wrap("""
                <measure number="1">
                  <attributes>
                    <divisions>4</divisions>
                    <key><fifths>0</fifths><mode>major</mode></key>
                    <time><beats>4</beats><beat-type>4</beat-type></time>
                    <staves>1</staves>
                  </attributes>
                  <note>
                    <rest/><duration>4</duration><voice>1</voice><staff>1</staff>
                  </note>
                  <note>
                    <grace/>
                    <pitch><step>D</step><octave>4</octave></pitch>
                    <voice>1</voice><staff>1</staff>
                  </note>
                  <note>
                    <pitch><step>C</step><octave>4</octave></pitch>
                    <duration>4</duration><voice>1</voice><staff>1</staff>
                  </note>
                </measure>
                """);
        var result = MusicXmlParser.parse(xml);
        Track t = result.performance().score().tracks().get(0);
        assertEquals(2, t.notes().size(), "grace + main → two notes (rest is silent)");

        // Look up by midi rather than by index — Track's canonical sort
        // ((tickMs, midi)) can place a lower-pitched main before a
        // higher-pitched grace if both share an onset; here they don't
        // (the rest gives the grace room), but the assertion is more
        // robust this way.
        PitchedNote grace = t.notes().stream()
                .map(n -> (PitchedNote) n).filter(n -> n.midi() == 62).findFirst().orElseThrow();
        PitchedNote main  = t.notes().stream()
                .map(n -> (PitchedNote) n).filter(n -> n.midi() == 60).findFirst().orElseThrow();

        // Main note's onset is unaffected by the grace presence — the
        // grace is squeezed into the lead-in window, not stolen from
        // the main note's slot.
        assertTrue(main.tickMs() > 0,
                "main note onset must still reflect the leading rest");
        assertTrue(grace.tickMs() < main.tickMs(),
                "grace must precede the main onset when it has room");
        assertTrue(grace.tickMs() + grace.durationMs() <= main.tickMs() + 1,
                "grace must end at or before main onset (±1 ms rounding)");
    }

    @Test
    void multipleGraceNotesStackBeforeMain() {
        // Three graces (C5, D5, E5) before a main C4 at 1000 ms — each grace
        // occupies one pre-beat slot (~70 ms), so they fan out backwards.
        // Note the leading rest of 16 divisions (one quarter) to give the
        // graces room — at the default 120 bpm, the quarter is 500 ms.
        String xml = wrap("""
                <measure number="1">
                  <attributes>
                    <divisions>4</divisions>
                    <key><fifths>0</fifths><mode>major</mode></key>
                    <time><beats>4</beats><beat-type>4</beat-type></time>
                    <staves>1</staves>
                  </attributes>
                  <note>
                    <rest/><duration>4</duration><voice>1</voice><staff>1</staff>
                  </note>
                  <note>
                    <grace/>
                    <pitch><step>C</step><octave>5</octave></pitch>
                    <voice>1</voice><staff>1</staff>
                  </note>
                  <note>
                    <grace/>
                    <pitch><step>D</step><octave>5</octave></pitch>
                    <voice>1</voice><staff>1</staff>
                  </note>
                  <note>
                    <grace/>
                    <pitch><step>E</step><octave>5</octave></pitch>
                    <voice>1</voice><staff>1</staff>
                  </note>
                  <note>
                    <pitch><step>C</step><octave>4</octave></pitch>
                    <duration>4</duration><voice>1</voice><staff>1</staff>
                  </note>
                </measure>
                """);
        var result = MusicXmlParser.parse(xml);
        Track t = result.performance().score().tracks().get(0);
        // Just the three graces + the main; the rest is silence.
        assertEquals(4, t.notes().size());

        PitchedNote g1 = (PitchedNote) t.notes().get(0);
        PitchedNote g2 = (PitchedNote) t.notes().get(1);
        PitchedNote g3 = (PitchedNote) t.notes().get(2);
        PitchedNote main = (PitchedNote) t.notes().get(3);

        assertEquals(72, g1.midi(), "first grace should be C5 — order preserved");
        assertEquals(74, g2.midi(), "second grace D5");
        assertEquals(76, g3.midi(), "third grace E5");
        assertEquals(60, main.midi(), "main note C4");

        // Onsets are strictly increasing across the three graces.
        assertTrue(g1.tickMs() < g2.tickMs());
        assertTrue(g2.tickMs() < g3.tickMs());
        // All three graces sit before the main note.
        assertTrue(g3.tickMs() + g3.durationMs() <= main.tickMs() + 1,
                "last grace must end at or before main onset (±1 ms rounding)");
    }
}
