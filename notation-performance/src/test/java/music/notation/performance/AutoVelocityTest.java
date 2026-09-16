package music.notation.performance;

import music.notation.duration.Duration;
import music.notation.expressivity.*;

import music.notation.structure.TimeSignature;
import org.junit.jupiter.api.Test;

import java.util.List;

import static music.notation.performance.AutoVelocity.BASE_LEVEL;
import static music.notation.performance.AutoVelocity.DOWNBEAT_ACCENT;
import static music.notation.performance.AutoVelocity.JITTER;
import static music.notation.performance.AutoVelocity.MIDBAR_ACCENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Post-ms→Duration migration: beat positions are musical (quarter
 * notes in x/4 meters), velocities are levels in [0.0, 1.0]. The old
 * MIDI-byte ranges (base 90, +5 downbeat, +3 mid-bar, ±2 jitter) are
 * expressed via the production constants divided by 127.
 */
class AutoVelocityTest {

    /** {@code n} quarter notes. */
    private static Duration q(long n) { return Duration.of(n, 4); }

    /** A note a shade shorter than a quarter (the old 400 ms at 120 bpm = 1/5). */
    private static final Duration SHORT = Duration.of(1, 5);

    private static final double EPS = 1e-9;

    private static void assertInRange(double lo, double hi, double v, String label) {
        assertTrue(v >= lo - EPS && v <= hi + EPS,
                label + " in [" + lo + ".." + hi + "]: " + v);
    }

    private static void assertAccented(double v, String label) {
        assertInRange(BASE_LEVEL + DOWNBEAT_ACCENT - JITTER,
                      BASE_LEVEL + DOWNBEAT_ACCENT + JITTER, v, label);
    }

    private static void assertMidbar(double v, String label) {
        assertInRange(BASE_LEVEL + MIDBAR_ACCENT - JITTER,
                      BASE_LEVEL + MIDBAR_ACCENT + JITTER, v, label);
    }

    private static void assertUnaccented(double v, String label) {
        assertInRange(BASE_LEVEL - JITTER, BASE_LEVEL + JITTER, v, label);
    }

    private static Performance pieceWith(PitchedNote... notes) {
        var track = new Track(new TrackId("Piano"), TrackKind.PITCHED, List.of(notes));
        return new Performance(
                new Score(List.of(track)),
                TempoTrack.constant(120),
                Instrumentation.empty(),
                Volume.empty(), Articulations.empty(),
                Pedaling.empty(), Velocities.empty());
    }

    @Test
    void emptyPerformanceYieldsEmptyVelocities() {
        var perf = new Performance(
                Score.empty(), TempoTrack.empty(),
                Instrumentation.empty(), Volume.empty(),
                Articulations.empty(), Pedaling.empty(), Velocities.empty());
        assertTrue(AutoVelocity.generate(perf, new TimeSignature(4, 4))
                .byTrack().isEmpty());
    }

    @Test
    void nullTimeSigYieldsEmpty() {
        assertTrue(AutoVelocity.generate(pieceWith(new PitchedNote(q(0), q(1), 60)), null)
                .byTrack().isEmpty());
    }

