package music.notation.ui;

import javafx.animation.AnimationTimer;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.text.Font;
import javafx.util.Duration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.DoubleConsumer;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

/**
 * GarageBand-style coordinator: a frozen left column of per-track
 * control panels, paired with a horizontally-scrolling right column
 * containing a single {@link Canvas} drawing the bar-number ruler at
 * top and all track lanes below it. A global playhead overlay
 * (single {@link Line}) lives in the same scrollable region so it
 * scrolls horizontally with the canvas.
 *
 * <p>Single-canvas approach (post-Phase-5.3): simpler layout, single
 * redraw path, single hit-test/tooltip. Per-track enable/disable is
 * handled by tracking disabled track names and skipping their notes
 * during render.</p>
 */
final class PitchScroll extends HBox {

    // ── Visual constants ──────────────────────────────────────────────
    private static final double RULER_HEIGHT = 20.0;
    private static final double LANE_HEIGHT = 80.0;
    private static final double LANE_GAP = 2.0;
    private static final double LANE_HEADER = 4.0;     // top inset within a lane
    private static final double NOTE_HEIGHT = 4.0;
    private static final double PADDING_LEFT = 8.0;
    private static final double PADDING_RIGHT = 8.0;
    private static final double DEFAULT_MIN_QUARTER_PX = 4.0;
    /** GPU texture-size cap for individual Canvases. */
    private static final double MAX_CANVAS_DIM = 8192.0;

    private static final Color BG = Color.web("#1e1e2e");
    private static final Color RULER_BG = Color.web("#181825");
    private static final Color RULER_TEXT = Color.web("#a6adc8");
    private static final Color LANE_SEPARATOR = Color.web("#45475a");
    private static final Color GRID_LINE = Color.web("#313244");
    private static final Color BAR_LINE = Color.web("#585b70");
    private static final Color CURSOR_COLOR = Color.web("#f5c2e7");
    private static final Color[] TRACK_COLORS = {
            Color.web("#f38ba8"), Color.web("#a6e3a1"), Color.web("#89b4fa"),
            Color.web("#fab387"), Color.web("#cba6f7"), Color.web("#f9e2af"),
            Color.web("#94e2d5"), Color.web("#f2cdcd")
    };

    private static final String[] NOTE_NAMES = {
            "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
    };

    // ── Public callbacks / config ────────────────────────────────────
    @FunctionalInterface
    interface ControlPanelFactory {
        Node create(int trackIndex, String trackName);
    }

    private final LongConsumer onSeek;
    private final DoubleConsumer onCursorMove;
    private LongConsumer onCursorTick;
    private final ControlPanelFactory controlPanelFactory;
    private final double controlPanelWidth;

    // ── Layout components ────────────────────────────────────────────
    private final VBox leftColumn;
    private final Pane rulerSpacer;
    private final VBox controlPanelsBox;
    private final Canvas canvas;
    private final StackPane canvasStack;
    private final Pane playheadOverlay;
    private final Line playheadLine;
    private final ScrollPane laneScrollPane;
    private final Tooltip hoverTooltip = new Tooltip();
    private final AnimationTimer timer;

    // ── State ────────────────────────────────────────────────────────
    private PitchScrollData data;
    private LongSupplier tickSource;
    private double minQuarterPx = DEFAULT_MIN_QUARTER_PX;
    private double pixelsPerTick = 1.0;
    private long currentTick;
    private final Set<String> disabledTracks = new HashSet<>();

