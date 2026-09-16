package music.notation.mxl;

import music.notation.duration.Duration;
import music.notation.performance.KeySignatureTrack;
import music.notation.performance.TimeSignatureTrack;
import music.notation.structure.Mode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mid-piece <code>&lt;time&gt;</code> and <code>&lt;key&gt;</code>
 * changes used to be silently dropped (warning logged, data lost).
 * They now land on the Performance's
 * {@link TimeSignatureTrack} / {@link KeySignatureTrack}.
 *
 * <p>Backstop test pinned to actual MXL fragments — exercises the
 * full {@code parse → walkMeasure → applyAttributes} pipeline rather
 * than the track records in isolation.</p>
 */
class MusicXmlParserSignatureChangesTest {

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
    void midPieceTimeSigChange_isCapturedOnTimeSignatureTrack() {
        // Two measures: first is 4/4, second changes to 3/4. Each measure
        // is just one filler note; we're testing signature flow, not pitch.
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
                    <time><beats>3</beats><beat-type>4</beat-type></time>
                  </attributes>
                  <note><pitch><step>D</step><octave>4</octave></pitch>
                        <duration>12</duration><voice>1</voice><staff>1</staff></note>
                </measure>
                """);
        var result = MusicXmlParser.parse(xml);
        TimeSignatureTrack tt = result.performance().timeSignatures();
        // Canonical form: 4/4 at position 0 then 3/4 at the second measure's start.
        assertEquals(2, tt.changes().size(),
                "track should record the initial 4/4 + the mid-piece 3/4");
        assertTrue(tt.changes().get(0).at().isZero());
        assertEquals(4, tt.changes().get(0).timeSig().beats());
        assertEquals(4, tt.changes().get(0).timeSig().beatValue());
        // Measure 1 is a full 4/4 bar = one whole note, so measure 2 starts at 1/1.
        assertTrue(Duration.of(1, 1).equalsDuration(tt.changes().get(1).at()),
                "second change must be at the second measure's onset (one whole note in), got "
                        + tt.changes().get(1).at());
        assertEquals(3, tt.changes().get(1).timeSig().beats());
        assertEquals(4, tt.changes().get(1).timeSig().beatValue());
    }

    @Test
    void midPieceKeyChange_isCapturedOnKeySignatureTrack() {
        // C major → G major modulation in measure 2.
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
                  </attributes>
                  <note><pitch><step>G</step><octave>4</octave></pitch>
                        <duration>16</duration><voice>1</voice><staff>1</staff></note>
                </measure>
                """);
        var result = MusicXmlParser.parse(xml);
        KeySignatureTrack kt = result.performance().keySignatures();
        assertEquals(2, kt.changes().size());
        assertEquals(Mode.MAJOR, kt.changes().get(0).key().mode());
        // The tonic conversion goes through the fifths→key table; we
        // assert structural change rather than re-deriving the tonic
        // here — that's covered by other tests.
        assertTrue(Duration.of(1, 1).equalsDuration(kt.changes().get(1).at()),
                "second key change must be at the second measure's onset (one whole note in), got "
                        + kt.changes().get(1).at());
    }

    @Test
    void noSignatureChange_givesSingleEntryTrack() {
        // Stable piece: one entry per track (the initial values at position 0).
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
                """);
        var result = MusicXmlParser.parse(xml);
        assertEquals(1, result.performance().timeSignatures().changes().size());
        assertEquals(1, result.performance().keySignatures().changes().size());
        assertTrue(result.performance().timeSignatures().changes().get(0).at().isZero());
        assertTrue(result.performance().keySignatures().changes().get(0).at().isZero());
    }
}
