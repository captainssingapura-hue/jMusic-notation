package music.notation.ui;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Tooltip;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.util.Duration;

import java.util.List;
import java.util.function.LongConsumer;

/**
 * A single-track piano-roll lane. Pure {@link Canvas} renderer scoped
 * to one track's {@link NoteRect}s. The parent {@link PitchScroll}
 * coordinates layout, scroll, zoom, and the global playhead overlay.
 *
 * <p>Each lane:
 * <ul>
 *   <li>Knows its track name and color.</li>
 *   <li>Holds a snapshot of the piece's {@link PitchScrollData} +
 *       current pixels-per-tick.</li>
 *   <li>Renders bar grid + pitch-grid lines + only its own
 *       {@code NoteRect}s. Does NOT render the playhead.</li>
 *   <li>Handles seek-on-click and per-note hover tooltip locally.</li>
 * </ul>
 */
final class PitchScrollLane extends Canvas {

    private static final double NOTE_HEIGHT = 4.0;
    static final double LANE_HEADER = 6.0;     // top inset within the lane
    static final double LANE_HEIGHT = 80.0;    // visual height per lane
    static final double PADDING_LEFT = 8.0;
    static final double PADDING_RIGHT = 8.0;

    private static final Color BG = Color.web("#1e1e2e");
    private static final Color GRID_LINE = Color.web("#313244");
    private static final Color BAR_LINE = Color.web("#585b70");

    private static final String[] NOTE_NAMES = {
            "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
    };

    private final String trackKey;
    private final Color trackColor;
    private final LongConsumer onSeek;
    private final Tooltip hoverTooltip = new Tooltip();

    private PitchScrollData data;
    private double pixelsPerTick = 1.0;
    private long currentTick;

    PitchScrollLane(String trackKey, Color trackColor, LongConsumer onSeek) {
        this.trackKey = trackKey;
        this.trackColor = trackColor;
        this.onSeek = onSeek;

        hoverTooltip.setShowDelay(Duration.millis(100));
        hoverTooltip.setHideDelay(Duration.ZERO);
        hoverTooltip.setStyle("-fx-font-size: 12; -fx-background-color: #313244; -fx-text-fill: #cdd6f4;");

        var seek = (javafx.event.EventHandler<javafx.scene.input.MouseEvent>) event -> {
            if (data == null || onSeek == null) return;
            long tick = Math.clamp(
                    (long) ((event.getX() - PADDING_LEFT) / pixelsPerTick),
                    0L, data.totalTicks());
            onSeek.accept(tick);
            hoverTooltip.hide();
        };
        setOnMousePressed(seek::handle);
        setOnMouseDragged(seek::handle);

        setOnMouseMoved(event -> {
            NoteRect r = hitTest(event.getX(), event.getY());
            if (r != null) {
                hoverTooltip.setText(formatNoteInfo(r));
                if (hoverTooltip.isShowing()) {
                    hoverTooltip.setX(event.getScreenX() + 12);
                    hoverTooltip.setY(event.getScreenY() + 14);
                } else {
                    hoverTooltip.show(this, event.getScreenX() + 12, event.getScreenY() + 14);
                }
            } else {
                hoverTooltip.hide();
            }
        });
        setOnMouseExited(event -> hoverTooltip.hide());

        widthProperty().addListener((obs, o, n) -> redraw());
        heightProperty().addListener((obs, o, n) -> redraw());
    }

    String trackKey() {
        return trackKey;
    }

    void load(PitchScrollData data, double pixelsPerTick) {
        this.data = data;
        this.pixelsPerTick = pixelsPerTick;
        redraw();
    }

    void setPixelsPerTick(double ppt) {
        this.pixelsPerTick = ppt;
        redraw();
    }

    /** Update the highlighted-note tick for active-note glow during playback. */
    void setCurrentTick(long tick) {
        if (this.currentTick == tick) return;
        this.currentTick = tick;
        redraw();
    }

    /** Compute the desired width (pixels) given the current data + ppt. */
    double computeContentWidth() {
        if (data == null || data.totalTicks() == 0) return 0;
        return PADDING_LEFT + data.totalTicks() * pixelsPerTick + PADDING_RIGHT;
    }

