package music.notation.launcher;

import java.lang.reflect.Method;

/**
 * Non-JavaFX entry point used by {@link LauncherApp} to spawn each
 * sub-app in a fresh JVM. Same purpose as {@link Launcher} (avoid the
 * "main class extends Application" check that JavaFX runs on jar
 * Main-Class lookup) — but generic: takes the target {@code Application}
 * subclass's fully-qualified name as its first arg, then invokes its
 * {@code main} via reflection.
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 *   java -cp launcher-uber.jar  music.notation.launcher.SubAppRunner  music.notation.recorder.RecorderApp
 *   java -cp launcher-uber.jar  music.notation.launcher.SubAppRunner  music.notation.mixer.MixerApp
 *   java -cp launcher-uber.jar  music.notation.launcher.SubAppRunner  music.notation.ui.NotationApp
 * }</pre>
 *
 * <p>From the launcher's perspective, this means we don't have to write
 * a per-app non-Application wrapper class for every tile — one shared
 * runner handles them all. From a sub-app's perspective, nothing
 * changes: its own {@code main} runs and calls {@code Application.launch}
 * exactly as it would standalone.</p>
 *
 * <h2>Argument forwarding</h2>
 *
 * <p>Any additional command-line args after the target class name are
 * passed through to the target's main. So:</p>
 *
 * <pre>{@code
 *   java -cp uber.jar SubAppRunner music.notation.recorder.RecorderApp --foo bar
 *   // → RecorderApp.main(new String[] {"--foo", "bar"})
 * }</pre>
 */
public final class SubAppRunner {

    private SubAppRunner() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: " + SubAppRunner.class.getName()
                    + " <target-main-class> [args...]");
            System.exit(1);
        }
        String targetClassName = args[0];
        String[] forward = new String[args.length - 1];
        System.arraycopy(args, 1, forward, 0, forward.length);

        Class<?> targetClass;
        try {
            targetClass = Class.forName(targetClassName);
        } catch (ClassNotFoundException e) {
            System.err.println("SubAppRunner: target class not on classpath: " + targetClassName);
            System.exit(2);
            return;
        }
        Method mainMethod;
        try {
            mainMethod = targetClass.getDeclaredMethod("main", String[].class);
        } catch (NoSuchMethodException e) {
            System.err.println("SubAppRunner: " + targetClassName + " has no main(String[]) method");
            System.exit(3);
            return;
        }
        mainMethod.invoke(null, (Object) forward);
    }
}
