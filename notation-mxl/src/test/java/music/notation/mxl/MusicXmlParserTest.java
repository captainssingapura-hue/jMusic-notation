package music.notation.mxl;

import music.notation.performance.PitchedNote;
import music.notation.performance.Score;
import music.notation.performance.Track;
import music.notation.pitch.NoteName;
import music.notation.structure.KeySignature;
import music.notation.structure.Mode;
import music.notation.structure.TimeSignature;
import music.notation.pitch.Accidental;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicXmlParserTest {

    private static String chopinXml() throws IOException {
        try (InputStream in = MusicXmlParserTest.class.getResourceAsStream(
                "/Chopin_Nocturne_Op9_No1.mxl")) {
            assertNotNull(in);
            byte[] bytes = in.readAllBytes();
            return MxlContainer.open(bytes).rootXml();
        }
    }

    @Test
    void parsesChopinScoreMeta() throws IOException {
        var result = MusicXmlParser.parse(chopinXml());

        assertEquals(new TimeSignature(6, 4), result.timeSig());
        assertEquals(new KeySignature(NoteName.D, Accidental.FLAT, Mode.MAJOR), result.key());

        // Chopin's rubato gives many tempo changes; the initial entry must be 116 at tick 0.
        var changes = result.performance().tempo().changes();
        assertTrue(changes.size() >= 1, "expected at least one tempo entry");
        assertEquals(116, changes.get(0).bpm(), "initial tempo should be 116 bpm");
        assertEquals(0L, changes.get(0).tickMs(), "first tempo entry anchored at tick 0");
        assertTrue(changes.size() > 1,
                "Chopin Nocturne uses rubato — expected multiple tempo changes, got "
                        + changes.size());
    }

    @Test
    void splitsStavesIntoSeparateTracks() throws IOException {
        var result = MusicXmlParser.parse(chopinXml());
        Score score = result.performance().score();

        assertTrue(score.tracks().size() >= 2,
                "Chopin Nocturne has 2 staves → expect ≥2 tracks, got " + score.tracks().size());

        boolean hasStaff1 = score.tracks().stream()
                .anyMatch(t -> t.id().name().contains("staff 1"));
        boolean hasStaff2 = score.tracks().stream()
                .anyMatch(t -> t.id().name().contains("staff 2"));
        assertTrue(hasStaff1, "expected a track for staff 1");
        assertTrue(hasStaff2, "expected a track for staff 2");
    }

    @Test
    void firstTrebleNoteIsBflat5AtTickZero() throws IOException {
        var result = MusicXmlParser.parse(chopinXml());
        Track treble = result.performance().score().tracks().stream()
                .filter(t -> t.id().name().contains("staff 1"))
                .findFirst()
                .orElseThrow();

        PitchedNote first = (PitchedNote) treble.notes().get(0);
        assertEquals(0, first.tickMs(), "first note should start at tick 0");
        assertEquals(82, first.midi(), "first note is Bb5 → MIDI 82");

        // Eighth note at 116 bpm with divisions=480 → ~258.6 ms
        long quarter = Math.round(60_000.0 / 116);
        long expectedEighth = Math.round(quarter / 2.0);
        long tolerance = 2;
        assertTrue(Math.abs(first.durationMs() - expectedEighth) <= tolerance,
                "eighth note at 116 bpm should be ~" + expectedEighth +
                " ms, got " + first.durationMs());
    }

    @Test
    void chordsShareOnsetAcrossNotes() {
        // Synthetic minimal score with a 2-note chord on the first beat.
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
                        <duration>4</duration><voice>1</voice><staff>1</staff>
                      </note>
                      <note>
                        <chord/>
                        <pitch><step>E</step><octave>4</octave></pitch>
                        <duration>4</duration><voice>1</voice><staff>1</staff>
                      </note>
                      <note>
                        <chord/>
                        <pitch><step>G</step><octave>4</octave></pitch>
                        <duration>4</duration><voice>1</voice><staff>1</staff>
                      </note>
                    </measure>
                  </part>
                </score-partwise>
                """;
        var result = MusicXmlParser.parse(xml);
        Track t = result.performance().score().tracks().get(0);
        assertEquals(3, t.notes().size());
        long onset = ((PitchedNote) t.notes().get(0)).tickMs();
        for (var n : t.notes()) {
            assertEquals(onset, n.tickMs(), "all chord notes share the same onset");
        }
        // Pitches: C4=60, E4=64, G4=67 — independent of order Track may canonicalise.
        var midis = t.notes().stream()
                .map(n -> ((PitchedNote) n).midi())
                .sorted()
                .toList();
        assertEquals(java.util.List.of(60, 64, 67), midis);
    }

    @Test
    void backupRewindsCursorWithinMeasure() {
        // Two voices in one measure: voice 1 fills with C4 quarter ×4, then
        // backup rewinds and voice 2 fills with G3 quarter ×4. Both voices'
        // notes should start at tick 0, 1 quarter, 2 quarters, 3 quarters.
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
                      <note><pitch><step>C</step><octave>4</octave></pitch>
                            <duration>4</duration><voice>1</voice><staff>1</staff></note>
                      <note><pitch><step>C</step><octave>4</octave></pitch>
                            <duration>4</duration><voice>1</voice><staff>1</staff></note>
                      <note><pitch><step>C</step><octave>4</octave></pitch>
                            <duration>4</duration><voice>1</voice><staff>1</staff></note>
                      <note><pitch><step>C</step><octave>4</octave></pitch>
                            <duration>4</duration><voice>1</voice><staff>1</staff></note>
                      <backup><duration>16</duration></backup>
                      <note><pitch><step>G</step><octave>3</octave></pitch>
                            <duration>4</duration><voice>2</voice><staff>1</staff></note>
                      <note><pitch><step>G</step><octave>3</octave></pitch>
                            <duration>4</duration><voice>2</voice><staff>1</staff></note>
                      <note><pitch><step>G</step><octave>3</octave></pitch>
                            <duration>4</duration><voice>2</voice><staff>1</staff></note>
                      <note><pitch><step>G</step><octave>3</octave></pitch>
                            <duration>4</duration><voice>2</voice><staff>1</staff></note>
                    </measure>
                  </part>
                </score-partwise>
                """;
        var result = MusicXmlParser.parse(xml);
        Score s = result.performance().score();
        assertEquals(2, s.tracks().size(), "two voices → two tracks");

        Track voice1 = s.tracks().stream()
                .filter(t -> t.id().name().endsWith("v1")).findFirst().orElseThrow();
        Track voice2 = s.tracks().stream()
                .filter(t -> t.id().name().endsWith("v2")).findFirst().orElseThrow();

        assertEquals(4, voice1.notes().size());
        assertEquals(4, voice2.notes().size());
        // Both voices share the same onset sequence: 0, q, 2q, 3q.
        for (int i = 0; i < 4; i++) {
            assertEquals(voice1.notes().get(i).tickMs(),
                    voice2.notes().get(i).tickMs(),
                    "voices must align after backup at index " + i);
        }
        assertEquals(0L, voice2.notes().get(0).tickMs(),
                "voice 2 first note should start at tick 0 after backup");
    }

    @Test
    void tracksOrderedByDescendingAveragePitch() {
        // Three voices in one part: high (C5), mid (E4), low (G2). The
        // parser should emit the score with [v1-high, v2-mid, v3-low].
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
                        <pitch><step>G</step><octave>2</octave></pitch>
                        <duration>16</duration><voice>3</voice><staff>1</staff>
                      </note>
                      <backup><duration>16</duration></backup>
                      <note>
                        <pitch><step>E</step><octave>4</octave></pitch>
                        <duration>16</duration><voice>2</voice><staff>1</staff>
                      </note>
                      <backup><duration>16</duration></backup>
                      <note>
                        <pitch><step>C</step><octave>5</octave></pitch>
                        <duration>16</duration><voice>1</voice><staff>1</staff>
                      </note>
                    </measure>
                  </part>
                </score-partwise>
                """;
        var result = MusicXmlParser.parse(xml);
        var tracks = result.performance().score().tracks();
        assertEquals(3, tracks.size());
        // First track contains C5 (MIDI 72), then E4 (64), then G2 (43).
        assertEquals(72, ((PitchedNote) tracks.get(0).notes().get(0)).midi());
        assertEquals(64, ((PitchedNote) tracks.get(1).notes().get(0)).midi());
        assertEquals(43, ((PitchedNote) tracks.get(2).notes().get(0)).midi());
    }

    @Test
    void tieStartIsRecordedOnPitchedNote() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <score-partwise>
                  <part-list><score-part id="P1"><part-name>P</part-name></score-part></part-list>
                  <part id="P1">
                    <measure number="1">
                      <attributes><divisions>4</divisions>
                        <key><fifths>0</fifths><mode>major</mode></key>
                        <time><beats>4</beats><beat-type>4</beat-type></time>
                        <staves>1</staves></attributes>
                      <note>
                        <pitch><step>C</step><octave>4</octave></pitch>
                        <duration>16</duration>
                        <tie type="start"/>
                        <voice>1</voice><staff>1</staff>
                      </note>
                    </measure>
                    <measure number="2">
                      <note>
                        <pitch><step>C</step><octave>4</octave></pitch>
                        <duration>16</duration>
                        <tie type="stop"/>
                        <voice>1</voice><staff>1</staff>
                      </note>
                    </measure>
                  </part>
                </score-partwise>
                """;
        var result = MusicXmlParser.parse(xml);
        Track t = result.performance().score().tracks().get(0);
        assertEquals(2, t.notes().size());
        assertTrue(((PitchedNote) t.notes().get(0)).tiedToNext(),
                "note with <tie type=\"start\"/> should set tiedToNext");
        assertTrue(!((PitchedNote) t.notes().get(1)).tiedToNext(),
                "note with only <tie type=\"stop\"/> should leave tiedToNext false");
    }
}
