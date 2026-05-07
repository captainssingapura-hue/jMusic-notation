package music.notation.performance;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TempoConversionTest {

    @Test
    void emptyTrackUsesDefault120Bpm() {
        // 2000 ms at 120 bpm = 4 quarters
        assertEquals(4.0, TempoConversion.msToQuarters(TempoTrack.empty(), 2000), 1e-9);
        // 4 quarters at 120 bpm = 2000 ms
        assertEquals(2000L, TempoConversion.quartersToMs(TempoTrack.empty(), 4.0));
    }

    @Test
    void zeroOrNegativeIsIdentityZero() {
        assertEquals(0.0, TempoConversion.msToQuarters(TempoTrack.constant(120), 0));
        assertEquals(0L,  TempoConversion.quartersToMs(TempoTrack.constant(120), 0));
        assertEquals(0.0, TempoConversion.msToQuarters(TempoTrack.constant(120), -10));
    }

    @Test
    void constantBpmTrackMatchesEmptyConversion() {
        // A constant(120) TempoTrack should behave identically to empty
        // for any ms value (default = 120 bpm).
        TempoTrack c = TempoTrack.constant(120);
        TempoTrack e = TempoTrack.empty();
        for (long ms : new long[] {1, 100, 999, 5000, 60_000}) {
            assertEquals(
                    TempoConversion.msToQuarters(e, ms),
                    TempoConversion.msToQuarters(c, ms),
                    1e-9, "ms=" + ms);
        }
    }

    @Test
    void singleChangeAtNonZeroTickKeepsDefaultBeforeIt() {
        // Before tick 1000, bpm = 120 (default). From 1000 onward, bpm = 60.
        // msToQuarters(2000) = 1000ms*120/60000 + 1000ms*60/60000 = 2 + 1 = 3 quarters
        TempoTrack t = new TempoTrack(List.of(new TempoChange(1000, 60)));
        assertEquals(3.0, TempoConversion.msToQuarters(t, 2000), 1e-9);
    }

    @Test
    void quartersToMsIsInverseOfMsToQuarters() {
        // Round-trip across a multi-segment timeline.
        TempoTrack t = new TempoTrack(List.of(
                new TempoChange(0,    120),
                new TempoChange(2000, 60),
                new TempoChange(4000, 240)));
        for (long ms : new long[] {0, 100, 1500, 2000, 2001, 4000, 4500, 8000}) {
            double q = TempoConversion.msToQuarters(t, ms);
            long roundTrip = TempoConversion.quartersToMs(t, q);
            assertTrue(Math.abs(roundTrip - ms) <= 1, "ms=" + ms + " round=" + roundTrip);
        }
    }

    @Test
    void acceleratingTempoCompressesMs() {
        // Bar boundaries in 4/4: 4 quarters per bar.
        // Bar 1 at 60 bpm → 4000 ms; Bar 2 at 120 bpm → 2000 ms more.
        TempoTrack t = new TempoTrack(List.of(
                new TempoChange(0,    60),
                new TempoChange(4000, 120)));
        // After 4 quarters → 4000 ms
        assertEquals(4000L, TempoConversion.quartersToMs(t, 4.0));
        // After 8 quarters → 4000 + (4 quarters at 120bpm = 2000) = 6000 ms
        assertEquals(6000L, TempoConversion.quartersToMs(t, 8.0));
    }

    @Test
    void msToTicksRoundsToPpqResolution() {
        // 2000ms at 120bpm = 4 quarters; at PPQ=480 → 1920 ticks
        assertEquals(1920L, TempoConversion.msToTicks(TempoTrack.empty(), 2000, 480));
        // 1ms at 120bpm = 0.002 quarters; at PPQ=480 → ~0.96 ticks → rounds to 1
        assertEquals(1L, TempoConversion.msToTicks(TempoTrack.empty(), 1, 480));
    }
}
