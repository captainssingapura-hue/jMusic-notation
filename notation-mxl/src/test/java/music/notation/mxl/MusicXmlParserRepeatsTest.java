package music.notation.mxl;

import music.notation.mxl.RepeatStructure.Direction;
import music.notation.mxl.RepeatStructure.JumpKind;
import music.notation.performance.PitchedNote;
import music.notation.performance.Track;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Repeat-expansion behaviour, plus the {@link RepeatStructure} sidecar
 * the parser emits alongside the (already-expanded) {@code Performance}.
 */
class MusicXmlParserRepeatsTest {

    /** divisions=4, 4/4 → one whole-note measure = 16 divisions. */
    private static final String ATTRS = """
              <attributes>
                <divisions>4</divisions>
                <key><fifths>0</fifths><mode>major</mode></key>
                <time><beats>4</beats><beat-type>4</beat-type></time>
                <staves>1</staves>
              </attributes>
            """;

    private static String oneNoteMeasure(int number, String step, String barline) {
        return """
                <measure number="%d">
                  <note>
                    <pitch><step>%s</step><octave>4</octave></pitch>
                    <duration>16</duration><voice>1</voice><staff>1</staff>
                  </note>
                  %s
                </measure>
                """.formatted(number, step, barline);
    }

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
    void forwardBackwardRepeatPlaysSectionTwice() {
        // Three measures: M1, [forward] M2, M3 [backward].
        // Expected playback: M1, M2, M3, M2, M3.
        String body =
                "<measure number=\"1\">" + ATTRS + """
                          <note><pitch><step>C</step><octave>4</octave></pitch>
                                <duration>16</duration><voice>1</voice><staff>1</staff></note>
                        </measure>
                        """
                + """
                        <measure number="2">
                          <barline location="left"><repeat direction="forward"/></barline>
                          <note><pitch><step>D</step><octave>4</octave></pitch>
                                <duration>16</duration><voice>1</voice><staff>1</staff></note>
                        </measure>
                        <measure number="3">
                          <note><pitch><step>E</step><octave>4</octave></pitch>
                                <duration>16</duration><voice>1</voice><staff>1</staff></note>
                          <barline location="right"><repeat direction="backward"/></barline>
                        </measure>
                        """;
        var result = MusicXmlParser.parse(wrap(body));

        Track t = result.performance().score().tracks().get(0);
        // 5 notes: C, D, E, D, E
        assertEquals(5, t.notes().size(), "expected 5 notes after repeat expansion");
        List<Integer> midis = t.notes().stream()
                .map(n -> ((PitchedNote) n).midi()).toList();
        assertEquals(List.of(60, 62, 64, 62, 64), midis);

        // Sidecar reflects the unexpanded structure: 1 forward + 1 backward repeat.
        var rs = result.repeatStructure();
        assertEquals(2, rs.repeatBars().size());
        assertEquals(Direction.FORWARD,  rs.repeatBars().get(0).direction());
        assertEquals(Direction.BACKWARD, rs.repeatBars().get(1).direction());
        assertTrue(rs.voltas().isEmpty());
        assertTrue(rs.jumps().isEmpty());
    }

