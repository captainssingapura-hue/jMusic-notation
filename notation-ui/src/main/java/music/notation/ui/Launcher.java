package music.notation.ui;

/**
 * Non-JavaFX entry point for the uber-jar.
 *
 * <p>JavaFX refuses to launch if the main class extends {@code Application}
 * and the module system is not set up.  This thin wrapper delegates to
 * {@link NotationApp#main(String[])} to sidestep that check.</p>
 */
public final class Launcher {

    private Launcher() {}

    public static void main(final String[] args) {
        NotationApp.main(args);
    }
}
