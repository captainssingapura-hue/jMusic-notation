package music.notation.mxl;

import music.notation.event.Dynamic;
import music.notation.expressivity.Articulation;
import music.notation.expressivity.ArticulationControl;
import music.notation.expressivity.Loudness;
import music.notation.expressivity.VelocityControl;
import music.notation.expressivity.VolumeControl;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicXmlParserDynamicsTest {

    private static final String STAVES_PROLOGUE = """
            <attributes>
              <divisions>4</divisions>
              <key><fifths>0</fifths><mode>major</mode></key>
              <time><beats>4</beats><beat-type>4</beat-type></time>
              <staves>1</staves>
            </attributes>
            """;

    @Test
    void numericSoundDynamicsBecomesVolumeChange() {
        // A bare <sound dynamics="…"/> (no symbolic mark) is a MusicXML
        // percentage; it lands as a Loudness.Raw with level = pct / 100.
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <score-partwise>
                  <part-list><score-part id="P1"><part-name>P</part-name></score-part></part-list>
                  <part id="P1">
                    <measure number="1">
                      %s
                      <direction placement="below">
                        <staff>1</staff>
                        <sound dynamics="54.44"/>
                      </direction>
                      <note>
                        <pitch><step>C</step><octave>4</octave></pitch>
                        <duration>16</duration><voice>1</voice><staff>1</staff>
                      </note>
                    </measure>
                  </part>
                </score-partwise>
                """.formatted(STAVES_PROLOGUE);

        var result = MusicXmlParser.parse(xml);
        var volume = result.performance().volume().byTrack();
        assertEquals(1, volume.size(), "expected one volume entry for the single track");
        VolumeControl vc = volume.values().iterator().next();
        assertEquals(1, vc.changes().size());
        assertEquals(new Loudness.Raw(0.5444), vc.changes().get(0).loudness(),
                "numeric percentage → Raw(pct / 100), no symbol fabricated");
        assertEquals(0.5444, vc.changes().get(0).level(), 1e-9);
        assertTrue(vc.changes().get(0).at().isZero());

        // The same dynamic also drives per-note velocity — same Loudness
        // at the same musical position.
        var velocities = result.performance().velocities().byTrack();
        assertEquals(1, velocities.size());
        VelocityControl velCtrl = velocities.values().iterator().next();
        assertEquals(1, velCtrl.changes().size());
        assertEquals(new Loudness.Raw(0.5444), velCtrl.changes().get(0).loudness());
        assertTrue(velCtrl.changes().get(0).at().isZero());
    }

    @Test
    void symbolicMarkWinsOverSiblingSoundDynamics() {
        // When a <direction> carries both a symbolic <dynamics> mark and a
        // numeric <sound dynamics>, the authored symbol carries intent and
        // wins: the model keeps Named(P), not Raw(0.5444).
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <score-partwise>
                  <part-list><score-part id="P1"><part-name>P</part-name></score-part></part-list>
                  <part id="P1">
                    <measure number="1">
                      %s
                      <direction placement="below">
                        <direction-type><dynamics><p/></dynamics></direction-type>
                        <staff>1</staff>
                        <sound dynamics="54.44"/>
                      </direction>
                      <note>
                        <pitch><step>C</step><octave>4</octave></pitch>
                        <duration>16</duration><voice>1</voice><staff>1</staff>
                      </note>
                    </measure>
                  </part>
                </score-partwise>
                """.formatted(STAVES_PROLOGUE);

        var result = MusicXmlParser.parse(xml);
        VolumeControl vc = result.performance().volume().byTrack().values().iterator().next();
        assertEquals(1, vc.changes().size());
        assertEquals(new Loudness.Named(Dynamic.P), vc.changes().get(0).loudness());
        assertTrue(vc.changes().get(0).at().isZero());

        VelocityControl velCtrl = result.performance().velocities().byTrack().values().iterator().next();
        assertEquals(1, velCtrl.changes().size());
        assertEquals(new Loudness.Named(Dynamic.P), velCtrl.changes().get(0).loudness());
    }

    @Test
    void symbolicDynamicMapsToNamedLoudness() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <score-partwise>
                  <part-list><score-part id="P1"><part-name>P</part-name></score-part></part-list>
                  <part id="P1">
                    <measure number="1">
                      %s
                      <direction>
                        <direction-type><dynamics><ff/></dynamics></direction-type>
                      </direction>
                      <note>
                        <pitch><step>C</step><octave>4</octave></pitch>
                        <duration>16</duration><voice>1</voice><staff>1</staff>
                      </note>
                    </measure>
                  </part>
                </score-partwise>
                """.formatted(STAVES_PROLOGUE);

        var result = MusicXmlParser.parse(xml);
        VolumeControl vc = result.performance().volume().byTrack().values().iterator().next();
        assertEquals(new Loudness.Named(Dynamic.FF), vc.changes().get(0).loudness(),
                "ff → Named(FF): the authored glyph survives");
        assertEquals(Dynamic.FF.level(), vc.changes().get(0).level(), 1e-9,
                "ff resolves to the conventional 0.85 level");
    }

    @Test
    void staccatoNoteEmitsArticulationChange() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <score-partwise>
                  <part-list><score-part id="P1"><part-name>P</part-name></score-part></part-list>
                  <part id="P1">
                    <measure number="1">
                      %s
                      <note>
                        <pitch><step>C</step><octave>4</octave></pitch>
                        <duration>16</duration><voice>1</voice><staff>1</staff>
                        <notations><articulations><staccato/></articulations></notations>
                      </note>
                      <note>
                        <pitch><step>D</step><octave>4</octave></pitch>
                        <duration>16</duration><voice>1</voice><staff>1</staff>
                      </note>
                    </measure>
                  </part>
                </score-partwise>
                """.formatted(STAVES_PROLOGUE);

        var result = MusicXmlParser.parse(xml);
        var artic = result.performance().articulations().byTrack();
        assertEquals(1, artic.size());
        ArticulationControl ac = artic.values().iterator().next();
        // Expect STACCATO at note 1 onset (0), then NORMAL at note 2 onset.
        assertEquals(2, ac.changes().size());
        assertEquals(Articulation.STACCATO, ac.changes().get(0).kind());
        assertTrue(ac.changes().get(0).at().isZero());
        assertEquals(Articulation.NORMAL, ac.changes().get(1).kind());
    }

    @Test
    void slurMarksLegatoSpan() {
        // Three notes with a slur from note 1 (start) to note 3 (stop).
        // Notes inside the slur are LEGATO; the next non-slur note (none here)
        // would emit NORMAL. Since the slur ends on the last note, no NORMAL
        // change is emitted unless followed by a different-articulation note.
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <score-partwise>
                  <part-list><score-part id="P1"><part-name>P</part-name></score-part></part-list>
                  <part id="P1">
                    <measure number="1">
                      %s
                      <note>
                        <pitch><step>C</step><octave>4</octave></pitch>
                        <duration>4</duration><voice>1</voice><staff>1</staff>
                        <notations><slur type="start" number="1"/></notations>
                      </note>
                      <note>
                        <pitch><step>D</step><octave>4</octave></pitch>
                        <duration>4</duration><voice>1</voice><staff>1</staff>
                      </note>
                      <note>
                        <pitch><step>E</step><octave>4</octave></pitch>
                        <duration>4</duration><voice>1</voice><staff>1</staff>
                        <notations><slur type="stop" number="1"/></notations>
                      </note>
                      <note>
                        <pitch><step>F</step><octave>4</octave></pitch>
                        <duration>4</duration><voice>1</voice><staff>1</staff>
                      </note>
                    </measure>
                  </part>
                </score-partwise>
                """.formatted(STAVES_PROLOGUE);

        var result = MusicXmlParser.parse(xml);
        ArticulationControl ac = result.performance().articulations().byTrack()
                .values().iterator().next();
        assertEquals(2, ac.changes().size(), "LEGATO at note 1, NORMAL at note 4 (post-slur)");
        assertEquals(Articulation.LEGATO, ac.changes().get(0).kind());
        assertTrue(ac.changes().get(0).at().isZero());
        assertEquals(Articulation.NORMAL, ac.changes().get(1).kind());
    }

    @Test
    void chopinHasPopulatedVolumeAndArticulations() throws IOException {
        try (InputStream in = MusicXmlParserDynamicsTest.class
                .getResourceAsStream("/Chopin_Nocturne_Op9_No1.mxl")) {
            assertNotNull(in);
            var result = MusicXmlParser.parse(MxlContainer.open(in.readAllBytes()).rootXml());

            var volume = result.performance().volume().byTrack();
            var artic  = result.performance().articulations().byTrack();
            assertFalse(volume.isEmpty(),  "Chopin has many <dynamics> markings → Volume populated");
            assertFalse(artic.isEmpty(),   "Chopin has slurs → Articulations populated");

            // Sanity: at least one Volume entry has multiple changes (rubato dynamics).
            assertTrue(volume.values().stream().anyMatch(vc -> vc.changes().size() > 1),
                    "expected at least one VolumeControl with multiple changes");
        }
    }
}
