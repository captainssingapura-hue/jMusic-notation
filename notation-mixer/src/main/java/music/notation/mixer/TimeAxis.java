package music.notation.mixer;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;

/**
 * Observable mapping between musical time (microseconds) and screen
 * pixels, shared across every waveform lane in the mixer view.
 *
 * <h2>Why a shared axis</h2>
 *
 * <p>Alignment verification is only meaningful when every lane uses the
 * same time-to-pixel transformation. With one {@code TimeAxis} instance
 * referenced by all lanes, a downbeat at t = 1.234 s lives at the same
 * x-pixel in every track. Visual misalignment becomes a real signal,
 * not a rendering artefact.</p>
 *
 * <h2>What it does</h2>
 *
 * <ul>
 *   <li>Holds the visible time window: {@code viewportStartMicros} and
 *       {@code viewportEndMicros}.</li>
 *   <li>Holds the current pixel width of that window, set by the layout.</li>
 *   <li>Maps microseconds ↔ pixels via {@link #microsToPixel(long)} and
 *       {@link #pixelToMicros(double)}.</li>
 *   <li>Exposes JavaFX properties so dependent views re-paint when the
 *       window or width change.</li>
 * </ul>
 *
 * <h2>Zoom and scroll</h2>
 *
 * <p>Zoom is just changing the size of the visible window;
 * scrolling is shifting its start. The mixer's outer controls write to
 * {@link #viewportStartMicrosProperty()} and {@link #viewportEndMicrosProperty()};
 * the views listen and re-paint.</p>
 */
public final class TimeAxis {

    private final DoubleProperty viewportStartMicros = new SimpleDoubleProperty(0.0);
    private final DoubleProperty viewportEndMicros   = new SimpleDoubleProperty(1.0);
    private final DoubleProperty widthPixels         = new SimpleDoubleProperty(1.0);

    public DoubleProperty viewportStartMicrosProperty() { return viewportStartMicros; }
    public DoubleProperty viewportEndMicrosProperty()   { return viewportEndMicros; }
    public DoubleProperty widthPixelsProperty()         { return widthPixels; }

    public double viewportStartMicros() { return viewportStartMicros.get(); }
    public double viewportEndMicros()   { return viewportEndMicros.get(); }
    public double widthPixels()         { return widthPixels.get(); }

    /** Set the visible time window in microseconds. */
    public void setViewport(double startMicros, double endMicros) {
        if (endMicros <= startMicros) {
            throw new IllegalArgumentException(
                    "end must be > start: " + startMicros + " → " + endMicros);
        }
        viewportStartMicros.set(startMicros);
        viewportEndMicros.set(endMicros);
    }

    /** Set the rendered pixel width that the viewport occupies. */
    public void setWidthPixels(double pixels) {
        if (pixels < 0) throw new IllegalArgumentException("pixels must be >= 0: " + pixels);
        widthPixels.set(pixels);
    }

    /** Visible window length in microseconds. */
    public double viewportDurationMicros() {
        return viewportEndMicros.get() - viewportStartMicros.get();
    }

    /** Map a moment (microseconds) into a pixel x. May be outside [0, width]. */
    public double microsToPixel(long micros) {
        double frac = (micros - viewportStartMicros.get()) / viewportDurationMicros();
        return frac * widthPixels.get();
    }

    /** Map a pixel x back to a moment (microseconds). */
    public double pixelToMicros(double pixelX) {
        double frac = pixelX / Math.max(1.0, widthPixels.get());
        return viewportStartMicros.get() + frac * viewportDurationMicros();
    }
}
