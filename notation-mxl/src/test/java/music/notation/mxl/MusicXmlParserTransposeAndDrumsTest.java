package music.notation.mxl;

import music.notation.performance.DrumNote;
import music.notation.performance.PitchedNote;
import music.notation.performance.Track;
import music.notation.performance.TrackKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Landing 1f: transposing instruments stored as sounding pitch with a
 * {@link Transpositions} sidecar, and unpitched percussion routed into a
 * single DRUM track via the {@code <part-list>} percussion map.
 */
class MusicXmlParserTransposeAndDrumsTest {

    @Test
    void bFlatClarinetWritesSoundingPitchAndCapturesOffset() {
        // <transpose chromatic="-2"> on the only part. A written C4 (MIDI 60)
        // should be stored as MIDI 58 (B♭3) in the Performance, and the
        // Transpositions sidecar should record chromatic = -2 for that track.
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <score-partwise>
                  <part-list><score-part id="P1"><part-name>Clarinet</part-name></score-part></part-list>
                  <part id="P1">
                    <measure number="1">
                      <attributes>
                        <divisions>4</divisions>
                        <key><fifths>0</fifths><mode>major</mode></key>
                        <time><beats>4</beats><beat-type>4</beat-type></time>
                        <staves>1</staves>
                        <transpose>
                          <diatonic>-1</diatonic>
                          <chromatic>-2</chromatic>
                        </transpose>
                      </attributes>
                      <note>
                        <pitch><step>C</step><octave>4</octave></pitch>
                        <duration>16</duration><voice>1</voice><staff>1</staff>
                      </note>
                    </measure>
                  </part>
                </score-partwise>
                """;
        var result = MusicXmlParser.parse(xml);
        Track track = result.performance().score().tracks().get(0);
        assertEquals(58, ((PitchedNote) track.notes().get(0)).midi(),
                "written C4 → sounding B♭3 (MIDI 58)");

        var transpositions = result.transpositions();
        assertFalse(transpositions.isEmpty());
        Transpose t = transpositions.byTrack().get(track.id());
        assertEquals(-2, t.chromatic());
        assertEquals(0, t.octaveChange());
        assertEquals(-2, t.totalSemitones());
    }

    @Test
    void unpitchedNotesRouteToSingleDrumTrack() {
        // Drum part with three percussion instruments mapped via <midi-unpitched>.
        // GM percussion: 36 = kick, 38 = snare, 42 = closed hi-hat. The MIDI
        // value stored on DrumNote.piece() is (midi-unpitched - 1) per the
        // MusicXML 1..128 → MIDI 0..127 convention.
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <score-partwise>
                  <part-list>
                    <score-part id="P1">
                      <part-name>Drum Set</part-name>
                      <midi-instrument id="P1-K"><midi-unpitched>37</midi-unpitched></midi-instrument>
                      <midi-instrument id="P1-S"><midi-unpitched>39</midi-unpitched></midi-instrument>
                      <midi-instrument id="P1-H"><midi-unpitched>43</midi-unpitched></midi-instrument>
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
                        <unpitched><display-step>F</display-step><display-octave>4</display-octave></unpitched>
                        <duration>4</duration><instrument id="P1-K"/>
                        <voice>1</voice><staff>1</staff>
                      </note>
                      <note>
                        <unpitched><display-step>C</display-step><display-octave>5</display-octave></unpitched>
                        <duration>4</duration><instrument id="P1-S"/>
                        <voice>1</voice><staff>1</staff>
                      </note>
                      <note>
                        <unpitched><display-step>G</display-step><display-octave>5</display-octave></unpitched>
                        <duration>4</duration><instrument id="P1-H"/>
                        <voice>1</voice><staff>1</staff>
                      </note>
                      <note>
                        <unpitched><display-step>C</display-step><display-octave>5</display-octave></unpitched>
                        <duration>4</duration><instrument id="P1-S"/>
                        <voice>1</voice><staff>1</staff>
                      </note>
                    </measure>
                  </part>
                </score-partwise>
                """;
        var result = MusicXmlParser.parse(xml);

        // Exactly one track, kind DRUM.
        assertEquals(1, result.performance().score().tracks().size());
        Track drums = result.performance().score().tracks().get(0);
        assertEquals(TrackKind.DRUM, drums.kind());
        assertEquals("Drums", drums.id().name());
        assertEquals(4, drums.notes().size());

        // Notes (sorted by Track's canonical ordering — onset then drum piece).
        // Pieces: kick (36), snare (38), hi-hat (42), snare (38).
        // After Track sorts by tickMs then piece, expect at tick 0: 36; at q=1q: 38; etc.
        for (var n : drums.notes()) {
            assertTrue(n instanceof DrumNote);
        }
    }

    @Test
    void unpitchedWithoutInstrumentRefUsesFirstInPart() {
        // Some files omit <instrument> on a percussion note when the part
        // has only one drum sound. Parser should fall back to the first
        // mapped percussion in the part.
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <score-partwise>
                  <part-list>
                    <score-part id="P1">
                      <part-name>Bass Drum</part-name>
                      <midi-instrument id="P1-K"><midi-unpitched>37</midi-unpitched></midi-instrument>
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
                        <unpitched><display-step>F</display-step><display-octave>4</display-octave></unpitched>
                        <duration>4</duration><voice>1</voice><staff>1</staff>
                      </note>
                    </measure>
                  </part>
                </score-partwise>
                """;
        var result = MusicXmlParser.parse(xml);
        Track drums = result.performance().score().tracks().get(0);
        assertEquals(1, drums.notes().size());
        assertEquals(36, ((DrumNote) drums.notes().get(0)).piece(),
                "midi-unpitched 37 → MIDI 36 (kick)");
    }

    @Test
    void noTransposingPartLeavesSidecarEmpty() {
        // Sanity: parsing a normal piece (no <transpose>) yields empty Transpositions.
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <score-partwise>
                  <part-list><score-part id="P1"><part-name>P</part-name></score-part></part-list>
                  <part id="P1">
                    <measure number="1">
                      <attributes>
                        <divisions>4</divisions>
                        <key><fifths>0</fifths><mode>major</mode></key>
                        <time><beats>4</beats><beat-type>4</beat-type></time>
                        <staves>1</staves>
                      </attributes>
                      <note>
                        <pitch><step>C</step><octave>4</octave></pitch>
                        <duration>16</duration><voice>1</voice><staff>1</staff>
                      </note>
                    </measure>
                  </part>
                </score-partwise>
                """;
        var result = MusicXmlParser.parse(xml);
        assertTrue(result.transpositions().isEmpty());
    }
}
