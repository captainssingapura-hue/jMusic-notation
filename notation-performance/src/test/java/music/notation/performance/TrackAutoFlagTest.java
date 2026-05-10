package music.notation.performance;

import music.notation.expressivity.TrackId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Coverage for the {@code auto} bit on {@link Track} added in Phase 1.3
 * of the multi-synth fan-out work. Phase 2's channel allocator reads
 * this bit to route auto-X tracks (auto-drum, future auto-bass /
 * auto-harmony) onto a dedicated <em>AUTO</em> synth so source drums
 * and auto-drum can coexist on different ch-9 assignments.
 *
 * <p>This test pins down the bit semantics so the Phase-2 allocator
 * has a stable contract to read.</p>
 */
class TrackAutoFlagTest {

    private static final TrackId ID = new TrackId("T");

    @Test
    void backwardCompatConstructorDefaultsToFalse() {
        Track t = new Track(ID, TrackKind.PITCHED, List.of());
        assertFalse(t.auto(),
                "the 3-arg back-compat constructor must default auto to false "
                        + "so source-parsed tracks aren't accidentally routed "
                        + "to the AUTO synth");
    }

    @Test
    void canonicalConstructorRespectsExplicitAutoBit() {
        Track src = new Track(ID, TrackKind.DRUM, List.of(), false);
        Track aut = new Track(ID, TrackKind.DRUM, List.of(), true);
        assertFalse(src.auto());
        assertTrue(aut.auto());
    }

    @Test
    void emptyFactorySetsAutoFalse() {
        assertFalse(Track.empty(ID, TrackKind.PITCHED).auto());
        assertFalse(Track.empty(ID, TrackKind.DRUM).auto());
    }

    @Test
    void withAutoFlipsTheBitAndReturnsACopy() {
        Track src = new Track(ID, TrackKind.PITCHED, List.of());
        Track auto = src.withAuto(true);

        assertNotSame(src, auto, "withAuto must return a new instance");
        assertFalse(src.auto(), "source must be untouched");
        assertTrue(auto.auto());
        assertEquals(src.id(), auto.id());
        assertEquals(src.kind(), auto.kind());
        assertEquals(src.notes(), auto.notes());
    }

    @Test
    void recordEqualityDistinguishesAutoBit() {
        // Same id/kind/notes but different auto bit → not equal. Phase 2's
        // routing depends on the codec being able to tell two otherwise-
        // identical track shapes apart by provenance.
        Track src = new Track(ID, TrackKind.PITCHED, List.of(), false);
        Track aut = new Track(ID, TrackKind.PITCHED, List.of(), true);
        assertNotEquals(src, aut);
        assertNotEquals(src.hashCode(), aut.hashCode(),
                "hash must differ for the field to be Phase-2-routable");
    }
}
