package music.notation.transcribe;

import music.notation.duration.Duration;
import music.notation.structure.TimeSignature;
import org.junit.jupiter.api.Test;

import java.io.UncheckedIOException;

import static org.junit.jupiter.api.Assertions.*;

class TranscriptionJsonTest {

    @Test
    void minimalRequestGetsDefaults() {
        var r = TranscriptionJson.requestFromJson("""
                { "audio": "takes/take 3.wav" }
                """);
        assertEquals("takes/take 3.wav", r.audio());
        assertEquals("take 3", r.title());
        assertEquals(TimeWindow.WHOLE, r.window());
        assertEquals(PitchDetectorSpec.Yin.DEFAULT, r.detector());
        assertEquals(SegmentationSpec.DEFAULT, r.segmentation());
        assertEquals(TimingSpec.DEFAULT, r.timing());
        assertEquals(new TimeSignature(4, 4), r.timing().timeSignature());
        assertTrue(r.timing().autoKey());
        assertTrue(r.timing().grid().equalsDuration(Duration.of(1, 16)));
    }

    @Test
    void partialTimingKeepsOtherDefaults() {
        var r = TranscriptionJson.requestFromJson("""
                { "audio": "a.wav", "timing": { "bpm": 92, "key": "Eb minor", "grid": "1/12" } }
                """);
        assertEquals(92, r.timing().bpm());
        assertNull(r.timing().downbeatSec());
        assertEquals("Eb minor", Keys.name(r.timing().fixedKey()));
        assertEquals(12, r.timing().stepsPerBar());
    }

    @Test
    void fullRequestRoundTrips() {
        var r = TranscribeCli.exampleRequest();
        String json = TranscriptionJson.toJson(r);
        assertTrue(json.contains("\"type\" : \"yin\""), json);
        assertTrue(json.contains("\"grid\" : \"1/16\""), json);
        assertEquals(r, TranscriptionJson.requestFromJson(json));
    }

    @Test
    void mistakesAreReported() {
        var unknown = assertThrows(UncheckedIOException.class, () -> TranscriptionJson.requestFromJson("""
                { "audio": "a.wav", "timming": { "bpm": 90 } }
                """));
        assertTrue(unknown.getMessage().contains("timming"), unknown.getMessage());
        assertThrows(UncheckedIOException.class, () -> TranscriptionJson.requestFromJson("{ }"));
        assertThrows(UncheckedIOException.class, () -> TranscriptionJson.requestFromJson("""
                { "audio": "a.wav", "timing": { "bpm": 90, "key": "Q major" } }
                """));
        assertThrows(UncheckedIOException.class, () -> TranscriptionJson.requestFromJson("""
                { "audio": "a.wav", "window": { "startSec": 5, "endSec": 2 } }
                """));
        assertThrows(UncheckedIOException.class, () -> TranscriptionJson.requestFromJson("""
                { "audio": "a.wav", "detector": { "type": "yin", "windowSize": 256, "hopMs": 10,
                  "threshold": 0.15, "minHz": 65, "maxHz": 1100, "minRms": 0 } }
                """), "a 256-sample window cannot reach 65 Hz");
    }
}
