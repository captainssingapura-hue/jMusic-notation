package music.notation.ui;

import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.util.List;
import java.util.function.DoubleConsumer;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

/**
 * A pitch-grid canvas that renders the full note sequence scaled to fit
 * the available width. Active notes are highlighted during playback.
 *
 * <p>This component has no knowledge of the music domain model —
 * feed it a {@link PitchScrollData} and a tick source for animation.</p>
 */
final class PitchScroll extends Canvas {

    private static final double NOTE_HEIGHT = 4.0;
    private static final double LANE_HEADER = 18.0;
    private static final double LANE_GAP = 2.0;
    private static final double PADDING_LEFT = 40.0;
    private static final double PADDING_RIGHT = 20.0;
    private static final double DEFAULT_MIN_QUARTER_PX = 4.0;

    private static final Color BG = Color.web("#1e1e2e");
    private static final Color LANE_SEPARATOR = Color.web("#45475a");
    private static final Color GRID_LINE = Color.web("#313244");
    private static final Color CURSOR_COLOR = Color.web("#f5c2e7");
    private static final Color BAR_LINE = Color.web("#585b70");
    private static final Color LABEL_COLOR = Color.web("#a6adc8");
    private static final Color[] TRACK_COLORS = {
            Color.web("#f38ba8"), Color.web("#a6e3a1"), Color.web("#89b4fa"),
            Color.web("#fab387"), Color.web("#cba6f7"), Color.web("#f9e2af"),
            Color.web("#94e2d5"), Color.web("#f2cdcd")
    };

    private final LongConsumer onSeek;
    private final DoubleConsumer onCursorMove;
    private final AnimationTimer timer;

    private PitchScrollData data;
    private LongSupplier tickSource;
    private double minQuarterPx = DEFAULT_MIN_QUARTER_PX;

    PitchScroll(final LongConsumer onSeek, final DoubleConsumer onCursorMove) {
        this.onSeek = onSeek;
        this.onCursorMove = onCursorMove;
        this.timer = new AnimationTimer() {
            @Override
            public void handle(final long now) {
                final long tick = tickSource != null ? tickSource.getAsLong() : 0;
                render(tick);
                onCursorMove.accept(PADDING_LEFT + tick * pixelsPerTick());
            }
        };

        // Click or drag to seek — render immediately for instant visual feedback
        final javafx.event.EventHandler<javafx.scene.input.MouseEvent> seekFromMouse = event -> {
            if (data == null) return;
            final long tick = Math.clamp(
                    (long) ((event.getX() - PADDING_LEFT) / pixelsPerTick()),
                    0, data.totalTicks());
            onSeek.accept(tick);
            render(tick);
        };
        setOnMousePressed(seekFromMouse::handle);
        setOnMouseDragged(seekFromMouse::handle);

        // Re-render when canvas is resized
        widthProperty().addListener((obs, o, n) -> render(currentTick()));
        heightProperty().addListener((obs, o, n) -> render(currentTick()));
    }

    void load(final PitchScrollData data) {
        this.data = data;
        render(0);
    }

    void setMinQuarterPx(final double value) {
        this.minQuarterPx = value;
    }

    /** Minimum canvas width so that a quarter note is at least {@code minQuarterPx} wide. */
    double getMinContentWidth() {
        if (data == null || data.totalTicks() == 0) return 0;
        final double minPpt = minQuarterPx / data.ticksPerQuarter();
        return data.totalTicks() * minPpt + PADDING_LEFT + PADDING_RIGHT;
    }

    void startAnimation(final LongSupplier tickSource) {
        this.tickSource = tickSource;
        timer.start();
    }

    void stopAnimation() {
        timer.stop();
        this.tickSource = null;
    }

    private long currentTick() {
        return tickSource != null ? tickSource.getAsLong() : 0;
    }

    /** Scale factor: pixels per tick so the entire song fits the available width. */
    private double pixelsPerTick() {
        if (data == null || data.totalTicks() == 0) return 1.0;
        return (getWidth() - PADDING_LEFT - PADDING_RIGHT) / data.totalTicks();
    }

    private void render(final long currentTick) {
        if (data == null) return;
        final double w = getWidth();
        final double h = getHeight();
        if (w <= 0 || h <= 0 || data.trackCount() == 0) return;

        final GraphicsContext gc = getGraphicsContext2D();
        gc.setFill(BG);
        gc.fillRect(0, 0, w, h);

        final double ppt = pixelsPerTick();
        final int trackCount = data.trackCount();
        final double laneHeight = (h - (trackCount - 1) * LANE_GAP) / trackCount;
        final int noteRange = data.maxNote() - data.minNote() + 1;
        final List<NoteRect> noteRects = data.noteRects();

        for (int t = 0; t < trackCount; t++) {
            final double laneY = t * (laneHeight + LANE_GAP);
            final Color trackColor = TRACK_COLORS[t % TRACK_COLORS.length];
            final double notePixelHeight = (laneHeight - LANE_HEADER) / noteRange;

            // Pitch grid lines (every octave C)
            gc.setStroke(GRID_LINE);
            gc.setLineWidth(0.5);
            for (int note = data.minNote(); note <= data.maxNote(); note++) {
                if (note % 12 == 0) {
                    final double ny = laneY + LANE_HEADER + (data.maxNote() - note) * notePixelHeight;
                    gc.strokeLine(0, ny, w, ny);
                    gc.setFill(GRID_LINE.brighter());
                    gc.setFont(Font.font(9));
                    gc.fillText("C" + (note / 12 - 1), 2, ny - 1);
                }
            }

            // Bar lines
            final long barTickWidth = data.barTickWidth();
            if (barTickWidth > 0) {
                gc.setStroke(BAR_LINE);
                gc.setLineWidth(0.5);
                for (long tick = 0; tick <= data.totalTicks(); tick += barTickWidth) {
                    final double bx = PADDING_LEFT + tick * ppt;
                    gc.strokeLine(bx, laneY + LANE_HEADER, bx, laneY + laneHeight);
                }
            }

            // Notes
            for (final NoteRect r : noteRects) {
                if (r.trackIndex() != t) continue;

                final double x = PADDING_LEFT + r.startTick() * ppt;
                final double rw = (r.endTick() - r.startTick()) * ppt;
                final double y = laneY + LANE_HEADER + (data.maxNote() - r.midiNote()) * notePixelHeight;

                final boolean active = currentTick >= r.startTick() && currentTick < r.endTick();
                gc.setFill(active ? trackColor : trackColor.deriveColor(0, 1, 1, 0.6));
                gc.fillRoundRect(x, y - NOTE_HEIGHT / 2, Math.max(rw, 2), NOTE_HEIGHT, 2, 2);
            }

            // Lane separator
            if (t > 0) {
                gc.setStroke(LANE_SEPARATOR);
                gc.setLineWidth(1);
                gc.strokeLine(0, laneY - LANE_GAP / 2, w, laneY - LANE_GAP / 2);
            }

            // Track label
            gc.setFill(LABEL_COLOR);
            gc.setFont(Font.font("System", 12));
            gc.fillText(data.trackNames().get(t), 4, laneY + 14);
        }

        // Playback cursor
        final double cursorX = PADDING_LEFT + currentTick * ppt;
        gc.setStroke(CURSOR_COLOR);
        gc.setLineWidth(1.5);
        gc.strokeLine(cursorX, 0, cursorX, h);
    }
}