    @Test
    void voltasResolvePerPass() {
        // M1, [forward] M2, M3 [volta 1, backward], M4 [volta 2].
        // Pass 1: M1, M2, M3 (volta 1 plays).
        // Pass 2: M1?  Actually no — forward target was M2, so pass 2 starts at M2.
        //   M2, then volta 1 (M3) excluded, M4 (volta 2) plays.
        // Expected: M1, M2, M3, M2, M4 → C, D, E, D, F.
        String body =
                "<measure number=\"1\">" + ATTRS + """
                          <note><pitch><step>C</step><octave>4</octave></pitch>
                                <duration>16</duration><voice>1</voice><staff>1</staff></note>
                        </measure>
                        """
                + """
                        <measure number="2">
                          <barline location="left"><repeat direction="forward"/></barline>
                          <note><pitch><step>D</step><octave>4</octave></pitch>
                                <duration>16</duration><voice>1</voice><staff>1</staff></note>
                        </measure>
                        <measure number="3">
                          <barline location="left"><ending number="1" type="start"/></barline>
                          <note><pitch><step>E</step><octave>4</octave></pitch>
                                <duration>16</duration><voice>1</voice><staff>1</staff></note>
                          <barline location="right">
                            <ending number="1" type="stop"/>
                            <repeat direction="backward"/>
                          </barline>
                        </measure>
                        <measure number="4">
                          <barline location="left"><ending number="2" type="start"/></barline>
                          <note><pitch><step>F</step><octave>4</octave></pitch>
                                <duration>16</duration><voice>1</voice><staff>1</staff></note>
                          <barline location="right"><ending number="2" type="stop"/></barline>
                        </measure>
                        """;
        var result = MusicXmlParser.parse(wrap(body));

        List<Integer> midis = result.performance().score().tracks().get(0)
                .notes().stream().map(n -> ((PitchedNote) n).midi()).toList();
        assertEquals(List.of(60, 62, 64, 62, 65), midis,
                "expected pass-1 plays volta 1 (E), pass-2 skips to volta 2 (F)");

        var rs = result.repeatStructure();
        assertEquals(2, rs.voltas().size(), "two volta blocks");
        assertEquals(List.of(1), rs.voltas().get(0).numbers());
        assertEquals(List.of(2), rs.voltas().get(1).numbers());
    }

    @Test
    void daCapoAlFineRoutesThroughStartAndStopsAtFine() {
        // M1, M2 [fine], M3 [D.C. al Fine].
        // Pass 1: M1, M2, M3 → reach D.C., return to M1.
        // Pass 2: M1, M2 → fine → stop.
        // Expected playback: C, D, E, C, D → 5 notes.
        String body =
                "<measure number=\"1\">" + ATTRS + """
                          <note><pitch><step>C</step><octave>4</octave></pitch>
                                <duration>16</duration><voice>1</voice><staff>1</staff></note>
                        </measure>
                        """
                + """
                        <measure number="2">
                          <note><pitch><step>D</step><octave>4</octave></pitch>
                                <duration>16</duration><voice>1</voice><staff>1</staff></note>
                          <direction>
                            <direction-type><words>Fine</words></direction-type>
                            <sound fine="yes"/>
                          </direction>
                        </measure>
                        <measure number="3">
                          <note><pitch><step>E</step><octave>4</octave></pitch>
                                <duration>16</duration><voice>1</voice><staff>1</staff></note>
                          <direction>
                            <direction-type><words>D.C. al Fine</words></direction-type>
                            <sound dacapo="yes"/>
                          </direction>
                        </measure>
                        """;
        var result = MusicXmlParser.parse(wrap(body));

        List<Integer> midis = result.performance().score().tracks().get(0)
                .notes().stream().map(n -> ((PitchedNote) n).midi()).toList();
        assertEquals(List.of(60, 62, 64, 60, 62), midis);

        var rs = result.repeatStructure();
        assertEquals(2, rs.jumps().size());
        boolean hasDacapo = rs.jumps().stream().anyMatch(j -> j.kind() == JumpKind.DACAPO);
        boolean hasFine   = rs.jumps().stream().anyMatch(j -> j.kind() == JumpKind.FINE);
        assertTrue(hasDacapo);
        assertTrue(hasFine);
    }

    @Test
    void noRepeatsLeavesStructureEmpty() {
        String body =
                "<measure number=\"1\">" + ATTRS + """
                          <note><pitch><step>C</step><octave>4</octave></pitch>
                                <duration>16</duration><voice>1</voice><staff>1</staff></note>
                        </measure>
                        """;
        var result = MusicXmlParser.parse(wrap(body));
        assertTrue(result.repeatStructure().isEmpty());
        assertFalse(result.performance().score().tracks().isEmpty());
    }
}
