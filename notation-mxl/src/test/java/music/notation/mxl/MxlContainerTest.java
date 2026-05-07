package music.notation.mxl;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke tests for the .mxl container layer. The MusicXML parser is still a
 * stub, so these exercise only zip extraction and {@code container.xml}
 * resolution.
 */
class MxlContainerTest {

    private static final String FIXTURE = "/Chopin_Nocturne_Op9_No1.mxl";

    private static byte[] fixtureBytes() throws IOException {
        try (InputStream in = MxlContainerTest.class.getResourceAsStream(FIXTURE)) {
            assertNotNull(in, "test fixture missing on classpath: " + FIXTURE);
            return in.readAllBytes();
        }
    }

    @Test
    void opensCompressedMusicXmlAndResolvesRootfile() throws IOException {
        MxlContainer c = MxlContainer.open(fixtureBytes());

        assertNotNull(c.rootPath(), "rootPath must be resolved from container.xml");
        assertFalse(c.rootPath().isBlank(), "rootPath must not be blank");
        assertTrue(c.entries().containsKey("META-INF/container.xml"),
                "archive should contain META-INF/container.xml");
        assertTrue(c.entries().containsKey(c.rootPath()),
                "archive should contain the rootfile " + c.rootPath());
    }

    @Test
    void rootXmlLooksLikeMusicXml() throws IOException {
        MxlContainer c = MxlContainer.open(fixtureBytes());
        String xml = c.rootXml();

        assertTrue(xml.startsWith("<?xml"), "root XML should start with XML declaration");
        assertTrue(xml.contains("score-partwise") || xml.contains("score-timewise"),
                "root XML should be a MusicXML score document");
    }

    @Test
    void rejectsNonMxlInput() {
        byte[] notAZip = "definitely not a zip file".getBytes();
        assertThrows(IllegalArgumentException.class, () -> MxlContainer.open(notAZip));
    }
}
