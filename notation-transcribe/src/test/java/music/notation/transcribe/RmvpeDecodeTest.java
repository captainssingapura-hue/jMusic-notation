package music.notation.transcribe;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** The parts of the RMVPE detector that do not need the model weights. */
class RmvpeDecodeTest {

    private static float[] bump(double centreBin, double width) {
        float[] s = new float[RmvpeTrackDetector.BINS];
        for (int i = 0; i < s.length; i++) s[i] = (float) Math.exp(-0.5 * Math.pow((i - centreBin) / width, 2));
        return s;
    }

    private static double centsOfBin(double bin) {
        return RmvpeTrackDetector.CENTS_BIN0 + RmvpeTrackDetector.CENTS_PER_BIN * bin;
    }

    @Test
    void decodeIsTheWeightedMeanAroundThePeak() {
        assertEquals(centsOfBin(150), RmvpeTrackDetector.decode(bump(150, 1.5), 0.03), 1e-3);
        // Between two bins: the local average lands between them, not on the argmax.
        assertEquals(centsOfBin(150.4), RmvpeTrackDetector.decode(bump(150.4, 1.5), 0.03), 0.5);
        // A far-away second peak outside the ±4 window does not pull the estimate.
        float[] s = bump(100, 1.5);
        s[300] = 0.5f;
        assertEquals(centsOfBin(100), RmvpeTrackDetector.decode(s, 0.03), 1e-3);
    }

    @Test
    void binScaleMatchesConcertPitch() {
        // A4 = 440 Hz is 1200·log2(44) ≈ 6551.3 cents above 10 Hz → bin ≈ 227.7.
        double bin = (1200 * Math.log(44) / Math.log(2) - RmvpeTrackDetector.CENTS_BIN0) / RmvpeTrackDetector.CENTS_PER_BIN;
        double cents = RmvpeTrackDetector.decode(bump(bin, 1.5), 0.03);
        assertEquals(440.0, 10 * Math.pow(2, cents / 1200), 0.2);
    }

    @Test
    void quietFramesAreUnvoicedAndEdgesAreSafe() {
        float[] low = bump(200, 1.5);
        for (int i = 0; i < low.length; i++) low[i] *= 0.02f;
        assertTrue(Double.isNaN(RmvpeTrackDetector.decode(low, 0.03)));
        assertFalse(Double.isNaN(RmvpeTrackDetector.decode(low, 0.01)));
        assertEquals(centsOfBin(0), RmvpeTrackDetector.decode(bump(0, 0.5), 0.03), 5);
        assertEquals(centsOfBin(359), RmvpeTrackDetector.decode(bump(359, 0.5), 0.03), 5);
    }

    @Test
    void chunksArePaddedToAMultipleOf32ByReflection() {
        float[][] mel = new float[2][40];
        for (int t = 0; t < 40; t++) { mel[0][t] = t; mel[1][t] = 100 + t; }
        float[] p = RmvpeTrackDetector.padToMultiple(mel, 3, 8, 4);         // frames 3..7, padded to 8
        assertArrayEquals(new float[]{3, 4, 5, 6, 7, 6, 5, 4, 103, 104, 105, 106, 107, 106, 105, 104}, p);
        assertEquals(2 * 32, RmvpeTrackDetector.padToMultiple(mel, 0, 32, 32).length, "already a multiple");
        float[] one = RmvpeTrackDetector.padToMultiple(mel, 9, 10, 4);       // one frame: edge repeated
        assertArrayEquals(new float[]{9, 9, 9, 9, 109, 109, 109, 109}, one);
        float[] two = RmvpeTrackDetector.padToMultiple(mel, 0, 2, 8);        // too short to mirror fully
        for (int i = 0; i < 8; i++) assertTrue(two[i] == 0 || two[i] == 1, "frame " + i + " = " + two[i]);
    }

    @Test
    void modelLocation() {
        assertEquals(Path.of("x/rmvpe.onnx"), RmvpeModel.locate("x/rmvpe.onnx"));
        String old = System.getProperty(RmvpeModel.PROPERTY);
        try {
            System.setProperty(RmvpeModel.PROPERTY, "from-property.onnx");
            assertEquals(Path.of("from-property.onnx"), RmvpeModel.locate(null));
            assertEquals(Path.of("explicit.onnx"), RmvpeModel.locate("explicit.onnx"), "explicit path wins");
        } finally {
            if (old == null) System.clearProperty(RmvpeModel.PROPERTY); else System.setProperty(RmvpeModel.PROPERTY, old);
        }
        var ex = assertThrows(RmvpeModel.ModelNotFoundException.class,
                () -> RmvpeModel.session("no/such/dir/rmvpe.onnx"));
        assertTrue(ex.getMessage().contains(RmvpeModel.ENV), ex.getMessage());
        assertTrue(ex.path().endsWith(Path.of("no", "such", "dir", "rmvpe.onnx")));
    }

    @Test
    void specRoundTripsThroughJson() {
        var r = TranscriptionJson.requestFromJson("""
                { "audio": "a.wav", "detector": { "type": "rmvpe" } }
                """);
        var d = assertInstanceOf(PitchDetectorSpec.Rmvpe.class, r.detector());
        assertNull(d.model());
        assertEquals(10.0, d.hopMs(), 1e-12);
        var custom = r.withAudio("b.wav");
        var tuned = new TranscriptionRequest("b.wav", null, new PitchDetectorSpec.Rmvpe("m/rmvpe.onnx", 0.1, 60, 900, 0.001),
                null, null, null);
        String json = TranscriptionJson.toJson(tuned);
        assertTrue(json.contains("\"type\" : \"rmvpe\""), json);
        assertFalse(json.contains("hopMs"), "hop is fixed by the model, not a setting: " + json);
        assertEquals(tuned, TranscriptionJson.requestFromJson(json));
        assertEquals("b.wav", custom.audio());
        assertEquals("RMVPE (threshold 0.1, 60–900 Hz, model rmvpe.onnx)", tuned.detector().describe());
        assertThrows(IllegalArgumentException.class, () -> new PitchDetectorSpec.Rmvpe(null, 0, 50, 1400, 0));
    }
}
