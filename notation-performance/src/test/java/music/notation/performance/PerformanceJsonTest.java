package music.notation.performance;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JSON round-trip parity tests for {@link PerformanceJson}.
 */
class PerformanceJsonTest {

    private static final TrackId LEAD = new TrackId("lead");
    private static final TrackId BASS = new TrackId("bass");
    private static final TrackId DRUMS = new TrackId("drums");

    @Test
    void toJson_emitsExpectedStructureForSinglePitchedNote() {
        Track t = new Track(LEAD, TrackKind.PITCHED, List.of(new PitchedNote(0, 500, 60)));
        Performance p = Performance.of(Score.of(t));
        String json = PerformanceJson.toJson(p);

        assertTrue(json.contains("\"type\" : \"PitchedNote\""), "expected type discriminator: \n" + json);
        assertTrue(json.contains("\"midi\" : 60"), "expected midi field: \n" + json);
        assertTrue(json.contains("\"lead\""), "expected bare 'lead' string: \n" + json);
        assertFalse(json.contains("\"name\" : \"lead\""), "TrackId should serialise to bare string, not object");
    }

    @Test
    void roundTrip_emptyPerformance() {
        Performance p = Performance.of(Score.empty());
        Performance back = PerformanceJson.fromJson(PerformanceJson.toJson(p));
        assertEquals(p, back);
    }

    @Test
    void roundTrip_singleTrackWithProgramAndTempo() {
        Track t = new Track(LEAD, TrackKind.PITCHED, List.of(
                new PitchedNote(0, 500, 60),
                new PitchedNote(500, 500, 62),
                new PitchedNote(1000, 500, 64)));
        Performance p = new Performance(
                new Score(List.of(t)),
                new TempoTrack(List.of(new TempoChange(0, 120), new TempoChange(2000, 90))),
                Instrumentation.single(LEAD, 24),
                Articulations.empty());
        Performance back = PerformanceJson.fromJson(PerformanceJson.toJson(p));
        assertEquals(p, back);
    }

    @Test
    void roundTrip_multiTrack_withDrumTrack() {
        Track lead = new Track(LEAD, TrackKind.PITCHED, List.of(new PitchedNote(0, 500, 60)));
        Track bass = new Track(BASS, TrackKind.PITCHED, List.of(new PitchedNote(0, 1000, 36)));
        Track drums = new Track(DRUMS, TrackKind.DRUM, List.of(
                new DrumNote(0, 100, Drums.KICK),
                new DrumNote(500, 100, Drums.SNARE)));
        Performance p = new Performance(
                new Score(List.of(lead, bass, drums)),
                TempoTrack.empty(),
                new Instrumentation(Map.of(LEAD, InstrumentControl.constant(40),
                        BASS, InstrumentControl.constant(33))),
                Articulations.empty());
        Performance back = PerformanceJson.fromJson(PerformanceJson.toJson(p));
        assertEquals(p, back);
    }

    @Test
    void roundTrip_canonShapedPerformance() {
        TrackId v1 = new TrackId("voice1");
        TrackId v2 = new TrackId("voice2");
        TrackId v3 = new TrackId("voice3");
        Track t1 = new Track(v1, TrackKind.PITCHED, List.of(new PitchedNote(0, 500, 60), new PitchedNote(500, 500, 62)));
        Track t2 = new Track(v2, TrackKind.PITCHED, List.of(new PitchedNote(0, 500, 64), new PitchedNote(500, 500, 65)));
        Track t3 = new Track(v3, TrackKind.PITCHED, List.of(new PitchedNote(0, 500, 67), new PitchedNote(500, 500, 69)));
        Performance p = new Performance(
                new Score(List.of(t1, t2, t3)),
                TempoTrack.constant(100),
                new Instrumentation(Map.of(
                        v1, InstrumentControl.constant(0),
                        v2, InstrumentControl.constant(40),
                        v3, InstrumentControl.constant(42))),
                Articulations.empty());
        Performance back = PerformanceJson.fromJson(PerformanceJson.toJson(p));
        assertEquals(p, back);
    }

    @Test
    void roundTrip_followingMidiCodec() {
        Track t = new Track(LEAD, TrackKind.PITCHED, List.of(
                new PitchedNote(0, 500, 60),
                new PitchedNote(500, 500, 64),
                new PitchedNote(1000, 500, 67)));
        Performance original = new Performance(
                new Score(List.of(t)),
                TempoTrack.constant(120),
                Instrumentation.single(LEAD, 0),
                Articulations.empty());

        // round-trip via MIDI first
        Performance afterMidi = MidiCodec.fromMidi(MidiCodec.toMidi(original));
        // then via JSON
        Performance afterJson = PerformanceJson.fromJson(PerformanceJson.toJson(afterMidi));
        assertEquals(afterMidi, afterJson);
    }
}