    private void redraw() {
        if (data == null) return;
        double w = getWidth();
        double h = getHeight();
        if (w <= 0 || h <= 0) return;

        GraphicsContext gc = getGraphicsContext2D();
        gc.setFill(BG);
        gc.fillRect(0, 0, w, h);

        int noteRange = data.maxNote() - data.minNote() + 1;
        if (noteRange <= 0) return;

        double notePixelHeight = (h - LANE_HEADER) / noteRange;

        // Pitch grid lines (every octave C)
        gc.setStroke(GRID_LINE);
        gc.setLineWidth(0.5);
        gc.setFont(Font.font(9));
        for (int note = data.minNote(); note <= data.maxNote(); note++) {
            if (note % 12 == 0) {
                double ny = LANE_HEADER + (data.maxNote() - note) * notePixelHeight;
                gc.strokeLine(0, ny, w, ny);
                gc.setFill(GRID_LINE.brighter());
                gc.fillText("C" + (note / 12 - 1), 2, ny - 1);
            }
        }

        // Bar lines from absolute tick 0 (pickup occupies bar 0).
        long barTickWidth = data.barTickWidth();
        if (barTickWidth > 0) {
            gc.setStroke(BAR_LINE);
            gc.setLineWidth(0.5);
            for (long tick = 0; tick <= data.totalTicks(); tick += barTickWidth) {
                double bx = PADDING_LEFT + tick * pixelsPerTick;
                gc.strokeLine(bx, 0, bx, h);
            }
        }

        // Notes for this track only.
        Color auxColor = trackColor.deriveColor(30, 0.55, 1.15, 1.0);
        for (NoteRect r : data.noteRects()) {
            if (!r.trackKey().equals(trackKey)) continue;
            double x = PADDING_LEFT + r.startTick() * pixelsPerTick;
            double rw = (r.endTick() - r.startTick()) * pixelsPerTick;
            double y = LANE_HEADER + (data.maxNote() - r.midiNote()) * notePixelHeight;

            boolean active = currentTick >= r.startTick() && currentTick < r.endTick();
            Color base = r.isAux() ? auxColor : trackColor;
            gc.setFill(active ? base : base.deriveColor(0, 1, 1, 0.6));
            gc.fillRoundRect(x, y - NOTE_HEIGHT / 2, Math.max(rw, 2), NOTE_HEIGHT, 2, 2);
        }
    }

    private NoteRect hitTest(double x, double y) {
        if (data == null) return null;
        if (pixelsPerTick <= 0) return null;
        double h = getHeight();
        int noteRange = data.maxNote() - data.minNote() + 1;
        if (noteRange <= 0) return null;
        double notePixelHeight = (h - LANE_HEADER) / noteRange;
        double tolerance = NOTE_HEIGHT;

        NoteRect best = null;
        double bestDy = Double.POSITIVE_INFINITY;
        for (NoteRect r : data.noteRects()) {
            if (!r.trackKey().equals(trackKey)) continue;
            double rx = PADDING_LEFT + r.startTick() * pixelsPerTick;
            double rw = Math.max((r.endTick() - r.startTick()) * pixelsPerTick, 2);
            if (x < rx || x > rx + rw) continue;
            double ry = LANE_HEADER + (data.maxNote() - r.midiNote()) * notePixelHeight;
            double dy = Math.abs(y - ry);
            if (dy <= tolerance && dy < bestDy) {
                best = r;
                bestDy = dy;
            }
        }
        return best;
    }

    private String formatNoteInfo(NoteRect r) {
        StringBuilder sb = new StringBuilder();
        sb.append(noteName(r.midiNote()));
        if (data != null && data.barTickWidth() > 0 && data.ticksPerQuarter() > 0) {
            long barWidth = data.barTickWidth();
            boolean hasPickup = data.pickupOffsetTicks() > 0;
            long rawBar = r.startTick() / barWidth;
            long tickInBar = r.startTick() % barWidth;
            long beat = tickInBar / data.ticksPerQuarter() + 1;
            sb.append("  ·  ");
            if (hasPickup && rawBar == 0) {
                sb.append("pickup beat ").append(beat);
            } else {
                long barIdx = hasPickup ? rawBar : rawBar + 1;
                sb.append("bar ").append(barIdx).append(" beat ").append(beat);
            }
        }
        sb.append("  ·  ").append(r.trackKey());
        if (r.isAux()) sb.append(" (voice ").append(r.voice()).append(')');
        return sb.toString();
    }

    private static String noteName(int midi) {
        int octave = midi / 12 - 1;
        return NOTE_NAMES[((midi % 12) + 12) % 12] + octave;
    }
}
