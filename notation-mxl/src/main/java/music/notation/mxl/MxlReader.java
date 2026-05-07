package music.notation.mxl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Pure parser entry point: bytes (or path) to {@link MxlImport}, no disk side
 * effects. The folder-aware coordinator with sidecar writes lives in
 * {@link MxlProject}; this class is the in-memory primitive that drives tests
 * and headless flows.
 *
 * <p>Pipeline: {@code byte[] -> MxlContainer (zip + container.xml) -> root XML
 * String -> MusicXmlParser.Result -> MxlImport}.</p>
 */
public final class MxlReader {

    private MxlReader() {}

    /** Read an .mxl from raw bytes. {@code displayName} is surfaced on the result. */
    public static MxlImport read(byte[] mxlBytes, String displayName) {
        MxlContainer container = MxlContainer.open(mxlBytes);
        String xml = container.rootXml();
        MusicXmlParser.Result parsed = MusicXmlParser.parse(xml);
        return new MxlImport(
                displayName,
                parsed.performance(),
                parsed.timeSig(),
                parsed.key(),
                xml,
                parsed.repeatStructure(),
                parsed.transpositions());
    }

    /** Read an .mxl from a filesystem path. The file's base name becomes the display name. */
    public static MxlImport read(Path mxlFile) {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(mxlFile);
        } catch (IOException e) {
            throw new IllegalArgumentException("failed to read " + mxlFile, e);
        }
        String name = mxlFile.getFileName().toString();
        if (name.toLowerCase().endsWith(".mxl")) {
            name = name.substring(0, name.length() - ".mxl".length());
        }
        return read(bytes, name);
    }
}
