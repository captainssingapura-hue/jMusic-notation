package music.notation.performance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Coverage for {@link ChannelAddr} — Phase-1 record introduced for
 * the multi-synth fan-out's Phase-2 channel allocator.
 */
class ChannelAddrTest {

    @Test
    void onSynthZeroIsConvenient() {
        ChannelAddr addr = ChannelAddr.onSynthZero(7);
        assertEquals(0, addr.synth());
        assertEquals(7, addr.channel());
    }

    @Test
    void allowsAllValidChannels() {
        for (int ch = 0; ch <= 15; ch++) {
            ChannelAddr addr = new ChannelAddr(0, ch);
            assertEquals(ch, addr.channel());
        }
    }

    @Test
    void allowsHighSynthIndex() {
        // No upper bound on synth index — Phase 2's cap is enforced by
        // the allocator, not by the record itself.
        ChannelAddr addr = new ChannelAddr(7, 0);
        assertEquals(7, addr.synth());
    }

    @Test
    void rejectsNegativeSynth() {
        assertThrows(IllegalArgumentException.class,
                () -> new ChannelAddr(-1, 0));
    }

    @Test
    void rejectsNegativeChannel() {
        assertThrows(IllegalArgumentException.class,
                () -> new ChannelAddr(0, -1));
    }

    @Test
    void rejectsChannelAbove15() {
        assertThrows(IllegalArgumentException.class,
                () -> new ChannelAddr(0, 16));
        assertThrows(IllegalArgumentException.class,
                () -> new ChannelAddr(0, 99));
    }

    @Test
    void recordEqualityWorks() {
        ChannelAddr a = new ChannelAddr(1, 4);
        ChannelAddr b = new ChannelAddr(1, 4);
        ChannelAddr c = new ChannelAddr(1, 5);
        ChannelAddr d = new ChannelAddr(2, 4);
        assertEquals(a, b);
        assertNotEquals(a, c);
        assertNotEquals(a, d);
    }

    @Test
    void drumChannelIsPermitted() {
        // Channel 9 is the GM rhythm channel by convention; the record
        // doesn't enforce that it's only used for drum tracks — that's
        // the allocator's contract, not the record's.
        ChannelAddr drumOnPrimary = new ChannelAddr(0, 9);
        ChannelAddr drumOnAuto = new ChannelAddr(1, 9);
        assertEquals(9, drumOnPrimary.channel());
        assertEquals(9, drumOnAuto.channel());
    }
}
