package music.notation.input;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the hardware-independent parts of {@link MicAudioCapture}:
 * the 16-bit-PCM-to-float decoder and the constructor validation. The
 * actual capture loop is exercised only when a microphone is available
 * (manual / integration testing).
 */
class MicAudioCaptureTest {

    @Test
    void decodeMidpointIsZero() {
        // 16-bit signed: 0x0000 = 0.0, 0x8000 = -1.0, 0x7FFF ≈ +0.9999.
        byte[] src = { 0x00, 0x00 };
        float[] dst = new float[1];
        MicAudioCapture.decodeInt16ToFloat(src, 0, 1, dst, 0);
        assertEquals(0.0f, dst[0], 1e-6f);
    }

    @Test
    void decodePositivePeak() {
        // 0x7FFF little-endian = bytes [0xFF, 0x7F] → +32767 / 32768 ≈ +1.0
        byte[] src = { (byte) 0xFF, (byte) 0x7F };
        float[] dst = new float[1];
        MicAudioCapture.decodeInt16ToFloat(src, 0, 1, dst, 0);
        assertEquals(0.9999695f, dst[0], 1e-5f);
    }

    @Test
    void decodeNegativePeak() {
        // 0x8000 little-endian = bytes [0x00, 0x80] → -32768 / 32768 = -1.0
        byte[] src = { 0x00, (byte) 0x80 };
        float[] dst = new float[1];
        MicAudioCapture.decodeInt16ToFloat(src, 0, 1, dst, 0);
        assertEquals(-1.0f, dst[0], 1e-6f);
    }

    @Test
    void decodeMultipleSamplesAtOffset() {
        // Pack three samples at +1000, -2000, +500. Decode into dst at offset 3.
        byte[] src = new byte[6];
        writeLE16(src, 0,  1000);
        writeLE16(src, 2, -2000);
        writeLE16(src, 4,   500);
        float[] dst = new float[10];
        MicAudioCapture.decodeInt16ToFloat(src, 0, 3, dst, 3);

        assertEquals(0f,             dst[0],  1e-6f);   // untouched
        assertEquals(0f,             dst[2],  1e-6f);
        assertEquals( 1000 / 32768f, dst[3],  1e-6f);
        assertEquals(-2000 / 32768f, dst[4],  1e-6f);
        assertEquals(  500 / 32768f, dst[5],  1e-6f);
        assertEquals(0f,             dst[6],  1e-6f);   // untouched
    }

    @Test
    void rejectsBadConstructorArgs() {
        assertThrows(IllegalArgumentException.class,
                () -> new MicAudioCapture(0, 2048, 1024, null, w -> {}));
        assertThrows(IllegalArgumentException.class,
                () -> new MicAudioCapture(44100, 0, 1024, null, w -> {}));
        assertThrows(IllegalArgumentException.class,
                () -> new MicAudioCapture(44100, 2048, 0, null, w -> {}));
        // hopSize > windowSize
        assertThrows(IllegalArgumentException.class,
                () -> new MicAudioCapture(44100, 2048, 4096, null, w -> {}));
        // null consumer
        assertThrows(IllegalArgumentException.class,
                () -> new MicAudioCapture(44100, 2048, 1024, null, null));
    }

    @Test
    void listInputDevices_doesNotThrow() {
        // We can't assert on what's available (CI environment vs dev box)
        // but the discovery call must not crash on any sane system.
        assertDoesNotThrow(MicAudioCapture::listInputDevices);
    }

    private static void writeLE16(byte[] buf, int off, int value) {
        buf[off]     = (byte) ( value       & 0xFF);
        buf[off + 1] = (byte) ((value >> 8) & 0xFF);
    }
}
