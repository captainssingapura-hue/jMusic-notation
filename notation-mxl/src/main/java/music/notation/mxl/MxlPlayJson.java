package music.notation.mxl;

import java.nio.file.Path;

/**
 * CLI counterpart to {@link MxlPlay} that loads a previously-extracted piece
 * from its split-JSON folder ({@code json/MXL_<base>/}) and plays it.
 *
 * <p>Usage:</p>
 * <pre>
 * mvn -pl notation-mxl exec:java -Dexec.mainClass=music.notation.mxl.MxlPlayJson \
 *     -Dexec.args="C:/path/to/json/MXL_<base>"
 * </pre>
 */
public final class MxlPlayJson {

    private MxlPlayJson() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("usage: MxlPlayJson <path-to-json-piece-dir>");
            System.exit(2);
        }
        Path pieceDir = Path.of(args[0]);
        System.out.println("Reading split JSON from: " + pieceDir);
        MxlPlay.play(MxlSplitJsonReader.read(pieceDir));
    }
}
