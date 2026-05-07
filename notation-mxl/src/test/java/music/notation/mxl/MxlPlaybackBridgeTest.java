package music.notation.mxl;

import music.notation.performance.MidiCodec;
import org.junit.jupiter.api.Test;

import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the Performance → MIDI bytes → {@link Sequence} bridge — the data
 * path {@link MxlPlay} drives in a CLI. No audio output here; this confirms
 * the encode/decode round-trip without depending on a working host
 * synthesizer (so it runs in headless CI).
 */
class MxlPlaybackBridgeTest {

    @Test
    void chopinPerformanceEncodesIntoLoadableMidiSequence() throws Exception {
        byte[] mxlBytes;
        try (InputStream in = MxlPlaybackBridgeTest.class
                .getResourceAsStream("/Chopin_Nocturne_Op9_No1.mxl")) {
            assertNotNull(in);
            mxlBytes = in.readAllBytes();
        }

        MxlImport imp = MxlReader.read(mxlBytes, "Chopin");
        byte[] midi = MidiCodec.toMidi(imp.performance());
        assertTrue(midi.length > 0, "MIDI bytes should be non-empty");

        Sequence sequence = MidiSystem.getSequence(new ByteArrayInputStream(midi));
        assertTrue(sequence.getTickLength() > 0,
                "MIDI sequence should have non-zero tick length");
        assertTrue(sequence.getMicrosecondLength() > 0,
                "MIDI sequence should have non-zero duration");
        assertTrue(sequence.getTracks().length >= 1,
                "MIDI sequence should have at least one track");
    }
}
