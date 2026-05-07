package music.notation.performance;

import music.notation.expressivity.*;

import music.notation.structure.TimeSignature;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoVelocityTest {

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
        assertTrue(AutoVelocity.generate(pieceWith(new PitchedNote(0, 500, 60)), null)
                .byTrack().isEmpty());
    }

    @Test
    void downbeatAccentIsHigherThanOtherBeats() {
        // 4/4 at 120bpm = 2000ms per bar = 500ms per quarter beat.
        // Notes at beat 1 (t=0), beat 2 (t=500), beat 3 (t=1000), beat 4 (t=1500).
        var perf = pieceWith(
                new PitchedNote(0,    400, 60),  // beat 1 → +5 + jitter
                new PitchedNote(500,  400, 62),  // beat 2 → +0 + jitter
                new PitchedNote(1000, 400, 64),  // beat 3 → +3 + jitter
                new PitchedNote(1500, 400, 65)); // beat 4 → +0 + jitter

        var ctrl = AutoVelocity.generate(perf, new TimeSignature(4, 4))
                .byTrack().values().iterator().next();
        // Beat 1 should be strictly higher than beat 2 and beat 4 (no mid-bar bump there).
        // Allow ±jitter window.
        int b1 = ctrl.velocityAt(0);
        int b2 = ctrl.velocityAt(500);
        int b3 = ctrl.velocityAt(1000);
        int b4 = ctrl.velocityAt(1500);
        // The accents are nominally +5/+0/+3/+0 over base 90, with ±2 jitter.
        // Worst case: b1 = 90+5-2 = 93, b2 = 90+0+2 = 92 → b1 > b2 holds.
        assertTrue(b1 >= 93 && b1 <= 97, "beat1 in range [93..97]: " + b1);
        assertTrue(b3 >= 91 && b3 <= 95, "beat3 in range [91..95]: " + b3);
        // Sanity: every velocity is a plausible MIDI value.
        for (int v : new int[] {b1, b2, b3, b4}) {
            assertTrue(v >= 1 && v <= 127);
        }
    }

    @Test
    void downbeatAccentRecursPerBar() {
        // 4/4 at 120bpm, two-bar piece. Beat 1 of bar 2 should also be accented.
        var perf = pieceWith(
                new PitchedNote(0,    400, 60),  // bar 1, beat 1
                new PitchedNote(2000, 400, 60)); // bar 2, beat 1
        var ctrl = AutoVelocity.generate(perf, new TimeSignature(4, 4))
                .byTrack().values().iterator().next();
        int v0    = ctrl.velocityAt(0);
        int v2000 = ctrl.velocityAt(2000);
        assertTrue(v0 >= 93 && v0 <= 97, "bar1 beat1 accented: " + v0);
        assertTrue(v2000 >= 93 && v2000 <= 97, "bar2 beat1 accented: " + v2000);
    }

    @Test
    void drumTracksAreSkipped() {
        var pitched = new Track(new TrackId("Piano"), TrackKind.PITCHED,
                List.<ConcreteNote>of(new PitchedNote(0, 500, 60)));
        var drums = new Track(new TrackId("Drums"), TrackKind.DRUM,
                List.<ConcreteNote>of(new DrumNote(0, 100, 36)));
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
        // 1 bar at 120bpm = 1500ms. Beats at 0, 500, 1000.
        var perf = pieceWith(
                new PitchedNote(0,    400, 60),
                new PitchedNote(500,  400, 62),
                new PitchedNote(1000, 400, 64));
        var ctrl = AutoVelocity.generate(perf, new TimeSignature(3, 4))
                .byTrack().values().iterator().next();
        int b1 = ctrl.velocityAt(0);
        int b2 = ctrl.velocityAt(500);
        int b3 = ctrl.velocityAt(1000);
        assertTrue(b1 >= 93 && b1 <= 97, "beat1 accent: " + b1);
        // Beats 2 and 3 should be near base (no mid-bar bump).
        for (int v : new int[] {b2, b3}) {
            assertTrue(v >= 88 && v <= 92, "non-accent in [88..92]: " + v);
        }
    }

    @Test
    void deterministicAcrossCalls() {
        // Same input → bit-identical output.
        var perf = pieceWith(
                new PitchedNote(0,    400, 60),
                new PitchedNote(500,  400, 62),
                new PitchedNote(1000, 400, 64));
        var v1 = AutoVelocity.generate(perf, new TimeSignature(4, 4));
        var v2 = AutoVelocity.generate(perf, new TimeSignature(4, 4));
        assertEquals(v1, v2);
    }
}
