package music.notation.mxl;

import music.notation.expressivity.TrackId;
import music.notation.expressivity.VolumeChange;
import music.notation.expressivity.VolumeControl;
import music.notation.performance.InstrumentControl;
import music.notation.performance.Performance;
import music.notation.performance.Track;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for {@code <part-list><score-part><midi-instrument>} pickup
 * — program (instrument selection) and static volume bootstrap. Drum-only
 * percussion mapping ({@code <midi-unpitched>}) is covered separately by
 * {@link MusicXmlParserTransposeAndDrumsTest}.
 *
 * <p>Without this pickup, every imported MXL renders as piano because
 * {@link Performance#instruments()} stays empty and the synth defaults
 * to program 0.</p>
 */
class MusicXmlParserMidiInstrumentTest {

    @Test
    void midiProgramSetsInstrumentationPerPart() {
        // <midi-program>41</midi-program> = MXL 1-indexed Violin.
        // After GM conversion (-1) we expect program 40 on the track.
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <score-partwise>
                  <part-list>
                    <score-part id="P1">
                      <part-name>Violin</part-name>
                      <midi-instrument id="P1-I1">
                        <midi-program>41</midi-program>
                      </midi-instrument>
                    </score-part>
                  </part-list>
                  <part id="P1">
                    <measure number="1">
                      <attributes>
                        <divisions>4</divisions>
                        <key><fifths>0</fifths><mode>major</mode></key>
                        <time><beats>4</beats><beat-type>4</beat-type></time>
                        <staves>1</staves>
                      </attributes>
                      <note>
                        <pitch><step>A</step><octave>4</octave></pitch>
                        <duration>16</duration><voice>1</voice><staff>1</staff>
                      </note>
                    </measure>
                  </part>
                </score-partwise>
                """;
        Performance perf = MusicXmlParser.parse(xml).performance();
        Track track = perf.score().tracks().get(0);

        var byTrack = perf.instruments().byTrack();
        assertFalse(byTrack.isEmpty(),
                "Instrumentation should be populated from <midi-program>");
        InstrumentControl ic = byTrack.get(track.id());
        assertNotNull(ic, "track must have an instrument control");
        assertEquals(1, ic.changes().size());
        assertEquals(0L, ic.changes().get(0).tickMs(),
                "part-level program is anchored at t=0");
        assertEquals(40, ic.changes().get(0).program(),
                "MXL <midi-program>41 (1-indexed) → MIDI program 40 (Violin)");
    }

    @Test
    void distinctPartsGetDistinctPrograms() {
        // Two parts, two different programs — confirm they don't collide.
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <score-partwise>
                  <part-list>
                    <score-part id="P1">
                      <midi-instrument id="P1-I1"><midi-program>41</midi-program></midi-instrument>
                    </score-part>
                    <score-part id="P2">
                      <midi-instrument id="P2-I1"><midi-program>43</midi-program></midi-instrument>
                    </score-part>
                  </part-list>
                  <part id="P1">
                    <measure number="1">
                      <attributes>
                        <divisions>4</divisions>
                        <key><fifths>0</fifths><mode>major</mode></key>
                        <time><beats>4</beats><beat-type>4</beat-type></time>
                        <staves>1</staves>
                      </attributes>
                      <note><pitch><step>G</step><octave>5</octave></pitch>
                        <duration>16</duration><voice>1</voice><staff>1</staff></note>
                    </measure>
                  </part>
                  <part id="P2">
                    <measure number="1">
                      <note><pitch><step>C</step><octave>3</octave></pitch>
                        <duration>16</duration><voice>1</voice><staff>1</staff></note>
                    </measure>
                  </part>
                </score-partwise>
                """;
        Performance perf = MusicXmlParser.parse(xml).performance();
        var byTrack = perf.instruments().byTrack();
        assertEquals(2, byTrack.size());

        // Tracks are ordered by descending average pitch — so P1's G5 is
        // the higher-pitched track, P2's C3 the lower.
        Track high = perf.score().tracks().get(0);
        Track low = perf.score().tracks().get(1);
        assertEquals(40, byTrack.get(high.id()).changes().get(0).program(),
                "high track = Violin (program 40)");
        assertEquals(42, byTrack.get(low.id()).changes().get(0).program(),
                "low track = Cello (program 42)");
    }

    @Test
    void multiStaffPartShareSameProgram() {
        // Multi-staff piano: one <score-part>, two staves → both tracks
        // get program 0 (Acoustic Grand Piano).
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <score-partwise>
                  <part-list>
                    <score-part id="P1">
                      <midi-instrument id="P1-I1"><midi-program>1</midi-program></midi-instrument>
                    </score-part>
                  </part-list>
                  <part id="P1">
                    <measure number="1">
                      <attributes>
                        <divisions>4</divisions>
                        <key><fifths>0</fifths><mode>major</mode></key>
                        <time><beats>4</beats><beat-type>4</beat-type></time>
                        <staves>2</staves>
                      </attributes>
                      <note><pitch><step>C</step><octave>5</octave></pitch>
                        <duration>16</duration><voice>1</voice><staff>1</staff></note>
                      <backup><duration>16</duration></backup>
                      <note><pitch><step>C</step><octave>3</octave></pitch>
                        <duration>16</duration><voice>2</voice><staff>2</staff></note>
                    </measure>
                  </part>
                </score-partwise>
                """;
        Performance perf = MusicXmlParser.parse(xml).performance();
        var byTrack = perf.instruments().byTrack();
        assertEquals(2, perf.score().tracks().size(),
                "two staves → two tracks");
        for (Track t : perf.score().tracks()) {
            InstrumentControl ic = byTrack.get(t.id());
            assertNotNull(ic, "every track of a part with <midi-program> "
                    + "should have an instrument control");
            assertEquals(0, ic.changes().get(0).program(),
                    "MXL <midi-program>1 → GM 0 (Acoustic Grand Piano)");
        }
    }

    @Test
    void noMidiProgramLeavesInstrumentationEmpty() {
        // No <midi-instrument> at all → backwards-compat: no entry in
        // Instrumentation, fallback to synth default at concretization.
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <score-partwise>
                  <part-list><score-part id="P1"><part-name>Voice</part-name></score-part></part-list>
                  <part id="P1">
                    <measure number="1">
                      <attributes>
                        <divisions>4</divisions>
                        <key><fifths>0</fifths><mode>major</mode></key>
                        <time><beats>4</beats><beat-type>4</beat-type></time>
                        <staves>1</staves>
                      </attributes>
                      <note><pitch><step>D</step><octave>4</octave></pitch>
                        <duration>16</duration><voice>1</voice><staff>1</staff></note>
                    </measure>
                  </part>
                </score-partwise>
                """;
        Performance perf = MusicXmlParser.parse(xml).performance();
        assertTrue(perf.instruments().byTrack().isEmpty(),
                "no <midi-program> → empty Instrumentation (synth default applies)");
    }

    @Test
    void programOutOfRangeIsClamped() {
        // <midi-program>200</midi-program> — rare but seen in some
        // exporters that emit values outside 1..128. After -1 conversion
        // we'd get 199 which is invalid MIDI; clamp to 127.
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <score-partwise>
                  <part-list>
                    <score-part id="P1">
                      <midi-instrument id="P1-I1"><midi-program>200</midi-program></midi-instrument>
                    </score-part>
                  </part-list>
                  <part id="P1">
                    <measure number="1">
                      <attributes>
                        <divisions>4</divisions>
                        <key><fifths>0</fifths><mode>major</mode></key>
                        <time><beats>4</beats><beat-type>4</beat-type></time>
                        <staves>1</staves>
                      </attributes>
                      <note><pitch><step>E</step><octave>4</octave></pitch>
                        <duration>16</duration><voice>1</voice><staff>1</staff></note>
                    </measure>
                  </part>
                </score-partwise>
                """;
        Performance perf = MusicXmlParser.parse(xml).performance();
        Track t = perf.score().tracks().get(0);
        assertEquals(127, perf.instruments().byTrack().get(t.id())
                .changes().get(0).program(),
                "program 200 (out of range) clamps to 127");
    }

    @Test
    void staticVolumeSeedsVolumeAndVelocityWhenNoDynamics() {
        // Part declares <volume>78</volume> (78% in MXL 0..100 scale) but
        // no <dynamics>. We expect a single VolumeChange + VelocityChange
        // at t=0 carrying the scaled value.
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <score-partwise>
                  <part-list>
                    <score-part id="P1">
                      <midi-instrument id="P1-I1">
                        <midi-program>1</midi-program>
                        <volume>78</volume>
                      </midi-instrument>
                    </score-part>
                  </part-list>
                  <part id="P1">
                    <measure number="1">
                      <attributes>
                        <divisions>4</divisions>
                        <key><fifths>0</fifths><mode>major</mode></key>
                        <time><beats>4</beats><beat-type>4</beat-type></time>
                        <staves>1</staves>
                      </attributes>
                      <note><pitch><step>F</step><octave>4</octave></pitch>
                        <duration>16</duration><voice>1</voice><staff>1</staff></note>
                    </measure>
                  </part>
                </score-partwise>
                """;
        Performance perf = MusicXmlParser.parse(xml).performance();
        Track t = perf.score().tracks().get(0);

        VolumeControl vc = perf.volume().byTrack().get(t.id());
        assertNotNull(vc, "<volume> with no <dynamics> → seeded VolumeControl");
        assertEquals(1, vc.changes().size());
        VolumeChange v = vc.changes().get(0);
        assertEquals(0L, v.tickMs());
        // 78% of 127 ≈ 99 (round half up).
        assertEquals(99, v.level(), "78 (MXL %) → 99 (MIDI 0..127)");

        var velCtrl = perf.velocities().byTrack().get(t.id());
        assertNotNull(velCtrl, "static <volume> also seeds Velocities");
        assertEquals(99, velCtrl.changes().get(0).velocity());
    }

    @Test
    void dynamicsTakePrecedenceOverStaticVolume() {
        // Part has BOTH <volume>50</volume> AND a <dynamics><f/></dynamics>
        // direction. Authored dynamics win — the static volume is ignored.
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <score-partwise>
                  <part-list>
                    <score-part id="P1">
                      <midi-instrument id="P1-I1">
                        <midi-program>1</midi-program>
                        <volume>50</volume>
                      </midi-instrument>
                    </score-part>
                  </part-list>
                  <part id="P1">
                    <measure number="1">
                      <attributes>
                        <divisions>4</divisions>
                        <key><fifths>0</fifths><mode>major</mode></key>
                        <time><beats>4</beats><beat-type>4</beat-type></time>
                        <staves>1</staves>
                      </attributes>
                      <direction placement="below">
                        <direction-type><dynamics><f/></dynamics></direction-type>
                      </direction>
                      <note><pitch><step>G</step><octave>4</octave></pitch>
                        <duration>16</duration><voice>1</voice><staff>1</staff></note>
                    </measure>
                  </part>
                </score-partwise>
                """;
        Performance perf = MusicXmlParser.parse(xml).performance();
        Track t = perf.score().tracks().get(0);
        VolumeControl vc = perf.volume().byTrack().get(t.id());
        assertNotNull(vc);
        assertEquals(1, vc.changes().size(),
                "only the dynamics-derived change should land — no static seed");
        // <f> maps to a known cc7 (parser's dynamics table); we just verify
        // it isn't 50% (50→63) i.e. the static volume.
        int level = vc.changes().get(0).level();
        assertTrue(level > 80,
                "<f> should be louder than 78 — got " + level);
    }

    @Test
    void volumeFractionFormScalesCorrectly() {
        // MusicXML 3.x sometimes emits <volume> as a 0.0..1.0 fraction.
        // 0.5 should map to ~64.
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <score-partwise>
                  <part-list>
                    <score-part id="P1">
                      <midi-instrument id="P1-I1">
                        <midi-program>1</midi-program>
                        <volume>0.5</volume>
                      </midi-instrument>
                    </score-part>
                  </part-list>
                  <part id="P1">
                    <measure number="1">
                      <attributes>
                        <divisions>4</divisions>
                        <key><fifths>0</fifths><mode>major</mode></key>
                        <time><beats>4</beats><beat-type>4</beat-type></time>
                        <staves>1</staves>
                      </attributes>
                      <note><pitch><step>A</step><octave>4</octave></pitch>
                        <duration>16</duration><voice>1</voice><staff>1</staff></note>
                    </measure>
                  </part>
                </score-partwise>
                """;
        Performance perf = MusicXmlParser.parse(xml).performance();
        Track t = perf.score().tracks().get(0);
        VolumeControl vc = perf.volume().byTrack().get(t.id());
        assertNotNull(vc);
        assertEquals(64, vc.changes().get(0).level(),
                "0.5 (fraction) → 64 (MIDI 0..127)");
    }

    @Test
    void drumOnlyPartIsNotMistakenForMelodicProgram() {
        // A drum part has only <midi-unpitched>; no <midi-program> for
        // melodic. We must NOT seed Instrumentation off the drum entry.
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <score-partwise>
                  <part-list>
                    <score-part id="P1">
                      <part-name>Drum</part-name>
                      <midi-instrument id="P1-K"><midi-unpitched>36</midi-unpitched></midi-instrument>
                    </score-part>
                  </part-list>
                  <part id="P1">
                    <measure number="1">
                      <attributes>
                        <divisions>4</divisions>
                        <key><fifths>0</fifths><mode>major</mode></key>
                        <time><beats>4</beats><beat-type>4</beat-type></time>
                        <staves>1</staves>
                      </attributes>
                      <note>
                        <unpitched/>
                        <duration>16</duration>
                        <voice>1</voice>
                        <instrument id="P1-K"/>
                      </note>
                    </measure>
                  </part>
                </score-partwise>
                """;
        Performance perf = MusicXmlParser.parse(xml).performance();
        // Drum tracks are not keyed in Instrumentation (drums are channel 9
        // and use bank-select for kit choice, not program).
        for (Track t : perf.score().tracks()) {
            assertNull(perf.instruments().byTrack().get(t.id()),
                    "drum-only part should not produce Instrumentation entries");
        }
    }
}
