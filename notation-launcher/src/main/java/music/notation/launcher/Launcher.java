package music.notation.launcher;

/**
 * Non-JavaFX entry point for the shaded uber-jar — the Main-Class set
 * in the jar's manifest by maven-shade-plugin.
 *
 * <p>JavaFX refuses to launch if the jar's main class extends
 * {@code javafx.application.Application} and the module system is not
 * set up (i.e. running from the classpath instead of the module path,
 * as is the case for a fat jar). This thin wrapper sidesteps that check
 * by being non-Application; it then delegates to
 * {@link LauncherApp#main(String[])} which calls
 * {@code Application.launch} in the normal way.</p>
 *
 * <p>Mirrors {@code music.notation.ui.Launcher} for {@code NotationApp}.</p>
 */
public final class Launcher {

    private Launcher() {}

    public static void main(final String[] args) {
        LauncherApp.main(args);
    }
}
