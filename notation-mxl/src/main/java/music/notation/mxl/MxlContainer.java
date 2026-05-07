package music.notation.mxl;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Resolves the root MusicXML document inside a compressed {@code .mxl} container.
 *
 * <p>An {@code .mxl} is a ZIP archive whose {@code META-INF/container.xml} declares
 * one or more {@code <rootfile>} entries; the first rootfile is the canonical
 * score document. See the W3C MusicXML container spec.</p>
 *
 * <p>This class is intentionally narrow: it extracts entries to memory and finds
 * the root XML — nothing more. Parsing the MusicXML itself lives in
 * {@link MusicXmlParser}.</p>
 */
public final class MxlContainer {

    private final Map<String, byte[]> entries;
    private final String rootPath;

    private MxlContainer(Map<String, byte[]> entries, String rootPath) {
        this.entries = entries;
        this.rootPath = rootPath;
    }

    /** Open an {@code .mxl} archive from raw bytes and resolve its root score path. */
    public static MxlContainer open(byte[] mxlBytes) {
        Map<String, byte[]> entries = readZip(mxlBytes);
        byte[] containerXml = entries.get("META-INF/container.xml");
        if (containerXml == null) {
            throw new IllegalArgumentException(
                    "not a valid .mxl: META-INF/container.xml missing");
        }
        String root = parseRootfilePath(containerXml);
        if (!entries.containsKey(root)) {
            throw new IllegalArgumentException(
                    "container.xml points to missing rootfile: " + root);
        }
        return new MxlContainer(entries, root);
    }

    /** Path of the root MusicXML document inside the archive (e.g. {@code score.xml}). */
    public String rootPath() { return rootPath; }

    /** The decompressed root MusicXML document as a UTF-8 string. */
    public String rootXml() {
        return new String(entries.get(rootPath), StandardCharsets.UTF_8);
    }

    /** All non-directory entries in the archive, keyed by archive-relative path. */
    public Map<String, byte[]> entries() { return entries; }

    private static Map<String, byte[]> readZip(byte[] bytes) {
        Map<String, byte[]> out = new HashMap<>();
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                out.put(e.getName(), zin.readAllBytes());
                zin.closeEntry();
            }
        } catch (IOException ioe) {
            throw new IllegalArgumentException("failed to read .mxl archive", ioe);
        }
        return out;
    }

    private static String parseRootfilePath(byte[] containerXml) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(new ByteArrayInputStream(containerXml));
            NodeList rootfiles = doc.getElementsByTagNameNS("*", "rootfile");
            if (rootfiles.getLength() == 0) {
                throw new IllegalArgumentException(
                        "container.xml has no <rootfile> element");
            }
            Element first = (Element) rootfiles.item(0);
            String path = first.getAttribute("full-path");
            if (path == null || path.isBlank()) {
                throw new IllegalArgumentException(
                        "container.xml <rootfile> missing full-path attribute");
            }
            return path;
        } catch (ParserConfigurationException | org.xml.sax.SAXException | IOException ex) {
            throw new IllegalArgumentException("failed to parse container.xml", ex);
        }
    }
}