    @Test
    void downbeatAccentIsHigherThanOtherBeats() {
        // 4/4: one bar = 4 quarters. Notes at beat 1 (0), beat 2 (1q),
        // beat 3 (2q), beat 4 (3q).
        var perf = pieceWith(
                new PitchedNote(q(0), SHORT, 60),  // beat 1 → +DOWNBEAT + jitter
                new PitchedNote(q(1), SHORT, 62),  // beat 2 → +0 + jitter
                new PitchedNote(q(2), SHORT, 64),  // beat 3 → +MIDBAR + jitter
                new PitchedNote(q(3), SHORT, 65)); // beat 4 → +0 + jitter

        var ctrl = AutoVelocity.generate(perf, new TimeSignature(4, 4))
                .byTrack().values().iterator().next();
        double b1 = ctrl.levelAt(q(0));
        double b2 = ctrl.levelAt(q(1));
        double b3 = ctrl.levelAt(q(2));
        double b4 = ctrl.levelAt(q(3));
        // The accents are nominally +5/+0/+3/+0 (in 1/127 units) over the
        // base, with ±2 jitter. Worst case: b1 = base+5-2 = base+3,
        // b2 = base+0+2 → b1 > b2 holds.
        assertAccented(b1, "beat1");
        assertMidbar(b3, "beat3");
        assertTrue(b1 > b2, "downbeat louder than beat 2");
        assertTrue(b1 > b4, "downbeat louder than beat 4");
        // Sanity: every level is a plausible value.
        for (double v : new double[] {b1, b2, b3, b4}) {
            assertTrue(v > 0.0 && v <= 1.0);
        }
    }

    @Test
    void downbeatAccentRecursPerBar() {
        // 4/4, two-bar piece. Beat 1 of bar 2 should also be accented.
        var perf = pieceWith(
                new PitchedNote(q(0), SHORT, 60),  // bar 1, beat 1
                new PitchedNote(q(4), SHORT, 60)); // bar 2, beat 1
        var ctrl = AutoVelocity.generate(perf, new TimeSignature(4, 4))
                .byTrack().values().iterator().next();
        double v0 = ctrl.levelAt(q(0));
        double v4 = ctrl.levelAt(q(4));
        assertAccented(v0, "bar1 beat1 accented");
        assertAccented(v4, "bar2 beat1 accented");
    }

    @Test
    void drumTracksAreSkipped() {
        var pitched = new Track(new TrackId("Piano"), TrackKind.PITCHED,
                List.<ConcreteNote>of(new PitchedNote(q(0), q(1), 60)));
        var drums = new Track(new TrackId("Drums"), TrackKind.DRUM,
                List.<ConcreteNote>of(new DrumNote(q(0), Duration.of(1, 20), 36)));
        var perf = new Performance(
                new Score(List.of(pitched, drums)),
                TempoTrack.constant(120),
                Instrumentation.empty(),
                Volume.empty(), Articulations.empty(),
                Pedaling.empty(), Velocities.empty());

        var velocities = AutoVelocity.generate(perf, new TimeSignature(4, 4));
        assertEquals(1, velocities.byTrack().size());
        assertTrue(velocities.byTrack().containsKey(new TrackId("Piano")));
        assertFalse(velocities.byTrack().containsKey(new TrackId("Drums")));
    }

    @Test
    void threeFourHasNoMidbarAccent() {
        // 3/4: odd beat count → no mid-bar accent. Only beat 1 should be louder.
        // Beats at 0, 1q, 2q.
        var perf = pieceWith(
                new PitchedNote(q(0), SHORT, 60),
                new PitchedNote(q(1), SHORT, 62),
                new PitchedNote(q(2), SHORT, 64));
        var ctrl = AutoVelocity.generate(perf, new TimeSignature(3, 4))
                .byTrack().values().iterator().next();
        double b1 = ctrl.levelAt(q(0));
        double b2 = ctrl.levelAt(q(1));
        double b3 = ctrl.levelAt(q(2));
        assertAccented(b1, "beat1 accent");
        // Beats 2 and 3 should be near base (no mid-bar bump).
        assertUnaccented(b2, "beat2 non-accent");
        assertUnaccented(b3, "beat3 non-accent");
    }

    @Test
    void deterministicAcrossCalls() {
        // Same input → bit-identical output.
        var perf = pieceWith(
                new PitchedNote(q(0), SHORT, 60),
                new PitchedNote(q(1), SHORT, 62),
                new PitchedNote(q(2), SHORT, 64));
        var v1 = AutoVelocity.generate(perf, new TimeSignature(4, 4));
        var v2 = AutoVelocity.generate(perf, new TimeSignature(4, 4));
        assertEquals(v1, v2);
    }
}
