package music.notation.performance;

import music.notation.duration.Duration;
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
 * and onsets must stay non-negative even under wide-σ jitter.</p>
 *
 * <p>Jitter is perceptual (ms) while notes are anchored at musical
 * {@link Duration}s, so the tests bridge with a {@link TimeMapper} at
 * the piece's constant 120 bpm — exactly what the transform does
 * internally. At 120 bpm one quarter = 500 ms.</p>
 */
class HumanizerTransformTest {

    private static final TempoTrack TEMPO = TempoTrack.constant(120);
    private static final TimeMapper MAPPER = new TimeMapper(TEMPO);

    /** Musical position that renders at {@code ms} wall-clock under {@link #TEMPO}. */
    private static Duration ms(long ms) { return MAPPER.toDuration(ms); }

    /** {@code n} quarter notes. */
    private static Duration q(long n) { return Duration.of(n, 4); }

    /** The old 100 ms drum-hit length at 120 bpm = 1/20 of a whole. */
    private static final Duration HIT = Duration.of(1, 20);

    /** Build a 1-track piece with N drum hits spaced {@code step} apart. */
    private static Performance drumPiece(int n, Duration step) {
        List<ConcreteNote> hits = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            hits.add(new DrumNote(step.times(i), HIT, 36));
        }
        var track = new Track(new TrackId("Drums"), TrackKind.DRUM, hits);
        return new Performance(
                new Score(List.of(track)),
                TEMPO,
                Instrumentation.empty(), Volume.empty(),
                Articulations.empty(), Pedaling.empty(),
                Velocities.empty());
    }

    /** A piece with one PITCHED track + one DRUM track. */
    private static Performance pitchedAndDrumPiece() {
        var pitched = new Track(
                new TrackId("Piano"), TrackKind.PITCHED,
                List.of(new PitchedNote(q(1), Duration.of(1, 10), 60),
                        new PitchedNote(q(2), Duration.of(1, 10), 62),
                        new PitchedNote(q(3), Duration.of(1, 10), 64)));
        var drums = new Track(
                new TrackId("Drums"), TrackKind.DRUM,
                List.of(new DrumNote(q(1), HIT, 36),
                        new DrumNote(q(2), HIT, 36),
                        new DrumNote(q(3), HIT, 36)));
        return new Performance(
                new Score(List.of(pitched, drums)),
                TEMPO,
                Instrumentation.empty(), Volume.empty(),
                Articulations.empty(), Pedaling.empty(),
                Velocities.empty());
    }

    @Test
    void offIsIdentity() {
        Performance perf = drumPiece(10, q(1));
        assertSame(perf, HumanizerTransform.apply(perf, HumanizerTransform.Params.OFF));
    }

    @Test
    void nullParamsIsIdentity() {
        Performance perf = drumPiece(10, q(1));
        assertSame(perf, HumanizerTransform.apply(perf, null));
    }

    @Test
    void nullPerformanceReturnsNull() {
        assertNull(HumanizerTransform.apply(null, HumanizerTransform.Params.MEDIUM));
    }

    @Test
    void deterministicForFixedSeed() {
        Performance perf = drumPiece(20, q(1));
        var params = new HumanizerTransform.Params(10, true, 42L);

        Performance a = HumanizerTransform.apply(perf, params);
        Performance b = HumanizerTransform.apply(perf, params);

        assertEquals(a, b, "same input + same seed must produce equal Performance");
    }

    @Test
    void differentSeedsProduceDifferentJitter() {
        Performance perf = drumPiece(50, q(1));
        Performance a = HumanizerTransform.apply(perf,
                new HumanizerTransform.Params(10, true, 42L));
        Performance b = HumanizerTransform.apply(perf,
                new HumanizerTransform.Params(10, true, 43L));
        assertNotEquals(a, b);
    }

    @Test
    void durationsArePreserved() {
        Performance perf = drumPiece(20, q(1));
        Performance after = HumanizerTransform.apply(perf,
                new HumanizerTransform.Params(20, true, 7L));

        var origNotes  = perf.score().tracks().get(0).notes();
        var afterNotes = after.score().tracks().get(0).notes();
        assertEquals(origNotes.size(), afterNotes.size());
        for (int i = 0; i < origNotes.size(); i++) {
            assertTrue(origNotes.get(i).duration().equalsDuration(afterNotes.get(i).duration()),
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
    void onsetClampedToZero() {
        // A note at position 0 with a wide-σ jitter could land at a
        // negative onset — must be clamped.
        var note = new DrumNote(Duration.zero(), HIT, 36);
        var track = new Track(new TrackId("Drums"), TrackKind.DRUM, List.of(note));
        Performance perf = new Performance(
                new Score(List.of(track)),
                TEMPO,
                Instrumentation.empty(), Volume.empty(),
                Articulations.empty(), Pedaling.empty(),
                Velocities.empty());

        // Run with several seeds to exercise both positive and negative offsets.
        for (long seed = 1; seed <= 50; seed++) {
            Performance after = HumanizerTransform.apply(perf,
                    new HumanizerTransform.Params(200, true, seed));
            Duration at = after.score().tracks().get(0).notes().get(0).at();
            assertTrue(at.compareDuration(Duration.zero()) >= 0,
                    "seed " + seed + ": onset went negative: " + at);
        }
    }

    @Test
    void statisticalSigmaMatchesParameter() {
        // 1000 hits all at the same notional position: jittered onset
        // std-dev (in wall-clock ms) should approximate σ = maxJitterMs/3.
        int n = 1000;
        final long baseMs = 10_000L;   // 20 quarters at 120 bpm
        List<ConcreteNote> hits = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            // Use a big onset so clamping doesn't bias the distribution.
            hits.add(new DrumNote(ms(baseMs), HIT, 36));
        }
        var track = new Track(new TrackId("Drums"), TrackKind.DRUM, hits);
        Performance perf = new Performance(
                new Score(List.of(track)),
                TEMPO,
                Instrumentation.empty(), Volume.empty(),
                Articulations.empty(), Pedaling.empty(),
                Velocities.empty());

        Performance after = HumanizerTransform.apply(perf,
                new HumanizerTransform.Params(30, true, 12345L));

        double sumDelta = 0;
        double sumSqDelta = 0;
        var notes = after.score().tracks().get(0).notes();
        for (var jitteredNote : notes) {
            long delta = MAPPER.toMs(jitteredNote.at()) - baseMs;
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
        Performance perf = drumPiece(50, ms(100));
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
