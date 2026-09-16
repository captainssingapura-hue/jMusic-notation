package music.notation.performance;

import music.notation.pitch.Accidental;
import music.notation.pitch.NoteName;
import music.notation.structure.KeySignature;
import music.notation.structure.Mode;
import music.notation.structure.TimeSignature;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Invariants for the new {@link TimeSignatureTrack} and
 * {@link KeySignatureTrack} side-channels — canonical sort, adjacent
 * dedup, factory shortcuts.
 */
class SignatureTrackTest {

    private static final TimeSignature FOUR_FOUR = new TimeSignature(4, 4);
    private static final TimeSignature THREE_FOUR = new TimeSignature(3, 4);
    private static final TimeSignature SIX_EIGHT = new TimeSignature(6, 8);

    private static final KeySignature C_MAJOR =
            new KeySignature(NoteName.C, Accidental.NATURAL, Mode.MAJOR);
    private static final KeySignature G_MAJOR =
            new KeySignature(NoteName.G, Accidental.NATURAL, Mode.MAJOR);
    private static final KeySignature D_MAJOR =
            new KeySignature(NoteName.D, Accidental.NATURAL, Mode.MAJOR);

    // ── TimeSignatureChange / TimeSignatureTrack ───────────────────────

    @Test
    void timeSigChange_rejectsNegativeTick() {
        assertThrows(IllegalArgumentException.class,
                () -> new TimeSignatureChange(-1, FOUR_FOUR));
    }

    @Test
    void timeSigChange_rejectsNullSig() {
        assertThrows(NullPointerException.class,
                () -> new TimeSignatureChange(0, null));
    }

    @Test
    void timeSigTrack_empty() {
        assertTrue(TimeSignatureTrack.empty().changes().isEmpty());
    }

    @Test
    void timeSigTrack_constant() {
        var t = TimeSignatureTrack.constant(FOUR_FOUR);
        assertEquals(1, t.changes().size());
        assertEquals(0L, t.changes().get(0).tickMs());
        assertEquals(FOUR_FOUR, t.changes().get(0).timeSig());
    }

    @Test
    void timeSigTrack_sortsByTick() {
        var t = new TimeSignatureTrack(List.of(
                new TimeSignatureChange(2000, SIX_EIGHT),
                new TimeSignatureChange(0,    FOUR_FOUR),
                new TimeSignatureChange(1000, THREE_FOUR)));
        assertEquals(3, t.changes().size());
        assertEquals(0L,    t.changes().get(0).tickMs());
        assertEquals(1000L, t.changes().get(1).tickMs());
        assertEquals(2000L, t.changes().get(2).tickMs());
    }

    @Test
    void timeSigTrack_dropsAdjacentDuplicates() {
        // Two consecutive 4/4 entries collapse to one — matches TempoTrack.
        var t = new TimeSignatureTrack(List.of(
                new TimeSignatureChange(0,    FOUR_FOUR),
                new TimeSignatureChange(500,  FOUR_FOUR),
                new TimeSignatureChange(1000, THREE_FOUR)));
        assertEquals(2, t.changes().size());
        assertEquals(FOUR_FOUR,  t.changes().get(0).timeSig());
        assertEquals(THREE_FOUR, t.changes().get(1).timeSig());
    }

    // ── KeySignatureChange / KeySignatureTrack ─────────────────────────

    @Test
    void keySigChange_rejectsNegativeTick() {
        assertThrows(IllegalArgumentException.class,
                () -> new KeySignatureChange(-1, C_MAJOR));
    }

    @Test
    void keySigChange_rejectsNullKey() {
        assertThrows(NullPointerException.class,
                () -> new KeySignatureChange(0, null));
    }

    @Test
    void keySigTrack_constantAndEmpty() {
        assertTrue(KeySignatureTrack.empty().changes().isEmpty());

        var t = KeySignatureTrack.constant(C_MAJOR);
        assertEquals(1, t.changes().size());
        assertEquals(C_MAJOR, t.changes().get(0).key());
    }

    @Test
    void keySigTrack_sortAndDedup() {
        var t = new KeySignatureTrack(List.of(
                new KeySignatureChange(3000, D_MAJOR),
                new KeySignatureChange(0,    C_MAJOR),
                new KeySignatureChange(1000, C_MAJOR),     // dup of preceding
                new KeySignatureChange(2000, G_MAJOR)));
        assertEquals(3, t.changes().size());
        assertEquals(C_MAJOR, t.changes().get(0).key());
        assertEquals(G_MAJOR, t.changes().get(1).key());
        assertEquals(D_MAJOR, t.changes().get(2).key());
    }
}
