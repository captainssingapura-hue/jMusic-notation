package music.notation.performance;

import music.notation.duration.Duration;
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

    /** {@code n} quarter notes. */
    private static Duration q(long n) { return Duration.of(n, 4); }

    /** A negative musical position (invalid anchor). */
    private static final Duration NEG = Duration.of(-1, 4);

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
                () -> new TimeSignatureChange(NEG, FOUR_FOUR));
    }

    @Test
    void timeSigChange_rejectsNullSig() {
        assertThrows(NullPointerException.class,
                () -> new TimeSignatureChange(q(0), null));
    }

    @Test
    void timeSigTrack_empty() {
        assertTrue(TimeSignatureTrack.empty().changes().isEmpty());
    }

    @Test
    void timeSigTrack_constant() {
        var t = TimeSignatureTrack.constant(FOUR_FOUR);
        assertEquals(1, t.changes().size());
        assertTrue(t.changes().get(0).at().isZero());
        assertEquals(FOUR_FOUR, t.changes().get(0).timeSig());
    }

    @Test
    void timeSigTrack_sortsByPosition() {
        var t = new TimeSignatureTrack(List.of(
                new TimeSignatureChange(q(4), SIX_EIGHT),
                new TimeSignatureChange(q(0), FOUR_FOUR),
                new TimeSignatureChange(q(2), THREE_FOUR)));
        assertEquals(3, t.changes().size());
        assertTrue(q(0).equalsDuration(t.changes().get(0).at()));
        assertTrue(q(2).equalsDuration(t.changes().get(1).at()));
        assertTrue(q(4).equalsDuration(t.changes().get(2).at()));
    }

    @Test
    void timeSigTrack_dropsAdjacentDuplicates() {
        // Two consecutive 4/4 entries collapse to one — matches TempoTrack.
        var t = new TimeSignatureTrack(List.of(
                new TimeSignatureChange(q(0), FOUR_FOUR),
                new TimeSignatureChange(q(1), FOUR_FOUR),
                new TimeSignatureChange(q(2), THREE_FOUR)));
        assertEquals(2, t.changes().size());
        assertEquals(FOUR_FOUR,  t.changes().get(0).timeSig());
        assertEquals(THREE_FOUR, t.changes().get(1).timeSig());
    }

    // ── KeySignatureChange / KeySignatureTrack ─────────────────────────

    @Test
    void keySigChange_rejectsNegativeTick() {
        assertThrows(IllegalArgumentException.class,
                () -> new KeySignatureChange(NEG, C_MAJOR));
    }

    @Test
    void keySigChange_rejectsNullKey() {
        assertThrows(NullPointerException.class,
                () -> new KeySignatureChange(q(0), null));
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
                new KeySignatureChange(q(6), D_MAJOR),
                new KeySignatureChange(q(0), C_MAJOR),
                new KeySignatureChange(q(2), C_MAJOR),     // dup of preceding
                new KeySignatureChange(q(4), G_MAJOR)));
        assertEquals(3, t.changes().size());
        assertEquals(C_MAJOR, t.changes().get(0).key());
        assertEquals(G_MAJOR, t.changes().get(1).key());
        assertEquals(D_MAJOR, t.changes().get(2).key());
    }
}
