package music.notation.transcribe.bench;

import javafx.application.Application;

/**
 * Plain entry point for running the bench from a classpath ({@code java -cp …}):
 * JavaFX refuses to start when the main class itself extends
 * {@link Application} and the JavaFX modules are not on the module path.
 * Arguments are passed through (an audio file or a request JSON).
 */
public final class TranscribeBench {

    private TranscribeBench() {}

    public static void main(String[] args) {
        Application.launch(TranscribeBenchApp.class, args);
    }
}
