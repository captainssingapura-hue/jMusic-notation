package music.notation.transcribe;

import music.notation.pitch.Accidental;
import music.notation.pitch.NoteName;
import music.notation.structure.KeySignature;
import music.notation.structure.Mode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KeysTest {

    @Test
    void parseNameAndSignature() {
        assertEquals(new KeySignature(NoteName.B, Accidental.FLAT, Mode.MAJOR), Keys.parse("Bb major"));
        assertEquals(new KeySignature(NoteName.E, Accidental.FLAT, Mode.MINOR), Keys.parse("e♭ minor"));
        assertEquals(new KeySignature(NoteName.F, Accidental.SHARP, Mode.MAJOR), Keys.parse("F#"));
        assertEquals("Bb major", Keys.name(Keys.parse("bb maj")));
        assertEquals(-2, Keys.fifths(Keys.parse("Bb major")));
        assertEquals(1, Keys.fifths(Keys.parse("E minor")));
        assertEquals(6, Keys.fifths(Keys.parse("F# major")));
        assertEquals(-6, Keys.fifths(Keys.parse("Gb major")));
        assertEquals(-6, Keys.fifths(Keys.parse("Eb minor")));
        assertEquals(-4, Keys.fifths(Keys.parse("F minor")));
        assertEquals(0, Keys.fifths(Keys.parse("A minor")));
        assertThrows(IllegalArgumentException.class, () -> Keys.parse("H major"));
        assertThrows(IllegalArgumentException.class, () -> Keys.parse("C lydianish"));
    }

    @Test
    void spellingFollowsTheKey() {
        assertEquals("Bb4", Keys.noteName(70, Keys.parse("Bb major")));
        assertEquals("A#4", Keys.noteName(70, Keys.parse("E minor")));
        assertEquals("C4", Keys.noteName(60, null));
        assertEquals("Eb minor", Keys.name(Keys.of(3, Mode.MINOR)));
        assertEquals("Ab major", Keys.name(Keys.of(8, Mode.MAJOR)));
    }

    private static List<TranscribedNote> line(int[] midis, double[] beats) {
        var out = new ArrayList<TranscribedNote>();
        double t = 0;
        for (int i = 0; i < midis.length; i++) {
            out.add(new TranscribedNote(t, t + beats[i] * 500, midis[i], 0, 0.9, 0.2));
            t += beats[i] * 500;
        }
        return out;
    }

    @Test
    void keyFinding() {
        // Twinkle in G.
        var twinkle = line(new int[]{67, 67, 74, 74, 76, 76, 74, 72, 72, 71, 71, 69, 69, 67},
                new double[]{1, 1, 1, 1, 1, 1, 2, 1, 1, 1, 1, 1, 1, 2});
        assertEquals("G major", Keys.name(KeyEstimator.estimate(twinkle)));
        // E♭ major scale, up and back, with long tonics.
        var eb = line(new int[]{63, 65, 67, 68, 70, 72, 74, 75, 70, 67, 63},
                new double[]{2, 1, 1, 1, 2, 1, 1, 2, 1, 1, 3});
        assertEquals("Eb major", Keys.name(KeyEstimator.estimate(eb)));
        // D harmonic minor, leaning on D and A, with the raised C♯.
        var dm = line(new int[]{62, 65, 69, 70, 69, 61, 62, 64, 65, 64, 62},
                new double[]{2, 1, 2, 1, 1, 1, 2, 1, 1, 1, 3});
        assertEquals("D minor", Keys.name(KeyEstimator.estimate(dm)));
        assertEquals("C major", Keys.name(KeyEstimator.estimate(List.of())));
    }
}
