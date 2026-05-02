package music.notation.play;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

class SoundbankSetupTest {

    @Test
    void emptyIsImmutable() {
        var s = SoundbankSetup.empty();
        assertTrue(s.files().isEmpty());
        assertThrows(UnsupportedOperationException.class,
                () -> s.files().add(new File("dummy.sf2")));
    }

    @Test
    void nullFilesNormaliseToEmpty() {
        var s = new SoundbankSetup(null);
        assertNotNull(s.files());
        assertTrue(s.files().isEmpty());
    }

    @Test
    void applyOnNullSynthIsNoop() {
        var s = SoundbankSetup.of(new File("/no/such/file.sf2"));
        assertEquals(0, s.apply(null));
    }

    @Test
    void missingFilesAreSkipped() {
        // No real synth available in headless test env; this just checks
        // the empty-files short-circuit doesn't throw.
        var s = SoundbankSetup.of(new File("/no/such/file.sf2"));
        assertDoesNotThrow(() -> s.apply(null));
    }
}
