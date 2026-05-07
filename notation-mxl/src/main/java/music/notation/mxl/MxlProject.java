package music.notation.mxl;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * Folder-aware coordinator for MXL imports. Owns all disk side-effects so
 * that {@link MxlReader} stays pure.
 *
 * <p>A "project folder" is a user-chosen directory holding subfolders by
 * purpose:</p>
 * <pre>
 *   &lt;project&gt;/
 *     mxl/    - source .mxl files (populated when an import copies a file in)
 *     xml/    - decompressed MusicXML, written as MXL_&lt;basename&gt;.xml
 *     json/   - concrete-notes Performance JSON, written as MXL_&lt;basename&gt;.json
 * </pre>
 *
 * <p>The default project folder is inferred from the picked .mxl's parent
 * directory ({@link #inferFrom}); the user may override with
 * {@link #at(Path)}. When the source .mxl lives outside the project folder,
 * {@link #importMxl(Path)} first copies it into {@code &lt;project&gt;/mxl/}.</p>
 */
public final class MxlProject {

    /** Sidecar filename prefix marking files derived from MXL extraction. */
    public static final String SIDECAR_PREFIX = "MXL_";

    private final Path projectFolder;

    private MxlProject(Path projectFolder) {
        this.projectFolder = Objects.requireNonNull(projectFolder, "projectFolder");
    }

    /** Default: project folder is the parent directory of the picked .mxl file. */
    public static MxlProject inferFrom(Path mxlFile) {
        Path parent = mxlFile.toAbsolutePath().getParent();
        if (parent == null) {
            throw new IllegalArgumentException(
                    "cannot infer project folder from path with no parent: " + mxlFile);
        }
        return new MxlProject(parent);
    }

    /** User-chosen project folder. Created if it does not exist. */
    public static MxlProject at(Path projectFolder) {
        return new MxlProject(projectFolder.toAbsolutePath());
    }

    public Path projectFolder() { return projectFolder; }
    public Path mxlDir()  { return projectFolder.resolve("mxl"); }
    public Path xmlDir()  { return projectFolder.resolve("xml"); }
    public Path jsonDir() { return projectFolder.resolve("json"); }

    /**
     * Decompress {@code mxlFile} into the project's {@code xml/} folder and,
     * if the source lives outside the project, mirror a copy into {@code mxl/}.
     * Does not invoke the MusicXML parser — useful for inspection and as the
     * extraction primitive {@link #importMxl(Path)} builds on.
     *
     * @return the path of the written {@code MXL_&lt;base&gt;.xml} file
     */
    public Path extractXml(Path mxlFile) {
        Path absSource = mxlFile.toAbsolutePath();
        String base = stripMxlExtension(absSource.getFileName().toString());

        try {
            if (!absSource.startsWith(projectFolder)) {
                Files.createDirectories(mxlDir());
                Files.copy(absSource, mxlDir().resolve(base + ".mxl"),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to copy mxl into project: " + absSource, e);
        }

        byte[] bytes;
        try {
            bytes = Files.readAllBytes(absSource);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read .mxl: " + absSource, e);
        }

        MxlContainer container = MxlContainer.open(bytes);
        Path xmlOut = xmlDir().resolve(SIDECAR_PREFIX + base + ".xml");
        try {
            Files.createDirectories(xmlDir());
            Files.writeString(xmlOut, container.rootXml(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write extracted xml: " + xmlOut, e);
        }
        return xmlOut;
    }

    /**
     * Full import: extract the XML (via {@link #extractXml(Path)}), parse it
     * to a {@link Performance}, and write a per-piece folder of split JSON
     * files under {@code json/MXL_&lt;base&gt;/} (see {@link MxlSplitJsonWriter}
     * for the layout).
     */
    public MxlImport importMxl(Path mxlFile) {
        extractXml(mxlFile);
        String base = stripMxlExtension(
                mxlFile.toAbsolutePath().getFileName().toString());

        MxlImport result = MxlReader.read(mxlFile.toAbsolutePath());

        Path pieceDir = jsonDir().resolve(SIDECAR_PREFIX + base);
        MxlSplitJsonWriter.write(result, pieceDir);

        return result;
    }

    private static String stripMxlExtension(String name) {
        return name.toLowerCase().endsWith(".mxl")
                ? name.substring(0, name.length() - ".mxl".length())
                : name;
    }
}
