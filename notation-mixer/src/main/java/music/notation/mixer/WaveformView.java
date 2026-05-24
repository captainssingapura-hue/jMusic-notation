package music.notation.mixer;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/**
 * JavaFX canvas component that paints a {@link WaveformData} overview
 * against a shared {@link TimeAxis}. One instance per lane in the mixer.
 *
 * <h2>Painting strategy</h2>
 *
 * <p>For each pixel column visible in the current viewport, find the
 * subset of the overview buckets that fall within that column's time
 * range, then draw a vertical line from the column's min to its max. The
 * overview is already a min/max compression of the source samples, so
 * this is two array lookups per column — very fast.</p>
 *
 * <h2>Visual cues</h2>
 *
 * <ul>
 *   <li>Filled vertical segment per column (the waveform).</li>
 *   <li>A horizontal mid-line for the zero amplitude reference.</li>
 *   <li>A 1-px-thick outline so empty lanes are still visible.</li>
 *   <li>Optional "muted" rendering (greyed out) when {@link #mutedProperty} is true.</li>
 * </ul>
 *
 * <h2>What this is not</h2>
 *
 * <p>This is a read-only overview painter. Audition (play cursor) is
 * provided by the parent component as a separate overlay; gain
 * adjustments don't change the painted shape (they only affect the
 * mixer's output). This separation keeps the view a pure function of
 * (WaveformData, TimeAxis, muted-flag).</p>
 */
public final class WaveformView extends Canvas {

    private final WaveformData data;
    private final TimeAxis axis;
    private final BooleanProperty muted = new SimpleBooleanProperty(false);

    private Color waveformColor = Color.web("#3e5e85");
    private Color mutedColor    = Color.web("#bbbbbb");
    private Color backgroundColor = Color.web("#fffef9");
    private Color midlineColor  = Color.web("#d4cfc0");

    public WaveformView(WaveformData data, TimeAxis axis) {
        this.data = data;
        this.axis = axis;

        // Repaint whenever size or viewport changes.
        widthProperty().addListener((obs, oldV, newV) -> redraw());
        heightProperty().addListener((obs, oldV, newV) -> redraw());
        axis.viewportStartMicrosProperty().addListener((obs, oldV, newV) -> redraw());
        axis.viewportEndMicrosProperty().addListener((obs, oldV, newV) -> redraw());
        muted.addListener((obs, oldV, newV) -> redraw());
    }

    public BooleanProperty mutedProperty() { return muted; }
    public WaveformData data()             { return data; }

    /** Override the waveform stroke colour (useful for per-track tinting). */
    public void setWaveformColor(Color c) {
        this.waveformColor = c;
        redraw();
    }

    @Override
    public boolean isResizable() { return true; }

    @Override
    public double prefWidth(double height)  { return getWidth(); }
    @Override
    public double prefHeight(double width)  { return getHeight(); }
    @Override
    public double minWidth(double height)   { return 0; }
    @Override
    public double minHeight(double width)   { return 0; }
    @Override
    public double maxWidth(double height)   { return Double.MAX_VALUE; }
    @Override
    public double maxHeight(double width)   { return Double.MAX_VALUE; }

    private void redraw() {
        GraphicsContext g = getGraphicsContext2D();
        double w = getWidth();
        double h = getHeight();
        if (w <= 0 || h <= 0) return;

        g.setFill(backgroundColor);
        g.fillRect(0, 0, w, h);

        // Mid-line (zero reference).
        g.setStroke(midlineColor);
        g.setLineWidth(1.0);
        g.strokeLine(0, h / 2.0, w, h / 2.0);

        if (data.bucketCount() == 0) return;

        Color stroke = muted.get() ? mutedColor : waveformColor;
        g.setStroke(stroke);
        g.setLineWidth(1.0);

        // For each pixel column, find the overview buckets covering its
        // time range and draw a vertical line from min(min) to max(max).
        double startMicros = axis.viewportStartMicros();
        double endMicros   = axis.viewportEndMicros();
        double sampleRate  = data.format().getSampleRate();
        double bucketDurationMicros = data.samplesPerBucket() * 1_000_000.0 / sampleRate;
        int bucketCount = data.bucketCount();
        double halfH = h / 2.0;
        double scale = halfH / 32768.0;

        for (int px = 0; px < (int) Math.ceil(w); px++) {
            double col0Micros = startMicros + (px      / w) * (endMicros - startMicros);
            double col1Micros = startMicros + ((px + 1) / w) * (endMicros - startMicros);

            int b0 = (int) Math.max(0, Math.floor(col0Micros / bucketDurationMicros));
            int b1 = (int) Math.min(bucketCount - 1, Math.floor(col1Micros / bucketDurationMicros));
            if (b1 < 0 || b0 >= bucketCount) continue;

            short colMin = Short.MAX_VALUE;
            short colMax = Short.MIN_VALUE;
            for (int b = b0; b <= b1; b++) {
                short bMin = data.min(b);
                short bMax = data.max(b);
                if (bMin < colMin) colMin = bMin;
                if (bMax > colMax) colMax = bMax;
            }
            if (colMin > colMax) continue;   // window past end of audio

            double yMin = halfH - colMax * scale;   // higher sample → smaller y
            double yMax = halfH - colMin * scale;
            // Always draw at least one pixel so quiet sections are visible.
            if (yMax - yMin < 1.0) {
                yMin -= 0.5;
                yMax += 0.5;
            }
            g.strokeLine(px + 0.5, yMin, px + 0.5, yMax);
        }
    }
}
