package music.notation.experiments.pi;

import music.notation.experiments.blues.minor.BluesMinorConcretizer;
import music.notation.experiments.blues.minor.BluesMinorNote;
import music.notation.experiments.hirajoshi.HirajoshiConcretizer;
import music.notation.experiments.hirajoshi.HirajoshiNote;
import music.notation.performance.MidiCodec;
import music.notation.performance.PitchedNote;
import music.notation.performance.TrackId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the digit-to-degree mapping for both pentatonic and hexatonic
 * scales, and that the resulting Performance round-trips through MidiCodec.
 */
class PiSongTest {

    @Test
    void pentatonic_mapsTenDigitsAcrossTwoOctaves() {
        // Hirajoshi in C: degrees I, II, ♭III, V, ♭VI = 0, 2, 3, 7, 8 semitones from tonic.
        // baseOctave 4 → MIDI base 60 (C4). Digits 0..4 in oct 4, digits 5..9 in oct 5.
        var p = PiSong.build("0123456789",
                HirajoshiNote::ofIndex, HirajoshiConcretizer.inC(),
                /*baseOctave=*/ 4, /*noteMs=*/ 100, /*program=*/ 0);

        var notes = p.score().tracks().get(0).notes();
        assertEquals(10, notes.size());

        int[] expected = {
                60,  // 0 -> deg 0 oct 4 -> C4
                62,  // 1 -> deg 1 oct 4 -> D4
                63,  // 2 -> deg 2 oct 4 -> Eb4
                67,  // 3 -> deg 3 oct 4 -> G4
                68,  // 4 -> deg 4 oct 4 -> Ab4
                72,  // 5 -> deg 0 oct 5 -> C5
                74,  // 6 -> deg 1 oct 5 -> D5
                75,  // 7 -> deg 2 oct 5 -> Eb5
                79,  // 8 -> deg 3 oct 5 -> G5
                80,  // 9 -> deg 4 oct 5 -> Ab5
        };
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], ((PitchedNote) notes.get(i)).midi(),
                    "digit " + i + " should map to midi " + expected[i]);
        }
    }

    @Test
    void hexatonic_mapsAllSixDegreesInBaseOctaveBeforeRising() {
        // Blues minor in C: degrees I, ♭III, IV, ♭V, V, ♭VII = 0, 3, 5, 6, 7, 10 semitones.
        // baseOctave 4 → C4 = 60. Digits 0..5 in oct 4; digits 6..9 occupy degrees 0..3 of oct 5.
        var p = PiSong.build("0123456789",
                BluesMinorNote::ofIndex, BluesMinorConcretizer.inC(),
                /*baseOctave=*/ 4, /*noteMs=*/ 100, /*program=*/ 0);

        var notes = p.score().tracks().get(0).notes();
        assertEquals(10, notes.size());

        int[] expected = {
                60,  // 0 -> deg 0 oct 4 -> C4
                63,  // 1 -> deg 1 oct 4 -> Eb4
                65,  // 2 -> deg 2 oct 4 -> F4
                66,  // 3 -> deg 3 oct 4 -> Gb4
                67,  // 4 -> deg 4 oct 4 -> G4
                70,  // 5 -> deg 5 oct 4 -> Bb4
                72,  // 6 -> deg 0 oct 5 -> C5
                75,  // 7 -> deg 1 oct 5 -> Eb5
                77,  // 8 -> deg 2 oct 5 -> F5
                78,  // 9 -> deg 3 oct 5 -> Gb5
        };
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], ((PitchedNote) notes.get(i)).midi(),
                    "digit " + i + " should map to midi " + expected[i]);
        }
    }

    @Test
    void nonDigitChars_areSkipped() {
        var p = PiSong.build("3.14",
                HirajoshiNote::ofIndex, HirajoshiConcretizer.inC(),
                4, 100, 0);
        // '3', '1', '4' counted; '.' skipped → 3 notes.
        assertEquals(3, p.score().tracks().get(0).notes().size());
    }

    @Test
    void canon_buildsOneTrackPerVoice_eachOctaveStaggered() {
        var voices = List.of(
                new PiSong.Voice(new TrackId("v0"), 5, 0),
                new PiSong.Voice(new TrackId("v1"), 4, 4),
                new PiSong.Voice(new TrackId("v2"), 3, 8)
        );
        var p = PiSong.buildCanon("0",  // single digit -> degree 0 oct base
                HirajoshiNote::ofIndex, HirajoshiConcretizer.inC(),
                voices, 100, 107);

        var tracks = p.score().tracks();
        assertEquals(3, tracks.size());

        // Each voice has one note (digit "0" -> degree 0, octave = baseOctave).
        // Hirajoshi tonic = C, so midi = 12*(oct+1).
        // v0 at oct 5 -> C5 = 72, voice tickMs = 0 * 100 = 0
        // v1 at oct 4 -> C4 = 60, voice tickMs = 4 * 100 = 400
        // v2 at oct 3 -> C3 = 48, voice tickMs = 8 * 100 = 800
        assertNote(tracks.get(0).notes().get(0), 0,   72);
        assertNote(tracks.get(1).notes().get(0), 400, 60);
        assertNote(tracks.get(2).notes().get(0), 800, 48);
    }

    @Test
    void canon_roundTripsThroughMidi() {
        var voices = List.of(
                new PiSong.Voice(new TrackId("v0"), 5, 0),
                new PiSong.Voice(new TrackId("v1"), 4, 4)
        );
        var p = PiSong.buildCanon("3.14159",
                HirajoshiNote::ofIndex, HirajoshiConcretizer.inC(),
                voices, 100, 107);
        assertEquals(p, MidiCodec.fromMidi(MidiCodec.toMidi(p)));
    }

    private static void assertNote(Object n, long expectedTick, int expectedMidi) {
        var pn = (PitchedNote) n;
        assertEquals(expectedTick, pn.tickMs());
        assertEquals(expectedMidi, pn.midi());
    }

    @Test
    void piSong_roundTripsThroughMidi() {
        var p = PiSong.build("3.14159265358979",
                HirajoshiNote::ofIndex, HirajoshiConcretizer.inC(),
                4, 200, 107);
        assertEquals(p, MidiCodec.fromMidi(MidiCodec.toMidi(p)));
    }
}
