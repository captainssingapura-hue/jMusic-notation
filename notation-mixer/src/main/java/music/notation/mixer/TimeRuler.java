package music.notation.mixer;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

/**
 * Horizontal time ruler painted above the stack of {@link TrackRow}s.
 * Shows second-major / 250-ms-minor tick marks across the shared
 * {@link TimeAxis}.
 *
 * <p>Major tick interval auto-scales by viewport duration so the ruler
 * stays legible whether the user is looking at a 5-second slice or a
 * 5-minute one.</p>
 */
public final class TimeRuler extends Canvas {

    private static final double RULER_HEIGHT = 28;
    private static final Color TICK_COLOR  = Color.web("#5a554c");
    private static final Color LABEL_COLOR = Color.web("#3a3a3a");
    private static final Color MINOR_COLOR = Color.web("#bbb");
    private static final Color BG_COLOR    = Color.web("#f4f1e8");

    private final TimeAxis axis;

    public TimeRuler(TimeAxis axis) {
        this.axis = axis;
        setHeight(RULER_HEIGHT);
        widthProperty().addListener((obs, oldV, newV) -> redraw());
        axis.viewportStartMicrosProperty().addListener((obs, oldV, newV) -> redraw());
        axis.viewportEndMicrosProperty().addListener((obs, oldV, newV) -> redraw());
    }

    @Override public boolean isResizable()      { return true; }
    @Override public double prefWidth(double h) { return getWidth(); }
    @Override public double prefHeight(double w){ return RULER_HEIGHT; }
    @Override public double minHeight(double w) { return RULER_HEIGHT; }
    @Override public double maxHeight(double w) { return RULER_HEIGHT; }

    private void redraw() {
        GraphicsContext g = getGraphicsContext2D();
        double w = getWidth();
        if (w <= 0) return;

        g.setFill(BG_COLOR);
        g.fillRect(0, 0, w, RULER_HEIGHT);

        double startMicros = axis.viewportStartMicros();
        double endMicros   = axis.viewportEndMicros();
        double durMicros   = endMicros - startMicros;
        if (durMicros <= 0) return;

        // Choose major tick interval: try 1 s, 5 s, 10 s, 30 s ... so ~10 majors fit.
        double targetMajors = 10.0;
        double rawInterval = durMicros / targetMajors;
        double majorInterval = chooseInterval(rawInterval);
        double minorInterval = majorInterval / 4.0;

        g.setFont(Font.font("System", 10));

        // Minor ticks
        g.setStroke(MINOR_COLOR);
        g.setLineWidth(0.7);
        double firstMinor = Math.ceil(startMicros / minorInterval) * minorInterval;
        for (double t = firstMinor; t < endMicros; t += minorInterval) {
            double x = (t - startMicros) / durMicros * w;
            g.strokeLine(x, RULER_HEIGHT - 6, x, RULER_HEIGHT);
        }

        // Major ticks + labels
        g.setStroke(TICK_COLOR);
        g.setLineWidth(1.0);
        g.setFill(LABEL_COLOR);
        double firstMajor = Math.ceil(startMicros / majorInterval) * majorInterval;
        for (double t = firstMajor; t < endMicros; t += majorInterval) {
            double x = (t - startMicros) / durMicros * w;
            g.strokeLine(x, RULER_HEIGHT - 12, x, RULER_HEIGHT);
            g.fillText(formatTime(t), x + 3, RULER_HEIGHT - 14);
        }

        // Bottom border
        g.setStroke(Color.web("#d4cfc0"));
        g.setLineWidth(1.0);
        g.strokeLine(0, RULER_HEIGHT - 0.5, w, RULER_HEIGHT - 0.5);
    }

    private static double chooseInterval(double rawMicros) {
        // Pick a "nice" number from 1, 2, 5 × 10ⁿ µs covering rawMicros.
        if (rawMicros <= 0) return 1_000_000;
        double pow = Math.pow(10, Math.floor(Math.log10(rawMicros)));
        double normalised = rawMicros / pow;
        double nice;
        if      (normalised < 1.5) nice = 1;
        else if (normalised < 3.5) nice = 2;
        else if (normalised < 7.5) nice = 5;
        else                       nice = 10;
        return nice * pow;
    }

    private static String formatTime(double micros) {
        long totalMillis = Math.round(micros / 1000.0);
        long s = totalMillis / 1000;
        long ms = totalMillis % 1000;
        long m = s / 60;
        s = s % 60;
        if (m > 0) {
            return String.format("%d:%02d", m, s);
        }
        if (ms == 0) {
            return s + "s";
        }
        return String.format("%.3fs", micros / 1_000_000.0);
    }
}