    PitchScroll(LongConsumer onSeek,
                DoubleConsumer onCursorMove,
                ControlPanelFactory controlPanelFactory,
                double controlPanelWidth) {
        this.onSeek = onSeek;
        this.onCursorMove = onCursorMove;
        this.controlPanelFactory = controlPanelFactory;
        this.controlPanelWidth = controlPanelWidth;

        setStyle("-fx-background-color: #1e1e2e;");
        setSpacing(0);

        // ── Left column: ruler-height spacer + frozen control panels.
        rulerSpacer = new Pane();
        rulerSpacer.setMinHeight(RULER_HEIGHT);
        rulerSpacer.setPrefHeight(RULER_HEIGHT);
        rulerSpacer.setMaxHeight(RULER_HEIGHT);
        rulerSpacer.setStyle("-fx-background-color: #181825; "
                + "-fx-border-color: transparent transparent #313244 transparent; -fx-border-width: 1;");

        controlPanelsBox = new VBox(LANE_GAP);
        controlPanelsBox.setStyle("-fx-background-color: #181825;");

        leftColumn = new VBox(rulerSpacer, controlPanelsBox);
        leftColumn.setMinWidth(controlPanelWidth);
        leftColumn.setPrefWidth(controlPanelWidth);
        leftColumn.setMaxWidth(controlPanelWidth);
        leftColumn.setStyle("-fx-background-color: #181825;");

        // ── Right column: single Canvas (ruler + all lanes) + playhead overlay.
        canvas = new Canvas(800, RULER_HEIGHT + LANE_HEIGHT);

        playheadLine = new Line();
        playheadLine.setStroke(CURSOR_COLOR);
        playheadLine.setStrokeWidth(1.5);
        playheadLine.setVisible(false);
        playheadOverlay = new Pane(playheadLine);
        playheadOverlay.setMouseTransparent(true);
        playheadOverlay.setPickOnBounds(false);
        playheadOverlay.setStyle("-fx-background-color: transparent;");

        canvasStack = new StackPane(canvas, playheadOverlay);
        canvasStack.setStyle("-fx-background-color: #1e1e2e;");
        canvasStack.setAlignment(javafx.geometry.Pos.TOP_LEFT);

        laneScrollPane = new ScrollPane(canvasStack);
        laneScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        laneScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        laneScrollPane.setStyle(
                "-fx-background: #1e1e2e; -fx-background-color: #1e1e2e;"
                        + " -fx-padding: 0; -fx-border-width: 0;");
        HBox.setHgrow(laneScrollPane, Priority.ALWAYS);

        getChildren().setAll(leftColumn, laneScrollPane);

        // ── Hover tooltip + click-to-seek.
        hoverTooltip.setShowDelay(Duration.millis(100));
        hoverTooltip.setHideDelay(Duration.ZERO);
        hoverTooltip.setStyle("-fx-font-size: 12; -fx-background-color: #313244; -fx-text-fill: #cdd6f4;");

        var seek = (javafx.event.EventHandler<javafx.scene.input.MouseEvent>) event -> {
            if (data == null || event.getY() < RULER_HEIGHT) return;
            long tick = Math.clamp(
                    (long) ((event.getX() - PADDING_LEFT) / Math.max(pixelsPerTick, 1e-9)),
                    0L, data.totalTicks());
            seekTo(tick);
            hoverTooltip.hide();
        };
        canvas.setOnMousePressed(seek::handle);
        canvas.setOnMouseDragged(seek::handle);

        // Ruler-row click also seeks (no Y-restriction).
        canvas.setOnMouseClicked(event -> {
            if (data == null || event.getY() >= RULER_HEIGHT) return;
            long tick = Math.clamp(
                    (long) ((event.getX() - PADDING_LEFT) / Math.max(pixelsPerTick, 1e-9)),
                    0L, data.totalTicks());
            seekTo(tick);
        });

        canvas.setOnMouseMoved(event -> {
            NoteRect r = hitTest(event.getX(), event.getY());
            if (r != null) {
                hoverTooltip.setText(formatNoteInfo(r));
                if (hoverTooltip.isShowing()) {
                    hoverTooltip.setX(event.getScreenX() + 12);
                    hoverTooltip.setY(event.getScreenY() + 14);
                } else {
                    hoverTooltip.show(canvas, event.getScreenX() + 12, event.getScreenY() + 14);
                }
            } else {
                hoverTooltip.hide();
            }
        });
        canvas.setOnMouseExited(event -> hoverTooltip.hide());

        // ── Animation timer drives the playhead + glow during playback.
        this.timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                long tick = tickSource != null ? tickSource.getAsLong() : 0;
                currentTick = tick;
                redraw();
                updatePlayhead(tick);
                if (onCursorMove != null) onCursorMove.accept(playheadLine.getStartX());
                if (onCursorTick != null) onCursorTick.accept(tick);
            }
        };

        laneScrollPane.viewportBoundsProperty().addListener((obs, o, n) -> recomputeSizing());
    }

    // ── Public API ────────────────────────────────────────────────────

    void load(PitchScrollData data) {
        this.data = data;
        rebuildControlPanels();
        recomputeSizing();
        currentTick = 0;
        playheadLine.setVisible(true);
        redraw();
        updatePlayhead(0);
        if (onCursorTick != null) onCursorTick.accept(0);
    }

    /** Programmatic seek — updates the visible cursor and notifies onSeek/onCursorTick. */
    void seekTo(long tick) {
        if (data == null) return;
        long clamped = Math.clamp(tick, 0L, data.totalTicks());
        currentTick = clamped;
        if (onSeek != null) onSeek.accept(clamped);
        redraw();
        updatePlayhead(clamped);
        if (onCursorTick != null) onCursorTick.accept(clamped);
    }

    void setMinQuarterPx(double value) {
        this.minQuarterPx = value;
        recomputeSizing();
        redraw();
    }

    void setOnCursorTick(LongConsumer onCursorTick) {
        this.onCursorTick = onCursorTick;
    }

    void setTrackEnabled(String trackName, boolean enabled) {
        if (enabled) disabledTracks.remove(trackName);
        else disabledTracks.add(trackName);
        redraw();
    }

    double computeContentHeight() {
        if (data == null || data.trackCount() == 0) return 0;
        return data.trackCount() * (LANE_HEIGHT + LANE_GAP);
    }

    /** @deprecated horizontal scrolling is internal; returns 0. */
    @Deprecated
    double getMinContentWidth() { return 0; }

    void hostViewportChanged() {
        recomputeSizing();
    }

    void startAnimation(LongSupplier tickSource) {
        this.tickSource = tickSource;
        playheadLine.setVisible(true);
        timer.start();
    }

    /** Force a visible-cursor refresh (e.g. after host repositions the playhead). */
    void refreshCursor() {
        redraw();
        updatePlayhead(currentTick);
        if (onCursorTick != null) onCursorTick.accept(currentTick);
    }

    void stopAnimation() {
        timer.stop();
        this.tickSource = null;
    }

    long currentTick() {
        return tickSource != null ? tickSource.getAsLong() : 0;
    }

    double getLaneScrollHvalue() {
        return laneScrollPane.getHvalue();
    }

    void setLaneScrollHvalue(double v) {
        laneScrollPane.setHvalue(v);
    }

    double getLaneViewportWidth() {
        var b = laneScrollPane.getViewportBounds();
        return b == null ? 0 : b.getWidth();
    }

    double getLaneContentWidth() {
        return canvas.getWidth();
    }

    // ── Internals ─────────────────────────────────────────────────────

    private void rebuildControlPanels() {
        controlPanelsBox.getChildren().clear();
        if (data == null) return;
        for (int t = 0; t < data.trackCount(); t++) {
            String name = data.trackNames().get(t);
            Node panel = controlPanelFactory != null
                    ? controlPanelFactory.create(t, name)
                    : new Pane();
            if (panel instanceof Region r) {
                r.setMinWidth(controlPanelWidth);
                r.setPrefWidth(controlPanelWidth);
                r.setMaxWidth(controlPanelWidth);
                r.setMinHeight(LANE_HEIGHT);
                r.setPrefHeight(LANE_HEIGHT);
                r.setMaxHeight(LANE_HEIGHT);
            }
            controlPanelsBox.getChildren().add(panel);
        }
    }

    private void recomputeSizing() {
        if (data == null) return;
        double viewportW = getLaneViewportWidth();
        if (viewportW <= 0) viewportW = 800;
        double minPpt = data.totalTicks() == 0 ? 1.0 : minQuarterPx / data.ticksPerQuarter();
        double minLaneW = data.totalTicks() == 0
                ? viewportW
                : data.totalTicks() * minPpt + PADDING_LEFT + PADDING_RIGHT;
        double laneWidth = Math.min(MAX_CANVAS_DIM, Math.max(viewportW, minLaneW));

        double canvasH = RULER_HEIGHT + computeContentHeight();
        canvas.setWidth(laneWidth);
        canvas.setHeight(canvasH);
        canvasStack.setMinWidth(laneWidth);
        canvasStack.setPrefWidth(laneWidth);
        canvasStack.setMinHeight(canvasH);
        canvasStack.setPrefHeight(canvasH);
        playheadOverlay.setPrefWidth(laneWidth);
        playheadOverlay.setPrefHeight(canvasH);

        if (data.totalTicks() > 0) {
            pixelsPerTick = (laneWidth - PADDING_LEFT - PADDING_RIGHT) / data.totalTicks();
        } else {
            pixelsPerTick = 1.0;
        }

        // Match overall PitchScroll height so the host's vertical scroll has correct content size.
        double overallH = canvasH;
        setMinHeight(overallH);
        setPrefHeight(overallH);

        redraw();
        updatePlayhead(currentTick);
    }

    private void redraw() {
        if (data == null) return;
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        if (w <= 0 || h <= 0) return;

        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(BG);
        gc.fillRect(0, 0, w, h);

        // ── Bar-number ruler at top.
        gc.setFill(RULER_BG);
        gc.fillRect(0, 0, w, RULER_HEIGHT);
        long barTickWidth = data.barTickWidth();
        if (barTickWidth > 0) {
            boolean hasPickup = data.pickupOffsetTicks() > 0;
            gc.setFont(Font.font("System", 10));
            gc.setStroke(LANE_SEPARATOR);
            gc.setLineWidth(0.5);
            long bar = 0;
            for (long tick = 0; tick <= data.totalTicks(); tick += barTickWidth, bar++) {
                double bx = PADDING_LEFT + tick * pixelsPerTick;
                gc.strokeLine(bx, RULER_HEIGHT - 4, bx, RULER_HEIGHT);
                String label = (hasPickup && bar == 0)
                        ? "↟"
                        : String.valueOf(hasPickup ? bar : bar + 1);
                gc.setFill(RULER_TEXT);
                gc.fillText(label, bx + 3, RULER_HEIGHT - 6);
            }
        }
        gc.setStroke(GRID_LINE);
        gc.setLineWidth(0.5);
        gc.strokeLine(0, RULER_HEIGHT - 0.5, w, RULER_HEIGHT - 0.5);

        // ── Track lanes.
        int trackCount = data.trackCount();
        int noteRange = Math.max(1, data.maxNote() - data.minNote() + 1);
        List<NoteRect> noteRects = data.noteRects();

        for (int t = 0; t < trackCount; t++) {
            double laneY = RULER_HEIGHT + t * (LANE_HEIGHT + LANE_GAP);
            String trackKey = data.trackNames().get(t);
            Color trackColor = TRACK_COLORS[t % TRACK_COLORS.length];
            double notePixelHeight = (LANE_HEIGHT - LANE_HEADER) / noteRange;
            boolean disabled = disabledTracks.contains(trackKey);

            // Pitch grid (every octave C).
            gc.setStroke(GRID_LINE);
            gc.setLineWidth(0.5);
            gc.setFont(Font.font(9));
            for (int note = data.minNote(); note <= data.maxNote(); note++) {
                if (note % 12 == 0) {
                    double ny = laneY + LANE_HEADER + (data.maxNote() - note) * notePixelHeight;
                    gc.strokeLine(0, ny, w, ny);
                    gc.setFill(GRID_LINE.brighter());
                    gc.fillText("C" + (note / 12 - 1), 2, ny - 1);
                }
            }

            // Bar lines.
            if (barTickWidth > 0) {
                gc.setStroke(BAR_LINE);
                gc.setLineWidth(0.5);
                for (long tick = 0; tick <= data.totalTicks(); tick += barTickWidth) {
                    double bx = PADDING_LEFT + tick * pixelsPerTick;
                    gc.strokeLine(bx, laneY, bx, laneY + LANE_HEIGHT);
                }
            }

            // Notes for this track.
            Color auxColor = trackColor.deriveColor(30, 0.55, 1.15, 1.0);
            for (NoteRect r : noteRects) {
                if (!r.trackKey().equals(trackKey)) continue;
                double x = PADDING_LEFT + r.startTick() * pixelsPerTick;
                double rw = (r.endTick() - r.startTick()) * pixelsPerTick;
                double y = laneY + LANE_HEADER + (data.maxNote() - r.midiNote()) * notePixelHeight;
                boolean active = currentTick >= r.startTick() && currentTick < r.endTick();
                Color base = r.isAux() ? auxColor : trackColor;
                Color fill = active ? base : base.deriveColor(0, 1, 1, 0.6);
                if (disabled) fill = fill.deriveColor(0, 0.3, 0.6, 0.4);
                gc.setFill(fill);
                gc.fillRoundRect(x, y - NOTE_HEIGHT / 2, Math.max(rw, 2), NOTE_HEIGHT, 2, 2);
            }

            // Lane separator.
            if (t > 0) {
                gc.setStroke(LANE_SEPARATOR);
                gc.setLineWidth(1);
                gc.strokeLine(0, laneY - LANE_GAP / 2, w, laneY - LANE_GAP / 2);
            }
        }
    }

    private void updatePlayhead(long tick) {
        if (data == null) {
            playheadLine.setVisible(false);
            return;
        }
        double x = PADDING_LEFT + tick * pixelsPerTick;
        playheadLine.setStartX(x);
        playheadLine.setEndX(x);
        playheadLine.setStartY(0);
        playheadLine.setEndY(canvas.getHeight());
    }

    // ── Hit-test + tooltip helpers ───────────────────────────────────

    private NoteRect hitTest(double x, double y) {
        if (data == null) return null;
        if (pixelsPerTick <= 0) return null;
        if (y < RULER_HEIGHT) return null;

        int trackCount = data.trackCount();
        double yInLanes = y - RULER_HEIGHT;
        int trackIdx = (int) (yInLanes / (LANE_HEIGHT + LANE_GAP));
        if (trackIdx < 0 || trackIdx >= trackCount) return null;

        double laneY = RULER_HEIGHT + trackIdx * (LANE_HEIGHT + LANE_GAP);
        int noteRange = Math.max(1, data.maxNote() - data.minNote() + 1);
        double notePixelHeight = (LANE_HEIGHT - LANE_HEADER) / noteRange;
        String trackKey = data.trackNames().get(trackIdx);
        double tolerance = NOTE_HEIGHT;

        NoteRect best = null;
        double bestDy = Double.POSITIVE_INFINITY;
        for (NoteRect r : data.noteRects()) {
            if (!r.trackKey().equals(trackKey)) continue;
            double rx = PADDING_LEFT + r.startTick() * pixelsPerTick;
            double rw = Math.max((r.endTick() - r.startTick()) * pixelsPerTick, 2);
            if (x < rx || x > rx + rw) continue;
            double ry = laneY + LANE_HEADER + (data.maxNote() - r.midiNote()) * notePixelHeight;
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
        if (data.barTickWidth() > 0 && data.ticksPerQuarter() > 0) {
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
