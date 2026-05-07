package music.notation.mxl;

import music.notation.expressivity.PedalChange;
import music.notation.expressivity.PedalControl;
import music.notation.expressivity.PedalState;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sustain-pedal extraction — {@code <direction><direction-type><pedal/>}
 * markings produce CC #64-bound {@link PedalChange} events on every track
 * of the affected part (the damper applies to the whole instrument).
 */
class MusicXmlParserPedalTest {

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
    void startStopMarkingsBecomeDownUpEvents() {
        String xml = wrap("""
                <measure number="1">
                  <attributes>
                    <divisions>4</divisions>
                    <key><fifths>0</fifths><mode>major</mode></key>
                    <time><beats>4</beats><beat-type>4</beat-type></time>
                    <staves>1</staves>
                  </attributes>
                  <direction>
                    <direction-type><pedal type="start" line="yes"/></direction-type>
                  </direction>
                  <note><pitch><step>C</step><octave>4</octave></pitch>
                        <duration>16</duration><voice>1</voice><staff>1</staff></note>
                  <direction>
                    <direction-type><pedal type="stop" line="yes"/></direction-type>
                  </direction>
                </measure>
                """);
        var result = MusicXmlParser.parse(xml);
        var byTrack = result.performance().pedaling().byTrack();
        assertEquals(1, byTrack.size(), "single-track piece → one pedal control");

        PedalControl pc = byTrack.values().iterator().next();
        List<PedalChange> changes = pc.changes();
        assertEquals(2, changes.size());
        assertEquals(PedalState.DOWN, changes.get(0).state());
        assertEquals(0L,               changes.get(0).tickMs());
        assertEquals(PedalState.UP,    changes.get(1).state());
    }

    @Test
    void changeMarkingProducesChangeState() {
        String xml = wrap("""
                <measure number="1">
                  <attributes>
                    <divisions>4</divisions>
                    <key><fifths>0</fifths><mode>major</mode></key>
                    <time><beats>4</beats><beat-type>4</beat-type></time>
                    <staves>1</staves>
                  </attributes>
                  <direction><direction-type><pedal type="start"/></direction-type></direction>
                  <note><pitch><step>C</step><octave>4</octave></pitch>
                        <duration>8</duration><voice>1</voice><staff>1</staff></note>
                  <direction><direction-type><pedal type="change"/></direction-type></direction>
                  <note><pitch><step>D</step><octave>4</octave></pitch>
                        <duration>8</duration><voice>1</voice><staff>1</staff></note>
                </measure>
                """);
        var result = MusicXmlParser.parse(xml);
        PedalControl pc = result.performance().pedaling().byTrack()
                .values().iterator().next();

        List<PedalChange> changes = pc.changes();
        assertEquals(2, changes.size());
        assertEquals(PedalState.DOWN,   changes.get(0).state());
        assertEquals(PedalState.CHANGE, changes.get(1).state());
    }

    @Test
    void continueMarkingIsIgnored() {
        // <pedal type="continue"/> is visual-only — should not emit an event.
        String xml = wrap("""
                <measure number="1">
                  <attributes>
                    <divisions>4</divisions>
                    <key><fifths>0</fifths><mode>major</mode></key>
                    <time><beats>4</beats><beat-type>4</beat-type></time>
                    <staves>1</staves>
                  </attributes>
                  <direction><direction-type><pedal type="start"/></direction-type></direction>
                  <direction><direction-type><pedal type="continue"/></direction-type></direction>
                  <direction><direction-type><pedal type="stop"/></direction-type></direction>
                  <note><pitch><step>C</step><octave>4</octave></pitch>
                        <duration>16</duration><voice>1</voice><staff>1</staff></note>
                </measure>
                """);
        var result = MusicXmlParser.parse(xml);
        PedalControl pc = result.performance().pedaling().byTrack()
                .values().iterator().next();
        assertEquals(2, pc.changes().size(),
                "continue should be ignored — only start + stop emit");
    }

    @Test
    void pieceWithoutPedalMarkingsHasEmptyPedaling() {
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
        assertTrue(result.performance().pedaling().byTrack().isEmpty());
    }

    // ── Real-file regression: Chopin Nocturne ───────────────────────────

    @Test
    void chopinNocturneHasPopulatedPedaling() throws IOException {
        try (InputStream in = MusicXmlParserPedalTest.class
                .getResourceAsStream("/Chopin_Nocturne_Op9_No1.mxl")) {
            assertNotNull(in);
            var result = MusicXmlParser.parse(MxlContainer.open(in.readAllBytes()).rootXml());
            var byTrack = result.performance().pedaling().byTrack();
            assertFalse(byTrack.isEmpty(),
                    "Chopin Nocturne is heavily pedalled — Pedaling should be populated");

            // Sanity: at least one track has many pedal events (the source has
            // 328 <pedal> markings in total; spread across the part's tracks).
            int totalChanges = byTrack.values().stream()
                    .mapToInt(pc -> pc.changes().size())
                    .sum();
            assertTrue(totalChanges > 100,
                    "expected more than 100 pedal changes total, got " + totalChanges);
        }
    }
}
