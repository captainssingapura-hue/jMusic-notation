package music.notation.phrase;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Targeted checks for {@link Monophony#extractTopLine}. Uses a tiny
 * test-only record so we don't pull a notation-performance dep into
 * notation-core just to write the tests.
 */
class MonophonyTopLineTest {

    /** Test fixture: (tickMs, durationMs, midi). */
    record TN(long tickMs, long durationMs, int midi) {
        long endMs() { return tickMs + durationMs; }
    }

    private static Monophony.TopLine<TN> run(List<TN> notes) {
        return Monophony.extractTopLine(notes, TN::midi, TN::tickMs, TN::endMs);
    }

    @Test
    void alreadyMonophonic_keepsEveryNote() {
        var notes = List.of(
                new TN(   0, 500, 60),
                new TN( 500, 500, 62),
                new TN(1000, 500, 64));
        var top = run(notes);
        assertEquals(3, top.melody().size());
        assertEquals(0, top.dropped());
    }

    @Test
    void chordOnSingleOnset_picksHighestPitch() {
        // C-E-G chord all at tick 0 → only G (67) is kept.
        var notes = List.of(
                new TN(0, 500, 60),    // C
                new TN(0, 500, 64),    // E
                new TN(0, 500, 67));   // G
        var top = run(notes);
        assertEquals(1, top.melody().size());
        assertEquals(67, top.melody().get(0).midi());
        assertEquals(2, top.dropped());
    }

    @Test
    void nearCoincidentChord_groupedWithinTolerance() {
        // 1ms apart → same chord. Tolerance is 25ms.
        var notes = List.of(
                new TN(0,    500, 60),
                new TN(1,    500, 67));
        var top = run(notes);
        assertEquals(1, top.melody().size());
        assertEquals(67, top.melody().get(0).midi());
    }

    @Test
    void test1Vocal_doubleTriggerArtifact_dropsLowerNote() {
        // Exactly the shape from R:\Music_works\MIDI_Import\test1 —
        // a vocal converter emitted a 1ms-apart pair where the lower
        // one is also shorter. Top-line extraction must keep only the
        // higher / longer note as the sung pitch.
        var notes = List.of(
                new TN(2542, 372, 58),     // the "real" sung note
                new TN(2543,  90, 56));    // double-trigger artifact
        var top = run(notes);
        assertEquals(1, top.melody().size());
        assertEquals(58, top.melody().get(0).midi());
        assertEquals(1, top.dropped());
    }

    @Test
    void innerVoice_underHeldHighNote_isDropped() {
        // A held high G (4 sec) over a moving C-E-G line in the inner voice.
        // Only the held G is the top-line.
        var notes = List.of(
                new TN(   0, 4000, 79),    // held G5
                new TN(   0,  500, 60),    // inner C
                new TN( 500,  500, 64),    // inner E
                new TN(1000,  500, 67));   // inner G
        var top = run(notes);
        // All three inner notes are dropped — the high G is the top
        // throughout, and the inner onsets are below it.
        // Note: tick 0 is a chord-group with G5 winning → drops 1.
        // Ticks 500 / 1000 are below the still-sounding G5 → drop 2 more.
        assertEquals(1, top.melody().size());
        assertEquals(79, top.melody().get(0).midi());
        assertEquals(3, top.dropped());
    }

    @Test
    void bassThenMelody_bassKeptUntilMelodyStarts() {
        // Bass note plays alone for 500ms, then a higher melody starts
        // before the bass ends. Both should be kept — the bass is the
        // top while it's alone; the melody is the top once it starts.
        var notes = List.of(
                new TN(   0, 2000, 48),    // bass, lasts 2 seconds
                new TN( 500,  500, 72),    // higher melody mid-bass
                new TN(1000,  500, 74));   // higher melody continues
        var top = run(notes);
        assertEquals(3, top.melody().size());
        assertEquals(48, top.melody().get(0).midi());
        assertEquals(72, top.melody().get(1).midi());
        assertEquals(74, top.melody().get(2).midi());
        assertEquals(0, top.dropped());
    }

    @Test
    void newLowNoteAfterHighOneEnds_isKept() {
        // High C plays 0–500, then a lower G starts at 600 alone.
        // The G is the new top-line (nothing else sounding).
        var notes = List.of(
                new TN(   0,  500, 72),
                new TN( 600,  500, 67));
        var top = run(notes);
        assertEquals(2, top.melody().size());
        assertEquals(67, top.melody().get(1).midi());
        assertEquals(0, top.dropped());
    }

    @Test
    void emptyInput_returnsEmpty() {
        var top = run(List.of());
        assertEquals(0, top.melody().size());
        assertEquals(0, top.dropped());
    }

    @Test
    void resultIsSortedByTick_regardlessOfInputOrder() {
        var notes = List.of(
                new TN(1000, 500, 64),
                new TN(   0, 500, 60),
                new TN( 500, 500, 62));
        var top = run(notes);
        assertEquals(3, top.melody().size());
        assertEquals(0,    top.melody().get(0).tickMs());
        assertEquals(500,  top.melody().get(1).tickMs());
        assertEquals(1000, top.melody().get(2).tickMs());
    }
}
