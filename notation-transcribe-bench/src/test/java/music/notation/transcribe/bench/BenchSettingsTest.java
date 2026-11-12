package music.notation.transcribe.bench;

import music.notation.duration.Duration;
import music.notation.structure.TimeSignature;
import music.notation.transcribe.PitchDetectorSpec;
import music.notation.transcribe.SegmentationSpec;
import music.notation.transcribe.TimeWindow;
import music.notation.transcribe.TimingSpec;
import music.notation.transcribe.TranscriptionRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BenchSettingsTest {

    @Test
    void controlsRoundTripAndHiddenFieldsSurvive() {
        var detector = new PitchDetectorSpec.Yin(2048, 5, 0.1, 80, 900, 0.01);
        var seg = new SegmentationSpec(40, 70, 60, 60, 25, 12, 150, 0.35, 0.25);
        var base = new TranscriptionRequest("a.wav", new TimeWindow(1, 9), detector, seg,
                new TimingSpec(92, 1.3, new TimeSignature(3, 4), "Eb major", Duration.of(1, 12)), "A");
        var s = BenchSettings.from(base);
        assertEquals(92, s.bpm());
        assertEquals(1.3, s.downbeatSec());
        assertEquals("Eb major", s.key());

        var tweaked = new BenchSettings(100, null, s.timeSignature(), "auto", s.grid(), 2, 0, 80, 70, 0);
        var r = tweaked.apply(base, "b.wav", "B");
        assertEquals("b.wav", r.audio());
        assertEquals("B", r.title());
        assertSame(detector, r.detector(), "detector settings are not on the bench; kept from the request");
        assertEquals(40, r.segmentation().relativeFloorDb());
        assertEquals(0.25, r.segmentation().trimFraction());
        assertEquals(80, r.segmentation().minNoteMs());
        assertEquals(0, r.segmentation().dipRatio());
        assertEquals(new TimeWindow(2, 0), r.window());
        assertNull(r.timing().downbeatSec());
        assertTrue(r.timing().autoKey());
        assertEquals(tweaked, BenchSettings.from(r));
    }

    @Test
    void badValuesAreRejected() {
        var d = BenchSettings.DEFAULT;
        assertThrows(IllegalArgumentException.class, () ->
                new BenchSettings(5, null, d.timeSignature(), "auto", d.grid(), 0, 0, 60, 60, 0.35));
        assertThrows(IllegalArgumentException.class, () ->
                new BenchSettings(120, null, d.timeSignature(), "X minor", d.grid(), 0, 0, 60, 60, 0.35));
        assertThrows(IllegalArgumentException.class, () ->
                new BenchSettings(120, null, d.timeSignature(), "auto", d.grid(), 5, 3, 60, 60, 0.35));
        assertThrows(IllegalArgumentException.class, () ->
                new BenchSettings(120, null, d.timeSignature(), "auto", d.grid(), 0, 0, 60, 60, 1.5));
    }

    @Test
    void barClockPlacesBarsInFileTime() {
        // ♩ = 120 in 4/4: a bar is 2 s; downbeat at 3 s with one pickup bar → bar 1 starts at 1 s.
        var c = BarClock.of(new TimingSpec(120), 3.0, 1);
        assertEquals(1.0, c.barOneSec(), 1e-12);
        assertEquals(2.0, c.barSec(), 1e-12);
        assertEquals(0.5, c.beatSec(), 1e-12);
        assertEquals(3.0, c.barStartSec(2), 1e-12);
        assertEquals(3.75, c.fileSec(Duration.of(11, 8)), 1e-12);         // bar 2, beat 2½
        var sixEight = BarClock.of(new TimingSpec(120, null, new TimeSignature(6, 8), null, Duration.of(1, 8)), 0, 0);
        assertEquals(1.5, sixEight.barSec(), 1e-12);
        assertEquals(0.25, sixEight.beatSec(), 1e-12);
    }
}
