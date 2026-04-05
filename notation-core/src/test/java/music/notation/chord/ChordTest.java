package music.notation.chord;

import music.notation.pitch.Accidental;
import music.notation.pitch.NoteName;
import music.notation.pitch.Pitch;
import music.notation.pitch.StaffPitch;
import org.junit.jupiter.api.Test;

import java.util.List;

import static music.notation.pitch.Accidental.*;
import static music.notation.pitch.NoteName.*;
import static org.junit.jupiter.api.Assertions.*;

class ChordTest {

    // ── Triads ──────────────────────────────────────────────────────

    @Test
    void cMajorTriad() {
        final var chord = new MajorTriad(C, 4);
        assertPitches(chord.pitches(),
                pitch(C, NATURAL, 4), pitch(E, NATURAL, 4), pitch(G, NATURAL, 4));
    }

    @Test
    void dMajorTriad() {
        final var chord = new MajorTriad(D, 3);
        assertPitches(chord.pitches(),
                pitch(D, NATURAL, 3), pitch(F, SHARP, 3), pitch(A, NATURAL, 3));
    }

    @Test
    void ebMajorTriad() {
        final var chord = new MajorTriad(E, FLAT, 3);
        assertPitches(chord.pitches(),
                pitch(E, FLAT, 3), pitch(G, NATURAL, 3), pitch(B, FLAT, 3));
    }

    @Test
    void aMinorTriad() {
        final var chord = new MinorTriad(A, 3);
        assertPitches(chord.pitches(),
                pitch(A, NATURAL, 3), pitch(C, NATURAL, 4), pitch(E, NATURAL, 4));
    }

    @Test
    void dMinorTriad() {
        final var chord = new MinorTriad(D, 3);
        assertPitches(chord.pitches(),
                pitch(D, NATURAL, 3), pitch(F, NATURAL, 3), pitch(A, NATURAL, 3));
    }

    @Test
    void gMinorTriad() {
        final var chord = new MinorTriad(G, 3);
        assertPitches(chord.pitches(),
                pitch(G, NATURAL, 3), pitch(B, FLAT, 3), pitch(D, NATURAL, 4));
    }

    @Test
    void bDiminishedTriad() {
        final var chord = new DiminishedTriad(B, 3);
        assertPitches(chord.pitches(),
                pitch(B, NATURAL, 3), pitch(D, NATURAL, 4), pitch(F, NATURAL, 4));
    }

    @Test
    void cAugmentedTriad() {
        final var chord = new AugmentedTriad(C, 4);
        assertPitches(chord.pitches(),
                pitch(C, NATURAL, 4), pitch(E, NATURAL, 4), pitch(G, SHARP, 4));
    }

    // ── Sevenths ────────────────────────────────────────────────────

    @Test
    void g7DominantSeventh() {
        final var chord = new DominantSeventh(G, 3);
        assertPitches(chord.pitches(),
                pitch(G, NATURAL, 3), pitch(B, NATURAL, 3),
                pitch(D, NATURAL, 4), pitch(F, NATURAL, 4));
    }

    @Test
    void cMajorSeventh() {
        final var chord = new MajorSeventh(C, 3);
        assertPitches(chord.pitches(),
                pitch(C, NATURAL, 3), pitch(E, NATURAL, 3),
                pitch(G, NATURAL, 3), pitch(B, NATURAL, 3));
    }

    @Test
    void aMinorSeventh() {
        final var chord = new MinorSeventh(A, 3);
        assertPitches(chord.pitches(),
                pitch(A, NATURAL, 3), pitch(C, NATURAL, 4),
                pitch(E, NATURAL, 4), pitch(G, NATURAL, 4));
    }

    // ── Accidental root ─────────────────────────────────────────────

    @Test
    void bbMajorTriad() {
        final var chord = new MajorTriad(B, FLAT, 3);
        assertPitches(chord.pitches(),
                pitch(B, FLAT, 3), pitch(D, NATURAL, 4), pitch(F, NATURAL, 4));
    }

    @Test
    void fSharpMinorTriad() {
        final var chord = new MinorTriad(F, SHARP, 3);
        assertPitches(chord.pitches(),
                pitch(F, SHARP, 3), pitch(A, NATURAL, 3), pitch(C, SHARP, 4));
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private static StaffPitch pitch(NoteName name, Accidental acc, int octave) {
        return StaffPitch.of(name, acc, octave);
    }

    private static void assertPitches(List<Pitch> actual, Pitch... expected) {
        assertEquals(List.of(expected), actual);
    }
}
