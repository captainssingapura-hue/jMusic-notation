package music.notation.mxl;

import music.notation.performance.PitchedNote;
import music.notation.performance.Track;
import music.notation.structure.Mode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
    void graceNoteIsSkippedAndDoesNotAdvanceCursor() {
        // A grace note before a regular C4 quarter — the grace should be
        // dropped (count surfaced via SLF4J at end of part); the C4 lands
        // at tick 0 with no ms shifted by the grace.
        String xml = wrap("""
                <measure number="1">
                  <attributes>
                    <divisions>4</divisions>
                    <key><fifths>0</fifths><mode>major</mode></key>
                    <time><beats>4</beats><beat-type>4</beat-type></time>
                    <staves>1</staves>
                  </attributes>
                  <note>
                    <grace/>
                    <pitch><step>D</step><octave>4</octave></pitch>
                    <voice>1</voice><staff>1</staff>
                  </note>
                  <note>
                    <pitch><step>C</step><octave>4</octave></pitch>
                    <duration>16</duration><voice>1</voice><staff>1</staff>
                  </note>
                </measure>
                """);
        var result = MusicXmlParser.parse(xml);
        Track t = result.performance().score().tracks().get(0);
        assertEquals(1, t.notes().size(), "grace note should be skipped");
        assertEquals(0L, ((PitchedNote) t.notes().get(0)).tickMs(),
                "main note should still start at tick 0");
        assertEquals(60, ((PitchedNote) t.notes().get(0)).midi());
    }
}
