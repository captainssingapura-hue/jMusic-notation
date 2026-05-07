package music.notation.performance;

import music.notation.expressivity.*;

import music.notation.structure.TimeSignature;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoPedalingTest {

    private static Performance pieceWithDuration(long lastNoteEndMs) {
        return pieceWithDuration(lastNoteEndMs, TempoTrack.constant(120));
    }

    private static Performance pieceWithDuration(long lastNoteEndMs, TempoTrack tempos) {
        var note = new PitchedNote(0, lastNoteEndMs, 60);
        var track = new Track(new TrackId("Piano"), TrackKind.PITCHED, List.of(note));
        return new Performance(
                new Score(List.of(track)),
                tempos,
                Instrumentation.empty(),
                Volume.empty(),
                Articulations.empty(),
                Pedaling.empty());
    }

    @Test
    void emptyPerformanceYieldsEmptyPedaling() {
        var perf = new Performance(
                Score.empty(), TempoTrack.empty(),
                Instrumentation.empty(), Volume.empty(), Articulations.empty(),
                Pedaling.empty());
        assertTrue(AutoPedaling.generate(perf, new TimeSignature(4, 4))
                .byTrack().isEmpty());
    }

    @Test
    void nullTimeSigYieldsEmpty() {
        assertTrue(AutoPedaling.generate(pieceWithDuration(2000), null)
                .byTrack().isEmpty());
    }

    @Test
    void fourFourAtOneTwentyBpmGivesTwoSecondsPerBar() {
        // 1 bar of 4/4 at 120bpm = 2000 ms exactly. A 3-bar piece (6000ms)
        // should produce: DOWN @ 0, CHANGE @ 2000, CHANGE @ 4000, UP @ 6000.
        var perf = pieceWithDuration(6000);
        var ped = AutoPedaling.generate(perf, new TimeSignature(4, 4));
        assertEquals(1, ped.byTrack().size());

        var changes = ped.byTrack().values().iterator().next().changes();
        assertEquals(4, changes.size());
        assertEquals(PedalState.DOWN,   changes.get(0).state());
        assertEquals(0L,                changes.get(0).tickMs());
        assertEquals(PedalState.CHANGE, changes.get(1).state());
        assertEquals(2000L,             changes.get(1).tickMs());
        assertEquals(PedalState.CHANGE, changes.get(2).state());
        assertEquals(4000L,             changes.get(2).tickMs());
        assertEquals(PedalState.UP,     changes.get(3).state());
        assertEquals(6000L,             changes.get(3).tickMs());
    }

    @Test
    void threeFourGivesShorterBars() {
        // 3/4 at 120bpm: 1 bar = 1500 ms. A 4500 ms piece → 3 bars.
        var perf = pieceWithDuration(4500);
        var changes = AutoPedaling.generate(perf, new TimeSignature(3, 4))
                .byTrack().values().iterator().next().changes();
        assertEquals(4, changes.size());   // DOWN + 2 CHANGEs + UP
        assertEquals(1500L, changes.get(1).tickMs());
        assertEquals(3000L, changes.get(2).tickMs());
        assertEquals(4500L, changes.get(3).tickMs());
    }

    @Test
    void drumTracksAreSkipped() {
        var pitchedNote = new PitchedNote(0, 2000, 60);
        var drumNote = new DrumNote(0, 100, 36);
        var pitched = new Track(new TrackId("Piano"), TrackKind.PITCHED, List.of(pitchedNote));
        var drums   = new Track(new TrackId("Drums"), TrackKind.DRUM,    List.of(drumNote));
        var perf = new Performance(
                new Score(List.of(pitched, drums)),
                TempoTrack.constant(120),
                Instrumentation.empty(), Volume.empty(),
                Articulations.empty(), Pedaling.empty());

        var ped = AutoPedaling.generate(perf, new TimeSignature(4, 4));
        assertEquals(1, ped.byTrack().size(), "only pitched tracks get auto-pedal");
        assertTrue(ped.byTrack().containsKey(new TrackId("Piano")));
    }

    @Test
    void shorterTempoStretchesBarMs() {
        // Constant 60 bpm = half-speed → bars are 4000 ms long in 4/4.
        // Same 6000 ms piece → DOWN, CHANGE @ 4000, UP @ 6000.
        var perf = pieceWithDuration(6000, TempoTrack.constant(60));
        var changes = AutoPedaling.generate(perf, new TimeSignature(4, 4))
                .byTrack().values().iterator().next().changes();
        assertEquals(3, changes.size());
        assertEquals(4000L, changes.get(1).tickMs());
        assertEquals(6000L, changes.get(2).tickMs());
        assertEquals(PedalState.UP, changes.get(2).state());
    }

    @Test
    void bassChangeAddsMidBarChangeEvent() {
        // 4/4 at 120bpm, 1 bar = 2000 ms. Two-bar piece (4000 ms total)
        // with bass moving inside each bar:
        //   t=0    : C2 (36) + C5 (72)
        //   t=1000 : G2 (43)            ← bass C→G mid-bar 1
        //   t=2000 : C2 (36)            ← bar boundary; also bass G→C
        //   t=3000 : F2 (41)            ← bass C→F mid-bar 2
        // Expect: DOWN @ 0, CHANGE @ 1000, CHANGE @ 2000, CHANGE @ 3000, UP @ 4000.
        var bass = new Track(new TrackId("LH"), TrackKind.PITCHED, List.of(
                new PitchedNote(0,    1000, 36),
                new PitchedNote(1000, 1000, 43),
                new PitchedNote(2000, 1000, 36),
                new PitchedNote(3000, 1000, 41)));
        var treble = new Track(new TrackId("RH"), TrackKind.PITCHED, List.of(
                new PitchedNote(0, 4000, 72)));
        var perf = new Performance(
                new Score(List.of(bass, treble)),
                TempoTrack.constant(120),
                Instrumentation.empty(), Volume.empty(),
                Articulations.empty(), Pedaling.empty());

        var changes = AutoPedaling.generate(perf, new TimeSignature(4, 4))
                .byTrack().get(new TrackId("LH")).changes();
        assertEquals(5, changes.size());
        assertEquals(PedalState.DOWN,   changes.get(0).state());
        assertEquals(0L,                changes.get(0).tickMs());
        assertEquals(PedalState.CHANGE, changes.get(1).state());
        assertEquals(1000L,             changes.get(1).tickMs());
        assertEquals(PedalState.CHANGE, changes.get(2).state());
        assertEquals(2000L,             changes.get(2).tickMs());
        assertEquals(PedalState.CHANGE, changes.get(3).state());
        assertEquals(3000L,             changes.get(3).tickMs());
        assertEquals(PedalState.UP,     changes.get(4).state());
        assertEquals(4000L,             changes.get(4).tickMs());
    }

    @Test
    void trebleOnlyMelodyDoesNotOverPedal() {
        // Solo melody entirely above middle C → bass detector finds
        // nothing → only bar-boundary CHANGEs survive.
        var melody = new Track(new TrackId("RH"), TrackKind.PITCHED, List.of(
                new PitchedNote(0,    500, 72),  // C5
                new PitchedNote(500,  500, 74),  // D5
                new PitchedNote(1000, 500, 76),  // E5
                new PitchedNote(1500, 500, 77),  // F5
                new PitchedNote(2000, 500, 79),  // G5
                new PitchedNote(2500, 500, 81),  // A5
                new PitchedNote(3000, 500, 83),  // B5
                new PitchedNote(3500, 500, 84))); // C6
        var perf = new Performance(
                new Score(List.of(melody)),
                TempoTrack.constant(120),
                Instrumentation.empty(), Volume.empty(),
                Articulations.empty(), Pedaling.empty());

        // Plain bar-only output: DOWN @ 0, CHANGE @ 2000, UP @ 4000.
        // (No mid-bar CHANGEs because no notes are below MIDI 60.)
        var changes = AutoPedaling.generate(perf, new TimeSignature(4, 4))
                .byTrack().values().iterator().next().changes();
        assertEquals(3, changes.size());
        assertEquals(PedalState.DOWN,   changes.get(0).state());
        assertEquals(PedalState.CHANGE, changes.get(1).state());
        assertEquals(2000L,             changes.get(1).tickMs());
        assertEquals(PedalState.UP,     changes.get(2).state());
        assertEquals(4000L,             changes.get(2).tickMs());
    }

    @Test
    void repeatedBassNoteDoesNotEmitChange() {
        // Bass plays the same C2 four times — same harmony, no
        // mid-bar CHANGEs. Only the bar boundary contributes.
        var bass = new Track(new TrackId("LH"), TrackKind.PITCHED, List.of(
                new PitchedNote(0,    500, 36),
                new PitchedNote(500,  500, 36),
                new PitchedNote(1000, 500, 36),
                new PitchedNote(1500, 500, 36),
                new PitchedNote(2000, 500, 36),
                new PitchedNote(2500, 500, 36),
                new PitchedNote(3000, 500, 36),
                new PitchedNote(3500, 500, 36)));
        var perf = new Performance(
                new Score(List.of(bass)),
                TempoTrack.constant(120),
                Instrumentation.empty(), Volume.empty(),
                Articulations.empty(), Pedaling.empty());

        var changes = AutoPedaling.generate(perf, new TimeSignature(4, 4))
                .byTrack().values().iterator().next().changes();
        // DOWN @ 0, CHANGE @ 2000 (bar), UP @ 4000.
        assertEquals(3, changes.size());
        assertEquals(2000L, changes.get(1).tickMs());
    }

    @Test
    void bassChangeNearBarBoundaryIsDeduped() {
        // Bass at 1950ms (50ms before the bar boundary at 2000ms) — the
        // 200ms min-gap should drop it; only the bar boundary survives.
        var bass = new Track(new TrackId("LH"), TrackKind.PITCHED, List.of(
                new PitchedNote(0,    1000, 36),
                new PitchedNote(1950, 1000, 43),  // bass C→G but too close to bar 2
                new PitchedNote(3000, 1000, 41))); // bass G→F mid-bar 2 — kept
        var perf = new Performance(
                new Score(List.of(bass)),
                TempoTrack.constant(120),
                Instrumentation.empty(), Volume.empty(),
                Articulations.empty(), Pedaling.empty());

        var changes = AutoPedaling.generate(perf, new TimeSignature(4, 4))
                .byTrack().values().iterator().next().changes();
        // DOWN @ 0, CHANGE @ 2000 (bar; bass@1950 dropped),
        // CHANGE @ 3000 (bass G→F mid-bar 2), UP @ 4000.
        assertEquals(4, changes.size());
        assertEquals(0L,    changes.get(0).tickMs());
        assertEquals(2000L, changes.get(1).tickMs());
        assertEquals(3000L, changes.get(2).tickMs());
        assertEquals(4000L, changes.get(3).tickMs());
    }

    @Test
    void closeBassChangesAreDebouncedAgainstEachOther() {
        // Chromatic bass walk: notes 100ms apart. The first one starts
        // a chord; subsequent ones are within 200ms of the previous
        // emitted change → all dropped. Bar boundary remains.
        var bass = new Track(new TrackId("LH"), TrackKind.PITCHED, List.of(
                new PitchedNote(0,    100, 36),  // C2
                new PitchedNote(100,  100, 37),  // C#2 — bass change candidate @ 100
                new PitchedNote(200,  100, 38),  // D2  — candidate @ 200
                new PitchedNote(300,  100, 39),  // D#2 — candidate @ 300
                new PitchedNote(3500, 500, 39))); // same pitch as prev bass → no candidate
        var perf = new Performance(
                new Score(List.of(bass)),
                TempoTrack.constant(120),
                Instrumentation.empty(), Volume.empty(),
                Articulations.empty(), Pedaling.empty());

        var changes = AutoPedaling.generate(perf, new TimeSignature(4, 4))
                .byTrack().values().iterator().next().changes();
        // Bass@100 is within 200ms of DOWN@0 → DROPPED.
        // Bass@200 → too close to DOWN@0 (gap 200, NOT <200 → kept).
        // Bass@300 → 300-200=100 <200 → dropped.
        // Plus bar boundary at 2000, UP at 4000.
        // So we expect: DOWN@0, CHANGE@200, CHANGE@2000, UP@4000.
        assertEquals(4, changes.size());
        assertEquals(0L,    changes.get(0).tickMs());
        assertEquals(200L,  changes.get(1).tickMs());
        assertEquals(2000L, changes.get(2).tickMs());
        assertEquals(4000L, changes.get(3).tickMs());
    }

    @Test
    void tempoChangeMidPieceShiftsBarBoundary() {
        // 4/4. Bar 1 at 60 bpm → 4000 ms. Then tempo doubles to 120 bpm,
        // so bar 2 takes only 2000 ms. Piece length 7000 ms → boundaries
        // land at 4000 (bar 2 start) and 6000 (bar 3 start), then UP @ 7000.
        var tempos = new TempoTrack(List.of(
                new TempoChange(0,    60),
                new TempoChange(4000, 120)));
        var perf = pieceWithDuration(7000, tempos);
        var changes = AutoPedaling.generate(perf, new TimeSignature(4, 4))
                .byTrack().values().iterator().next().changes();
        // Expect: DOWN @ 0, CHANGE @ 4000, CHANGE @ 6000, UP @ 7000
        assertEquals(4, changes.size());
        assertEquals(PedalState.DOWN,   changes.get(0).state());
        assertEquals(0L,                changes.get(0).tickMs());
        assertEquals(PedalState.CHANGE, changes.get(1).state());
        assertEquals(4000L,             changes.get(1).tickMs());
        assertEquals(PedalState.CHANGE, changes.get(2).state());
        assertEquals(6000L,             changes.get(2).tickMs());
        assertEquals(PedalState.UP,     changes.get(3).state());
        assertEquals(7000L,             changes.get(3).tickMs());
    }
}
