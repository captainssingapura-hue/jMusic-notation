package music.notation.transcribe;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Finds and loads the RMVPE weights ({@code rmvpe.onnx}). The weights are
 * not part of the build: download them once (RVC project,
 * {@code huggingface.co/lj1995/VoiceConversionWebUI}, file
 * {@code rmvpe.onnx}, ~345 MB) and point at them with any of, in order:
 *
 * <ol>
 *   <li>the {@code model} field of {@link PitchDetectorSpec.Rmvpe};</li>
 *   <li>system property {@value #PROPERTY};</li>
 *   <li>environment variable {@value #ENV};</li>
 *   <li>the default {@code ~/.notation/models/rmvpe.onnx}.</li>
 * </ol>
 *
 * A loaded session is kept for the life of the JVM — loading takes a
 * second or two and the bench re-runs often. Sessions are thread-safe.
 */
public final class RmvpeModel {

    public static final String PROPERTY = "notation.rmvpe.model";
    public static final String ENV = "NOTATION_RMVPE_MODEL";
    public static final Path DEFAULT_PATH =
            Path.of(System.getProperty("user.home"), ".notation", "models", "rmvpe.onnx");

    private static final Map<Path, OrtSession> SESSIONS = new ConcurrentHashMap<>();

    private RmvpeModel() {}

    /** Where the model is expected, given an optional explicit path; does not check that it exists. */
    public static Path locate(String explicit) {
        if (explicit != null && !explicit.isBlank()) return Path.of(explicit);
        String p = System.getProperty(PROPERTY);
        if (p != null && !p.isBlank()) return Path.of(p);
        String e = System.getenv(ENV);
        if (e != null && !e.isBlank()) return Path.of(e);
        return DEFAULT_PATH;
    }

    /** Whether the model resolved for {@code explicit} is present. */
    public static boolean available(String explicit) {
        return Files.isRegularFile(locate(explicit));
    }

    /** The loaded session for {@code explicit} (see class doc for resolution). */
    public static OrtSession session(String explicit) {
        Path path = locate(explicit).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) throw new ModelNotFoundException(path);
        return SESSIONS.computeIfAbsent(path, RmvpeModel::load);
    }

    private static OrtSession load(Path path) {
        try {
            return OrtEnvironment.getEnvironment().createSession(path.toString(), new OrtSession.SessionOptions());
        } catch (OrtException ex) {
            throw new IllegalStateException("Cannot load RMVPE model " + path + ": " + ex.getMessage(), ex);
        }
    }

    /** The RMVPE weights are not where they were looked for. */
    public static final class ModelNotFoundException extends IllegalStateException {
        private final Path path;

        ModelNotFoundException(Path path) {
            super("RMVPE model not found at " + path + ". Download rmvpe.onnx and set the detector's \"model\", "
                    + "-D" + PROPERTY + "=…, or " + ENV + ", or place it at " + DEFAULT_PATH + ".");
            this.path = path;
        }

        public Path path() { return path; }
    }
}
