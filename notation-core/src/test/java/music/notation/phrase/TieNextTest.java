package music.notation.phrase;

import music.notation.event.Dynamic;
import music.notation.structure.KeySignature;
import music.notation.structure.Mode;
import music.notation.structure.TimeSignature;
import org.junit.jupiter.api.Test;

import static music.notation.duration.BaseValue.*;
import static music.notation.pitch.NoteName.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@code tieNext()}: same-pitch ties merged at phrase construction.
 */
class TieNextTest {

    private static final KeySignature KEY = new KeySignature(C, Mode.MAJOR);
    private static final TimeSignature TS = new TimeSignature(4, 4);

    private StaffPhraseBuilder b() {
        return StaffPhraseBuilder.in(KEY, TS, QUARTER);
    }

    // ── Success: basic 2-note tie ────────────────────────────────────

    @Test
    void tieNextMergesTwoSamePitchNotes() {
        var phrase = b()
                .bar().o4(QUARTER, F).tieNext().o4(QUARTER, F).o4(HALF, G)
                .build(new PhraseMarking(PhraseConnection.ATTACCA, true));

        // 3 original notes → 2 after merge (F+F sustained, then G)
        long noteCount = phrase.nodes().stream().filter(n -> n instanceof NoteNode).count();
        assertEquals(2, noteCount);

        var first = (NoteNode) phrase.nodes().stream().filter(n -> n instanceof NoteNode).findFirst().get();
        assertEquals(QUARTER.sixtyFourths() + QUARTER.sixtyFourths(),
                first.duration().sixtyFourths(),
                "merged note should have combined duration");
        assertFalse(first.hasTie(), "merged note's tie flag should be cleared");
    }

    // ── Success: chained tie across three notes ──────────────────────

    @Test
    void tieNextChainsAcrossThreeNotes() {
        var phrase = b()
                .bar().o4(QUARTER, F).tieNext().o4(QUARTER, F).tieNext().o4(HALF, F)
                .build(new PhraseMarking(PhraseConnection.ATTACCA, true));

        long noteCount = phrase.nodes().stream().filter(n -> n instanceof NoteNode).count();
        assertEquals(1, noteCount, "three tied notes should collapse to one");

        var merged = (NoteNode) phrase.nodes().stream().filter(n -> n instanceof NoteNode).findFirst().get();
        assertEquals(QUARTER.sixtyFourths() * 2 + HALF.sixtyFourths(),
                merged.duration().sixtyFourths());
    }

    // ── Success: tie preserves first note's decorations ──────────────

    @Test
    void tieNextPreservesFirstNoteOrnament() {
        var phrase = b()
                .bar().o4(F, QUARTER, music.notation.event.Ornament.TRILL)
                      .tieNext().o4(HALF.dot(), F)
                .build(new PhraseMarking(PhraseConnection.ATTACCA, true));

        var merged = (NoteNode) phrase.nodes().stream().filter(n -> n instanceof NoteNode).findFirst().get();
        assertTrue(merged.ornament().isPresent(), "ornament from first note must survive the tie");
        assertEquals(music.notation.event.Ornament.TRILL, merged.ornament().get());
    }

    // ── Success: zero-duration markers between tied notes don't break it ──

    @Test
    void tieNextPassesThroughDynamicMarkers() {
        var phrase = b()
                .bar().o4(QUARTER, F).tieNext().mf().o4(HALF.dot(), F)
                .build(new PhraseMarking(PhraseConnection.ATTACCA, true));

        // Expect: merged NoteNode, then DynamicNode preserved after it.
        var nodes = phrase.nodes();
        long noteCount = nodes.stream().filter(n -> n instanceof NoteNode).count();
        assertEquals(1, noteCount);
        assertTrue(nodes.stream().anyMatch(n -> n instanceof DynamicNode d && d.dynamic() == Dynamic.MF),
                "DynamicNode between the tied pair should be preserved");
    }

    // ── Success: chord tie (all pitches match) ───────────────────────

    @Test
    void tieNextMergesChordsWithIdenticalPitches() {
        var phrase = b()
                .bar().o4(QUARTER, C, E, G).tieNext().o4(HALF.dot(), C, E, G)
                .build(new PhraseMarking(PhraseConnection.ATTACCA, true));

        long noteCount = phrase.nodes().stream().filter(n -> n instanceof NoteNode).count();
        assertEquals(1, noteCount);
        var merged = (NoteNode) phrase.nodes().stream().filter(n -> n instanceof NoteNode).findFirst().get();
        assertEquals(3, merged.pitches().size());
    }

    // ── Failure: chord tie with mismatched pitches ───────────────────

    @Test
    void tieNextRejectsChordPitchMismatch() {
        var ex = assertThrows(IllegalStateException.class, () ->
                b().bar().o4(QUARTER, C, E, G).tieNext().o4(HALF.dot(), C, E, A)
                        .build(new PhraseMarking(PhraseConnection.ATTACCA, true)));
        assertTrue(ex.getMessage().contains("pitch mismatch"));
        // Both chord lists should appear in the message.
        assertTrue(ex.getMessage().contains("G"));
        assertTrue(ex.getMessage().contains("A"));
    }

    // ── Failure: single-pitch mismatch ───────────────────────────────

    @Test
    void tieNextRejectsPitchMismatch() {
        var ex = assertThrows(IllegalStateException.class, () ->
                b().bar().o4(QUARTER, F).tieNext().o4(HALF.dot(), G)
                        .build(new PhraseMarking(PhraseConnection.ATTACCA, true)));
        assertTrue(ex.getMessage().contains("pitch mismatch"));
    }

    // ── Failure: tieNext() with no preceding note ────────────────────

    @Test
    void tieNextRejectsCallOnEmptyBar() {
        var ex = assertThrows(IllegalStateException.class, () ->
                b().bar().tieNext());
        assertTrue(ex.getMessage().contains("preceding note"));
    }

    // ── Failure: tieNext() after a rest (no preceding note) ──────────

    @Test
    void tieNextRejectsCallAfterRest() {
        var ex = assertThrows(IllegalStateException.class, () ->
                b().bar().r(QUARTER).tieNext());
        assertTrue(ex.getMessage().contains("preceding note"));
    }

    // ── Failure: trailing tieNext() (no successor in phrase) ─────────

    @Test
    void tieNextRejectsDanglingTieAtPhraseEnd() {
        var ex = assertThrows(IllegalStateException.class, () ->
                b().bar().o4(QUARTER, F).o4(QUARTER, G).o4(QUARTER, A).o4(QUARTER, B).tieNext()
                        .build(new PhraseMarking(PhraseConnection.ATTACCA, true)));
        assertTrue(ex.getMessage().contains("no following note"));
    }
}
