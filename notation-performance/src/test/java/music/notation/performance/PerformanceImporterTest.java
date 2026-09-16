package music.notation.performance;

import music.notation.duration.Duration;
import music.notation.expressivity.*;

import music.notation.event.Instrument;
import music.notation.pitch.NoteName;
import music.notation.structure.KeySignature;
import music.notation.structure.MelodicTrack;
import music.notation.structure.Mode;
import music.notation.structure.Piece;
import music.notation.structure.TimeSignature;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PerformanceImporterTest {

    private static final TimeSignature TS_4_4 = new TimeSignature(4, 4);
    private static final KeySignature  KEY_C  = new KeySignature(NoteName.C, Mode.MAJOR);

    /** {@code n} eighth notes (the old 250 ms at 120 bpm). */
    private static Duration e(long n) { return Duration.of(n, 8); }

    private static Performance perfWithTrack(String name, int program, PitchedNote... notes) {
        var id = new TrackId(name);
        var track = new Track(id, TrackKind.PITCHED, List.of(notes));
        var instr = new Instrumentation(java.util.Map.of(id, InstrumentControl.constant(program)));
        return new Performance(
                Score.of(track),
                TempoTrack.constant(120),
                instr,
                Volume.empty(),
                Articulations.empty());
    }

    @Test
    void preserveMode_keepsOneTrackPerInputTrack() {
        // Mixed-register input that would split under SPLIT mode.
        var perf = perfWithTrack("piano", 0,
                new PitchedNote(e(0), e(2), 72),
                new PitchedNote(e(2), e(2), 48),     // crosses cutoff
                new PitchedNote(e(4), e(2), 76));
        Piece p = PerformanceImporter.toPiece(perf, TS_4_4, KEY_C, 120, "demo", 60,
                PerformanceImporter.SplitMode.PRESERVE);
        assertEquals(1, p.tracks().size(), "PRESERVE: 1 source ⇒ 1 output track");
        assertEquals("piano", p.tracks().get(0).name(),
                "PRESERVE: name should be unaltered (no RH/LH/v suffix)");
    }

    @Test
    void monophonicSingleTrack_oneOutputTrack_noBandSuffix() {
        // Four eighth-notes in the high register at 120 BPM.
        // Positions/lengths in eighths.
        var perf = perfWithTrack("piano", 0,
                new PitchedNote(e(0), e(1), 72),
                new PitchedNote(e(1), e(1), 74),
                new PitchedNote(e(2), e(1), 76),
                new PitchedNote(e(3), e(1), 77));

        Piece p = PerformanceImporter.toPiece(perf, TS_4_4, KEY_C, 120, "demo", 60);
        assertEquals(1, p.tracks().size());
        assertEquals("piano", p.tracks().get(0).name(),
                "single voice in single band ⇒ no suffix");
    }

    @Test
    void mixedRegisterTrack_splitsIntoRH_LH() {
        // Two notes high, two notes low — different bars to keep
        // monophonic-in-time within each band.
        var perf = perfWithTrack("piano", 0,
                new PitchedNote(e(0), e(2), 72),    // RH C5
                new PitchedNote(e(2), e(2), 74),    // RH D5
                new PitchedNote(e(4), e(2), 48),    // LH C3
                new PitchedNote(e(6), e(2), 50));   // LH D3

        Piece p = PerformanceImporter.toPiece(perf, TS_4_4, KEY_C, 120, "demo", 60);
        assertEquals(2, p.tracks().size());
        var names = p.tracks().stream().map(t -> t.name()).toList();
        assertTrue(names.contains("piano · RH"), "expected RH track, got " + names);
        assertTrue(names.contains("piano · LH"), "expected LH track, got " + names);
    }

    @Test
    void overlappingPadAndMelody_splitsIntoTwoVoicesInRH() {
        // Whole-note pad + 4 quarters above, all in the high band.
        var perf = perfWithTrack("piano", 0,
                new PitchedNote(e(0), e(8), 64),    // pad: E4 (whole note)
                new PitchedNote(e(0), e(2), 72),    // melody: C5
                new PitchedNote(e(2), e(2), 74),
                new PitchedNote(e(4), e(2), 76),
                new PitchedNote(e(6), e(2), 77));

        Piece p = PerformanceImporter.toPiece(perf, TS_4_4, KEY_C, 120, "demo", 60);
        assertTrue(p.tracks().size() >= 2,
                "expected at least 2 voices, got " + p.tracks().size());
        // No LH band content, so the band suffix is omitted.
        for (var t : p.tracks()) {
            assertFalse(t.name().contains("LH"),
                    "no LH expected: " + t.name());
            assertTrue(t.name().contains("v") || t.name().equals("piano"),
                    "expected voice suffix or unmodified name: " + t.name());
        }
    }

    @Test
    void instrumentPreservedFromPerformance() {
        var perf = perfWithTrack("lead", 81, new PitchedNote(e(0), e(2), 72));
        Piece p = PerformanceImporter.toPiece(perf, TS_4_4, KEY_C, 120, "demo", 60);
        // GM 81 = SYNTH_LEAD_SQUARE per the standard map.
        // Just assert it's not the default piano fallback.
        var t = (MelodicTrack) p.tracks().get(0);
        assertNotEquals(Instrument.ACOUSTIC_GRAND_PIANO, t.defaultInstrument(),
                "import should pick up the program-change instrument");
    }

    @Test
    void emptyPerformance_yieldsPieceWithNoTracks() {
        var perf = new Performance(
                Score.empty(),
                TempoTrack.constant(120),
                Instrumentation.empty(),
                Volume.empty(),
                Articulations.empty());
        Piece p = PerformanceImporter.toPiece(perf, TS_4_4, KEY_C, 120, "demo", 60);
        assertTrue(p.tracks().isEmpty());
    }

    @Test
    void drumSourceTrackBecomesOneDrumLane() {
        var pid = new TrackId("piano");
        var did = new TrackId("drums");
        var pitched = new Track(pid, TrackKind.PITCHED,
                List.of(new PitchedNote(e(0), e(2), 72)));
        // Drums: kick (note 36) on beats 1 & 3; snare (38) on beats 2 & 4.
        var drums = new Track(did, TrackKind.DRUM,
                List.of(new DrumNote(e(0), e(1), 36),
                        new DrumNote(e(2), e(1), 38),
                        new DrumNote(e(4), e(1), 36),
                        new DrumNote(e(6), e(1), 38)));
        var perf = new Performance(
                Score.of(pitched, drums),
                TempoTrack.constant(120),
                new Instrumentation(java.util.Map.of(
                        pid, InstrumentControl.constant(0),
                        did, InstrumentControl.constant(0))),
                Volume.empty(),
                Articulations.empty());
        Piece p = PerformanceImporter.toPiece(perf, TS_4_4, KEY_C, 120, "demo", 60);
        // 1 melodic + 1 merged drum lane (every percussion piece on one lane).
        assertEquals(2, p.tracks().size(),
                "expected 1 melodic + 1 drum lane, got " + p.tracks().size());
        var names = p.tracks().stream().map(t -> t.name()).toList();
        assertTrue(names.contains("drums"),
                "expected a 'drums' lane, got " + names);
    }
}
