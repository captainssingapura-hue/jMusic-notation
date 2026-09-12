package music.notation.performance;

import music.notation.duration.BaseValue;
import music.notation.duration.Duration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the central invariant of the ms→Duration migration: the
 * {@link TimeMapper} integrates a piecewise-constant
 * {@link TempoTrack} correctly so that variable BPM works.
 *
 * <p>Worked example matches the architecture doc
 * ({@code .docs/architecture/ms-to-duration-migration.html}):</p>
 *
 * <pre>TempoTrack:
 *   TempoChange(at=0,   bpm=120)
 *   TempoChange(at=1/2, bpm=60)    // tempo halves halfway through bar 1
 *
 * Note at musical position at=3/4:
 *   Segment [0, 1/2):  120 bpm. 2 quarters × 500 ms = 1000 ms
 *   Segment [1/2, 3/4): 60 bpm. 1 quarter  × 1000 ms = 1000 ms
 *   → Wall-clock onset at 2000 ms.</pre>
 */
class TimeMapperTest {

    @Test
    void emptyTrack_usesDefaultBpm() {
        TimeMapper m = new TimeMapper(TempoTrack.empty(), 120);
        // A quarter at 120 bpm = 500 ms.
        assertEquals(500L, m.toMs(Duration.of(BaseValue.QUARTER)));
        // A whole at 120 bpm = 2000 ms.
        assertEquals(2000L, m.toMs(Duration.of(BaseValue.WHOLE)));
        assertEquals(0L, m.toMs(Duration.zero()));
    }

    @Test
    void constantBpm_60_doublesEverything() {
        // 60 bpm → 1000 ms per quarter.
        TimeMapper m = new TimeMapper(TempoTrack.constant(60));
        assertEquals(1000L, m.toMs(Duration.of(BaseValue.QUARTER)));
        assertEquals(4000L, m.toMs(Duration.of(BaseValue.WHOLE)));
    }

    @Test
    void workedExample_tempoHalvesAtHalf_noteAt3of4Lands2000ms() {
        // The doc example, pinned literally.
        TempoTrack tempo = new TempoTrack(List.of(
                new TempoChange(Duration.zero(),       120),
                new TempoChange(Duration.of(1, 2),     60)));
        TimeMapper m = new TimeMapper(tempo);

        assertEquals(0L,    m.toMs(Duration.zero()),
                "start of piece");
        assertEquals(1000L, m.toMs(Duration.of(1, 2)),
                "first half at 120 bpm = 1000 ms");
        assertEquals(2000L, m.toMs(Duration.of(3, 4)),
                "doc worked example: 1000 (first half @120) + 1000 (next quarter @60)");
        assertEquals(3000L, m.toMs(Duration.of(BaseValue.WHOLE)),
                "full whole note: 1000 + 2000");
    }

    @Test
    void tempoChangeMidNote_endPositionAccountsForBothSegments() {
        // A whole note at position 0; tempo doubles at 1/4.
        // Segment [0, 1/4)  at 60 bpm  → 1 quarter × 1000 ms = 1000 ms
        // Segment [1/4, 1)  at 120 bpm → 3 quarters × 500 ms = 1500 ms
        // → Note ends at 2500 ms.
        TempoTrack tempo = new TempoTrack(List.of(
                new TempoChange(Duration.zero(),    60),
                new TempoChange(Duration.of(1, 4), 120)));
        TimeMapper m = new TimeMapper(tempo);

        assertEquals(0L,    m.toMs(Duration.zero()));
        assertEquals(1000L, m.toMs(Duration.of(1, 4)));
        assertEquals(2500L, m.toMs(Duration.of(BaseValue.WHOLE)));
    }

    @Test
    void msBetween_isDifferenceOfEndpoints() {
        TempoTrack tempo = new TempoTrack(List.of(
                new TempoChange(Duration.zero(),       120),
                new TempoChange(Duration.of(1, 2),     60)));
        TimeMapper m = new TimeMapper(tempo);

        // From quarter to whole: 0..1000 + 0..3000 → 3000 - 500 = 2500
        long direct = m.msBetween(Duration.of(1, 4), Duration.of(1, 1));
        long byEndpoints = m.toMs(Duration.of(1, 1)) - m.toMs(Duration.of(1, 4));
        assertEquals(byEndpoints, direct);
    }

    @Test
    void toDuration_inverseOnTempoBoundaries() {
        // Round-trip through ms and back lands on the same Duration
        // when the query is exactly on a tempo-segment boundary.
        TempoTrack tempo = new TempoTrack(List.of(
                new TempoChange(Duration.zero(),       120),
                new TempoChange(Duration.of(1, 2),     60)));
        TimeMapper m = new TimeMapper(tempo);

        for (Duration probe : List.of(
                Duration.zero(),
                Duration.of(1, 4),
                Duration.of(1, 2),
                Duration.of(3, 4),
                Duration.of(1, 1))) {
            long ms = m.toMs(probe);
            Duration back = m.toDuration(ms);
            assertTrue(back.equalsDuration(probe),
                    "round-trip at " + probe + " → " + ms + " ms → " + back);
        }
    }

    @Test
    void toDuration_prorataMidSegment() {
        // 60 bpm constant, query halfway through the first quarter (500 ms).
        // Should land at 1/8 (half of a quarter = half of 1/4 = 1/8).
        TimeMapper m = new TimeMapper(TempoTrack.constant(60));
        Duration d = m.toDuration(500);
        assertTrue(d.equalsDuration(Duration.of(1, 8)),
                "500 ms at 60 bpm should be 1/8 whole; got " + d);
    }

    @Test
    void tripletPosition_integratesExactly() {
        // A triplet 8th at 120 bpm. Quarter = 500 ms; triplet-8th = quarter/3 ≈ 166.667 ms.
        // Position = 1/12 (one triplet 8th).
        TimeMapper m = new TimeMapper(TempoTrack.constant(120));
        long ms = m.toMs(Duration.of(1, 12));
        // Exact value would be 166.666… ms; we Math.round once at the end so 167 is fine.
        assertTrue(ms == 166 || ms == 167,
                "triplet 8th at 120 bpm should round to 166 or 167 ms; got " + ms);
    }

    @Test
    void rejectsBadDefaultBpm() {
        assertThrows(IllegalArgumentException.class,
                () -> new TimeMapper(TempoTrack.empty(), 0));
        assertThrows(IllegalArgumentException.class,
                () -> new TimeMapper(TempoTrack.empty(), 1000));
    }
}
