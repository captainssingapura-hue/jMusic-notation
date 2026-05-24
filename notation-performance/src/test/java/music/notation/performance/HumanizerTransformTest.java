package music.notation.performance;

import music.notation.expressivity.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Coverage for {@link HumanizerTransform#apply(Performance, HumanizerTransform.Params)}.
 *
 * <p>The transform is a pure {@link Performance} → {@link Performance}
 * function — same input + same seed must produce byte-identical output,
 * and tickMs must stay non-negative even under wide-σ jitter.</p>
 */
class HumanizerTransformTest {

    /** Build a 1-track piece with N drum hits at evenly-spaced ms positions. */
    private static Performance drumPiece(int n, long stepMs) {
        List<ConcreteNote> hits = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            hits.add(new DrumNote(i * stepMs, 100L, 36));
        }
        var track = new Track(new TrackId("Drums"), TrackKind.DRUM, hits);
        return new Performance(
                new Score(List.of(track)),
                TempoTrack.constant(120),
                Instrumentation.empty(), Volume.empty(),
                Articulations.empty(), Pedaling.empty(),
                Velocities.empty());
    }

    /** A piece with one PITCHED track + one DRUM track. */
    private static Performance pitchedAndDrumPiece() {
        var pitched = new Track(
                new TrackId("Piano"), TrackKind.PITCHED,
                List.of(new PitchedNote(500L, 200L, 60),
                        new PitchedNote(1000L, 200L, 62),
                        new PitchedNote(1500L, 200L, 64)));
        var drums = new Track(
                new TrackId("Drums"), TrackKind.DRUM,
                List.of(new DrumNote(500L, 100L, 36),
                        new DrumNote(1000L, 100L, 36),
                        new DrumNote(1500L, 100L, 36)));
        return new Performance(
                new Score(List.of(pitched, drums)),
                TempoTrack.constant(120),
                Instrumentation.empty(), Volume.empty(),
                Articulations.empty(), Pedaling.empty(),
                Velocities.empty());
    }

    @Test
    void offIsIdentity() {
        Performance perf = drumPiece(10, 500);
        assertSame(perf, HumanizerTransform.apply(perf, HumanizerTransform.Params.OFF));
    }

    @Test
    void nullParamsIsIdentity() {
        Performance perf = drumPiece(10, 500);
        assertSame(perf, HumanizerTransform.apply(perf, null));
    }

    @Test
    void nullPerformanceReturnsNull() {
        assertNull(HumanizerTransform.apply(null, HumanizerTransform.Params.MEDIUM));
    }

    @Test
    void deterministicForFixedSeed() {
        Performance perf = drumPiece(20, 500);
        var params = new HumanizerTransform.Params(10, true, 42L);

        Performance a = HumanizerTransform.apply(perf, params);
        Performance b = HumanizerTransform.apply(perf, params);

        assertEquals(a, b, "same input + same seed must produce equal Performance");
    }

    @Test
    void differentSeedsProduceDifferentJitter() {
        Performance perf = drumPiece(50, 500);
        Performance a = HumanizerTransform.apply(perf,
                new HumanizerTransform.Params(10, true, 42L));
        Performance b = HumanizerTransform.apply(perf,
                new HumanizerTransform.Params(10, true, 43L));
        assertNotEquals(a, b);
    }

    @Test
    void durationsArePreserved() {
        Performance perf = drumPiece(20, 500);
        Performance after = HumanizerTransform.apply(perf,
                new HumanizerTransform.Params(20, true, 7L));

        var origNotes  = perf.score().tracks().get(0).notes();
        var afterNotes = after.score().tracks().get(0).notes();
        assertEquals(origNotes.size(), afterNotes.size());
        for (int i = 0; i < origNotes.size(); i++) {
            assertEquals(origNotes.get(i).durationMs(), afterNotes.get(i).durationMs(),
                    "note " + i + " duration must be unchanged");
        }
    }

    @Test
    void drumsOnlyLeavesPitchedTrackUntouched() {
        Performance perf = pitchedAndDrumPiece();
        Performance after = HumanizerTransform.apply(perf,
                new HumanizerTransform.Params(20, true, 7L));

        // Pitched track must be byte-identical (same Track object reused).
        assertSame(perf.score().tracks().get(0), after.score().tracks().get(0),
                "pitched track must pass through with drumsOnly=true");
        assertNotSame(perf.score().tracks().get(1), after.score().tracks().get(1),
                "drum track must have been replaced (jittered)");
    }

    @Test
    void drumsOnlyFalseTouchesPitchedToo() {
        Performance perf = pitchedAndDrumPiece();
        Performance after = HumanizerTransform.apply(perf,
                new HumanizerTransform.Params(20, false, 7L));

        // Both tracks should differ.
        assertNotSame(perf.score().tracks().get(0), after.score().tracks().get(0));
        assertNotSame(perf.score().tracks().get(1), after.score().tracks().get(1));
    }

    @Test
    void tickMsClampedToZero() {
        // A note at tickMs=0 with a wide-σ jitter could land at a
        // negative tickMs — must be clamped.
        var note = new DrumNote(0L, 100L, 36);
        var track = new Track(new TrackId("Drums"), TrackKind.DRUM, List.of(note));
        Performance perf = new Performance(
                new Score(List.of(track)),
                TempoTrack.constant(120),
                Instrumentation.empty(), Volume.empty(),
                Articulations.empty(), Pedaling.empty(),
                Velocities.empty());

        // Run with several seeds to exercise both positive and negative offsets.
        for (long seed = 1; seed <= 50; seed++) {
            Performance after = HumanizerTransform.apply(perf,
                    new HumanizerTransform.Params(200, true, seed));
            long tick = after.score().tracks().get(0).notes().get(0).tickMs();
            assertTrue(tick >= 0, "seed " + seed + ": tickMs went negative: " + tick);
        }
    }

    @Test
    void statisticalSigmaMatchesParameter() {
        // 1000 hits all at the same notional tick: jittered tick std-dev
        // should approximate σ = maxJitterMs/3 in ms.
        int n = 1000;
        List<ConcreteNote> hits = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            // Use big tickMs so clamping doesn't bias the distribution.
            hits.add(new DrumNote(10_000L, 100L, 36));
        }
        var track = new Track(new TrackId("Drums"), TrackKind.DRUM, hits);
        Performance perf = new Performance(
                new Score(List.of(track)),
                TempoTrack.constant(120),
                Instrumentation.empty(), Volume.empty(),
                Articulations.empty(), Pedaling.empty(),
                Velocities.empty());

        Performance after = HumanizerTransform.apply(perf,
                new HumanizerTransform.Params(30, true, 12345L));

        double sumDelta = 0;
        double sumSqDelta = 0;
        var notes = after.score().tracks().get(0).notes();
        for (var jitteredNote : notes) {
            long delta = jitteredNote.tickMs() - 10_000L;
            sumDelta += delta;
            sumSqDelta += delta * delta;
        }
        double mean = sumDelta / n;
        double variance = sumSqDelta / n - mean * mean;
        double stdDev = Math.sqrt(variance);

        // Expected σ = 30/3 = 10 ms. With N=1000 the empirical std-dev
        // should be well within ±20% of theoretical.
        assertTrue(Math.abs(mean) < 1.0, "mean drift > 1 ms: " + mean);
        assertTrue(stdDev > 8.0 && stdDev < 12.0,
                "σ outside [8, 12]: " + stdDev);
    }

    @Test
    void noteCountIsPreserved() {
        Performance perf = drumPiece(50, 100);
        Performance after = HumanizerTransform.apply(perf,
                new HumanizerTransform.Params(20, true, 99L));
        assertEquals(50, after.score().tracks().get(0).notes().size());
    }

    @Test
    void pitchAndPercussionPieceArePreserved() {
        Performance perf = pitchedAndDrumPiece();
        Performance after = HumanizerTransform.apply(perf,
                new HumanizerTransform.Params(15, false, 7L));

        // Pitches preserved on pitched track.
        var origPitched = perf.score().tracks().get(0).notes();
        var afterPitched = after.score().tracks().get(0).notes();
        for (int i = 0; i < origPitched.size(); i++) {
            assertEquals(((PitchedNote) origPitched.get(i)).midi(),
                    ((PitchedNote) afterPitched.get(i)).midi(),
                    "pitch unchanged");
        }
        // Drum-piece numbers preserved on drum track.
        var origDrum = perf.score().tracks().get(1).notes();
        var afterDrum = after.score().tracks().get(1).notes();
        for (int i = 0; i < origDrum.size(); i++) {
            assertEquals(((DrumNote) origDrum.get(i)).piece(),
                    ((DrumNote) afterDrum.get(i)).piece(),
                    "drum-piece number unchanged");
        }
    }
}
