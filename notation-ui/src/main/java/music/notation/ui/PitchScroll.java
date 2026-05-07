package music.notation.ui;

import javafx.animation.AnimationTimer;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.util.Duration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.DoubleConsumer;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

/**
 * Self-contained scrolled piano-roll. Internally split into two
 * canvases — a fixed ruler + tempo band pinned at the top, and the
 * lane area below — bound by a pair of {@link ScrollBar}s pinned to
 * the right and bottom edges of the widget. Logically there is one
 * big "piece canvas"; physically the viewport is two clipped panes
 * sharing the same horizontal offset, and the lanes pane has its own
 * vertical offset that the control-panel column tracks in lock-step.
 */
final class PitchScroll extends BorderPane {

    // ── Visual constants ──────────────────────────────────────────────
    private static final double RULER_HEIGHT = 20.0;
    private static final double TEMPO_BAND_HEIGHT = 7.0;
    /** Top of the lane area within the ruler canvas (also = ruler canvas height). */
    private static final double LANE_TOP = RULER_HEIGHT + TEMPO_BAND_HEIGHT;
    private static final double LANE_HEIGHT = 130.0;
    private static final double LANE_GAP = 2.0;
    private static final double LANE_HEADER = 4.0;     // top inset within a lane
    private static final double NOTE_HEIGHT = 4.0;
    private static final double PADDING_LEFT = 8.0;
    private static final double PADDING_RIGHT = 8.0;
    private static final double DEFAULT_MIN_QUARTER_PX = 4.0;
    private static final double SCROLLBAR_THICKNESS = 12.0;
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

    /**
     * Per-lane sustain-pedal background tint. Cycles through this small
     * warm palette so consecutive pedal-down regions are always different
     * colours (especially helpful at {@code <pedal type="change"/>} edges
     * where the audio gap is just 1 ms). See
     * {@code .docs/pedal-visualisation.md}.
     */
    private static final Color[] PEDAL_TINTS = {
            Color.rgb(245, 194, 124, 0.12),   // pale amber
            Color.rgb(245, 154, 162, 0.12),   // pale rose
            Color.rgb(166, 218, 149, 0.10),   // pale sage
            Color.rgb(180, 168, 245, 0.11),   // pale lavender
    };

    /** Pre-converted pedal region for visualisation. */
    record PedalTintRegion(long startTick, long endTick, int paletteIndex) {}

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
    private final VBox controlPanelsBox;
    private final ScrollPane controlsScrollPane;
    private final Canvas rulerCanvas;
    private final Canvas lanesCanvas;
    private final Pane rulerViewport;
    private final Pane lanesViewport;
    private final StackPane lanesStack;
    private final Pane playheadOverlay;
    private final Line playheadLine;
    private final ScrollBar hScrollBar = new ScrollBar();
    private final Tooltip hoverTooltip = new Tooltip();
    private final AnimationTimer timer;

    // ── State ────────────────────────────────────────────────────────
    private PitchScrollData data;
    private LongSupplier tickSource;
    private double minQuarterPx = DEFAULT_MIN_QUARTER_PX;
    private double pixelsPerTick = 1.0;
    private long currentTick;
    private final Set<String> disabledTracks = new HashSet<>();
    /** Pre-computed pedal tint regions per lane (track-name → list of regions). Empty when no pedaling. */
    private java.util.Map<String, java.util.List<PedalTintRegion>> pedalRegionsByTrack = java.util.Map.of();
    /** Honor-or-ignore toggle. Tints disappear when false. */
    private boolean pedalTintEnabled = true;

    PitchScroll(LongConsumer onSeek,
                DoubleConsumer onCursorMove,
                ControlPanelFactory controlPanelFactory,
                double controlPanelWidth) {
        this.onSeek = onSeek;
        this.onCursorMove = onCursorMove;
        this.controlPanelFactory = controlPanelFactory;
        this.controlPanelWidth = controlPanelWidth;

        setStyle("-fx-background-color: #1e1e2e;");

        // ── Top row: corner spacer (above the controls column) + ruler canvas pane.
        Pane topLeftCorner = makeBox(controlPanelWidth, LANE_TOP, "#181825",
                "-fx-border-color: transparent transparent #313244 transparent; -fx-border-width: 1;");

        rulerCanvas = new Canvas(800, LANE_TOP);
        rulerViewport = new Pane(rulerCanvas);
        rulerViewport.setMinHeight(LANE_TOP);
        rulerViewport.setPrefHeight(LANE_TOP);
        rulerViewport.setMaxHeight(LANE_TOP);
        rulerViewport.setStyle("-fx-background-color: #181825;");
        clipToBounds(rulerViewport);
        HBox.setHgrow(rulerViewport, Priority.ALWAYS);

        HBox topRow = new HBox(topLeftCorner, rulerViewport);

        // ── Centre row: scrollable controls column + lanes pane.
        // The controls column owns its own (native) ScrollPane vertical
        // scrollbar at its right edge. The lanes canvas mirrors the
        // ScrollPane's vvalue via a translateY listener — one source of
        // truth for vertical scroll.
        controlPanelsBox = new VBox(LANE_GAP);
        controlPanelsBox.setStyle("-fx-background-color: #181825;");
        controlPanelsBox.setMinWidth(controlPanelWidth);
        controlPanelsBox.setPrefWidth(controlPanelWidth);
        controlPanelsBox.setMaxWidth(controlPanelWidth);

        controlsScrollPane = new ScrollPane(controlPanelsBox);
        controlsScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        controlsScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        controlsScrollPane.setFitToWidth(true);
        controlsScrollPane.setStyle(
                "-fx-background: #181825; -fx-background-color: #181825;"
                        + " -fx-padding: 0; -fx-border-width: 0;");
        controlsScrollPane.setMinWidth(controlPanelWidth + SCROLLBAR_THICKNESS);
        controlsScrollPane.setPrefWidth(controlPanelWidth + SCROLLBAR_THICKNESS);
        controlsScrollPane.setMaxWidth(controlPanelWidth + SCROLLBAR_THICKNESS);

        lanesCanvas = new Canvas(800, LANE_HEIGHT);

        playheadLine = new Line();
        playheadLine.setStroke(CURSOR_COLOR);
        playheadLine.setStrokeWidth(1.5);
        playheadLine.setVisible(false);
        playheadOverlay = new Pane(playheadLine);
        playheadOverlay.setMouseTransparent(true);
        playheadOverlay.setPickOnBounds(false);

        lanesStack = new StackPane(lanesCanvas, playheadOverlay);
        lanesStack.setAlignment(Pos.TOP_LEFT);
        lanesStack.setStyle("-fx-background-color: #1e1e2e;");

        lanesViewport = new Pane(lanesStack);
        lanesViewport.setStyle("-fx-background-color: #1e1e2e;");
        clipToBounds(lanesViewport);
        HBox.setHgrow(lanesViewport, Priority.ALWAYS);

        HBox centreRow = new HBox(controlsScrollPane, lanesViewport);
        VBox.setVgrow(centreRow, Priority.ALWAYS);

        // ── Bottom row: corner spacer (below controls) + hScrollBar.
        Pane bottomLeftCorner = makeBox(controlPanelWidth + SCROLLBAR_THICKNESS,
                SCROLLBAR_THICKNESS, "#181825", "");

        hScrollBar.setOrientation(Orientation.HORIZONTAL);
        hScrollBar.setMinHeight(SCROLLBAR_THICKNESS);
        hScrollBar.setPrefHeight(SCROLLBAR_THICKNESS);
        hScrollBar.setMaxHeight(SCROLLBAR_THICKNESS);
        HBox.setHgrow(hScrollBar, Priority.ALWAYS);

        HBox bottomRow = new HBox(bottomLeftCorner, hScrollBar);

        setTop(topRow);
        setCenter(centreRow);
        setBottom(bottomRow);

        // ── Bind canvas translates.
        // Horizontal: both canvases follow the bottom hScrollBar (single source).
        DoubleBinding negH = hScrollBar.valueProperty().multiply(-1);
        rulerCanvas.translateXProperty().bind(negH);
        lanesStack.translateXProperty().bind(negH);

        // Vertical: lanes mirror the controls ScrollPane's vvalue.
        // Range is the actual scrollable content height (not canvas height) —
        // canvas may be stretched to fill the viewport when content is short,
        // but scrolling distance must stay tied to the content.
        javafx.beans.value.ChangeListener<Number> syncY = (obs, o, n) -> {
            double range = verticalScrollRange();
            lanesStack.setTranslateY(-controlsScrollPane.getVvalue() * range);
        };
        controlsScrollPane.vvalueProperty().addListener(syncY);
        lanesViewport.heightProperty().addListener(syncY);

        // Wheel-scroll over the lanes pane drives the controls ScrollPane
        // so the user can scroll vertically with the mouse anywhere.
        lanesViewport.setOnScroll(event -> {
            double dy = event.getDeltaY();
            if (dy == 0) return;
            double range = verticalScrollRange();
            if (range <= 0) return;
            double newV = controlsScrollPane.getVvalue() - dy / range;
            controlsScrollPane.setVvalue(Math.max(0, Math.min(1, newV)));
            event.consume();
        });

        hScrollBar.setUnitIncrement(40);
        hScrollBar.setBlockIncrement(200);

        // Recompute canvas sizes whenever the viewport changes — the canvas
        // always grows to fill the visible area in both dimensions.
        rulerViewport.widthProperty().addListener((obs, o, n) -> recomputeSizing());
        lanesViewport.widthProperty().addListener((obs, o, n) -> recomputeSizing());
        lanesViewport.heightProperty().addListener((obs, o, n) -> recomputeSizing());

        // ── Hover tooltip wiring: ruler/tempo band + lane notes.
        hoverTooltip.setShowDelay(Duration.millis(100));
        hoverTooltip.setHideDelay(Duration.ZERO);
        hoverTooltip.setStyle("-fx-font-size: 12; -fx-background-color: #313244; -fx-text-fill: #cdd6f4;");

        // Ruler canvas: click-to-seek + tempo-band tooltip.
        rulerCanvas.setOnMouseClicked(event -> {
            if (data == null) return;
            seekToCanvasX(event.getX());
        });
        rulerCanvas.setOnMouseMoved(event -> {
            if (data == null) { hoverTooltip.hide(); return; }
            if (event.getY() >= RULER_HEIGHT && event.getY() < LANE_TOP) {
                String text = tempoInfoAt(event.getX());
                if (text != null) {
                    hoverTooltip.setText(text);
                    showTooltipAt(event.getScreenX(), event.getScreenY(), rulerCanvas);
                    return;
                }
            }
            hoverTooltip.hide();
        });
        rulerCanvas.setOnMouseExited(event -> hoverTooltip.hide());

        // Lanes canvas: drag-to-seek + note hover tooltip.
        lanesCanvas.setOnMousePressed(event -> { if (data != null) seekToCanvasX(event.getX()); hoverTooltip.hide(); });
        lanesCanvas.setOnMouseDragged(event -> { if (data != null) seekToCanvasX(event.getX()); hoverTooltip.hide(); });
        lanesCanvas.setOnMouseMoved(event -> {
            NoteRect r = hitTest(event.getX(), event.getY());
            if (r != null) {
                hoverTooltip.setText(formatNoteInfo(r));
                showTooltipAt(event.getScreenX(), event.getScreenY(), lanesCanvas);
            } else {
                hoverTooltip.hide();
            }
        });
        lanesCanvas.setOnMouseExited(event -> hoverTooltip.hide());

        // ── Animation timer drives the playhead during playback.
        this.timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                long tick = tickSource != null ? tickSource.getAsLong() : 0;
                currentTick = tick;
                redrawLanes();
                updatePlayhead(tick);
                if (onCursorMove != null) onCursorMove.accept(playheadLine.getStartX());
                if (onCursorTick != null) onCursorTick.accept(tick);
            }
        };
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
        redrawLanes();
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

    /**
     * Pre-computed sustain-pedal tint regions per track-name. Pass an
     * empty map to clear. Adjacent regions within a lane should carry
     * incrementing palette indices so they cycle through different
     * tints — see {@code .docs/pedal-visualisation.md}.
     */
    void setPedalRegions(java.util.Map<String, java.util.List<PedalTintRegion>> regions) {
        this.pedalRegionsByTrack = (regions == null) ? java.util.Map.of() : regions;
        redrawLanes();
    }

    /** Honor-or-ignore tint toggle. Audio toggle is separate (on MidiPlayer). */
    void setPedalTintEnabled(boolean enabled) {
        this.pedalTintEnabled = enabled;
        redrawLanes();
    }

    void setTrackEnabled(String trackName, boolean enabled) {
        if (enabled) disabledTracks.remove(trackName);
        else disabledTracks.add(trackName);
        redrawLanes();
    }

    double computeContentHeight() {
        if (data == null || data.trackCount() == 0) return 0;
        return data.trackCount() * (LANE_HEIGHT + LANE_GAP);
    }

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
        redrawLanes();
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

    /** Horizontal scroll fraction in [0, 1]. */
    double getLaneScrollHvalue() {
        double max = hScrollBar.getMax();
        return max <= 0 ? 0 : hScrollBar.getValue() / max;
    }

    void setLaneScrollHvalue(double v) {
        double max = hScrollBar.getMax();
        hScrollBar.setValue(Math.clamp(v, 0, 1) * max);
    }

    double getLaneViewportWidth() { return lanesViewport.getWidth(); }
    double getLaneContentWidth()  { return lanesCanvas.getWidth(); }

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
        double viewportW = lanesViewport.getWidth();
        double viewportH = lanesViewport.getHeight();
        if (viewportW <= 0) viewportW = 800;
        if (viewportH <= 0) viewportH = 400;

        double minPpt = data.totalTicks() == 0 ? 1.0 : minQuarterPx / data.ticksPerQuarter();
        double minLaneW = data.totalTicks() == 0
                ? viewportW
                : data.totalTicks() * minPpt + PADDING_LEFT + PADDING_RIGHT;
        double laneWidth = Math.min(MAX_CANVAS_DIM, Math.max(viewportW, minLaneW));

        // Canvas height stretches to fill the viewport when content is shorter,
        // so the lane background extends to the bottom edge instead of leaving
        // an empty strip. Vertical scroll range is still computed from the
        // actual content height (see verticalScrollRange).
        double contentH = computeContentHeight();
        double canvasH = Math.max(contentH, viewportH);

        rulerCanvas.setWidth(laneWidth);
        rulerCanvas.setHeight(LANE_TOP);
        lanesCanvas.setWidth(laneWidth);
        lanesCanvas.setHeight(canvasH);
        lanesStack.setMinWidth(laneWidth);
        lanesStack.setPrefWidth(laneWidth);
        lanesStack.setMinHeight(canvasH);
        lanesStack.setPrefHeight(canvasH);
        playheadOverlay.setPrefWidth(laneWidth);
        playheadOverlay.setPrefHeight(canvasH);

        if (data.totalTicks() > 0) {
            pixelsPerTick = (laneWidth - PADDING_LEFT - PADDING_RIGHT) / data.totalTicks();
        } else {
            pixelsPerTick = 1.0;
        }

        updateScrollbars();
        // Re-apply vertical translate against the (possibly new) range.
        double range = verticalScrollRange();
        lanesStack.setTranslateY(-controlsScrollPane.getVvalue() * range);
        redraw();
        updatePlayhead(currentTick);
    }

    /** Pixel range over which the lanes can scroll vertically. */
    private double verticalScrollRange() {
        return Math.max(0, computeContentHeight() - lanesViewport.getHeight());
    }

    private void updateScrollbars() {
        double cW = lanesCanvas.getWidth();
        double vpW = lanesViewport.getWidth();
        double hMax = Math.max(0, cW - vpW);
        hScrollBar.setMin(0);
        hScrollBar.setMax(hMax);
        hScrollBar.setVisibleAmount(Math.min(vpW, cW));
        if (hScrollBar.getValue() > hMax) hScrollBar.setValue(hMax);
        // Vertical scroll is owned by controlsScrollPane (native) — nothing to size here.
    }

    private void redraw() {
        redrawRuler();
        redrawLanes();
    }

    private void redrawRuler() {
        if (data == null) return;
        double w = rulerCanvas.getWidth();
        double h = rulerCanvas.getHeight();
        if (w <= 0 || h <= 0) return;

        GraphicsContext gc = rulerCanvas.getGraphicsContext2D();
        gc.setFill(RULER_BG);
        gc.fillRect(0, 0, w, h);

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
                String label = (hasPickup && bar == 0) ? "↟"
                        : String.valueOf(hasPickup ? bar : bar + 1);
                gc.setFill(RULER_TEXT);
                gc.fillText(label, bx + 3, RULER_HEIGHT - 6);
            }
        }
        gc.setStroke(GRID_LINE);
        gc.setLineWidth(0.5);
        gc.strokeLine(0, RULER_HEIGHT - 0.5, w, RULER_HEIGHT - 0.5);

        // ── Tempo band (brushed-metal look, piece-relative gradient).
        var tempoSegments = data.tempoSegments();
        if (!tempoSegments.isEmpty()) {
            int minBpm = Integer.MAX_VALUE, maxBpm = Integer.MIN_VALUE;
            for (var seg : tempoSegments) {
                if (seg.bpm() < minBpm) minBpm = seg.bpm();
                if (seg.bpm() > maxBpm) maxBpm = seg.bpm();
            }
            double bandTop = RULER_HEIGHT;
            double bandBot = LANE_TOP;
            for (var seg : tempoSegments) {
                double x1 = PADDING_LEFT + seg.startTick() * pixelsPerTick;
                double x2 = PADDING_LEFT + seg.endTick() * pixelsPerTick;
                if (x2 <= 0 || x1 >= w) continue;
                double drawX = Math.max(0, x1);
                double drawW = Math.min(w, x2) - drawX;
                if (drawW <= 0) continue;
                Color base = TempoSegment.baseColour(seg.bpm(), minBpm, maxBpm);
                gc.setFill(TempoSegment.metalGradient(bandTop, bandBot, base));
                gc.fillRect(drawX, bandTop, drawW, bandBot - bandTop);
            }
            gc.setLineWidth(1.0);
            gc.setStroke(Color.color(0, 0, 0, 0.45));
            for (var seg : tempoSegments) {
                if (seg.startTick() == 0) continue;
                double bx = PADDING_LEFT + seg.startTick() * pixelsPerTick;
                if (bx < 0 || bx > w) continue;
                gc.strokeLine(Math.round(bx) + 0.5, bandTop, Math.round(bx) + 0.5, bandBot);
            }
            gc.setStroke(Color.color(1, 1, 1, 0.18));
            gc.strokeLine(0, bandTop + 0.5, w, bandTop + 0.5);
            gc.setStroke(Color.color(0, 0, 0, 0.40));
            gc.strokeLine(0, bandBot - 0.5, w, bandBot - 0.5);
        }
    }

    private void redrawLanes() {
        if (data == null) return;
        double w = lanesCanvas.getWidth();
        double h = lanesCanvas.getHeight();
        if (w <= 0 || h <= 0) return;

        GraphicsContext gc = lanesCanvas.getGraphicsContext2D();
        gc.setFill(BG);
        gc.fillRect(0, 0, w, h);

        int trackCount = data.trackCount();
        int noteRange = Math.max(1, data.maxNote() - data.minNote() + 1);
        long barTickWidth = data.barTickWidth();
        List<NoteRect> noteRects = data.noteRects();

        for (int t = 0; t < trackCount; t++) {
            double laneY = t * (LANE_HEIGHT + LANE_GAP);
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

            // Sustain-pedal tint (drawn before notes so notes render on top).
            if (pedalTintEnabled) {
                var regions = pedalRegionsByTrack.get(trackKey);
                if (regions != null && !regions.isEmpty()) {
                    double tintTop = laneY + LANE_HEADER;
                    double tintHeight = LANE_HEIGHT - LANE_HEADER;
                    for (PedalTintRegion region : regions) {
                        double x0 = PADDING_LEFT + region.startTick() * pixelsPerTick;
                        double x1 = PADDING_LEFT + region.endTick() * pixelsPerTick;
                        double rw = Math.max(x1 - x0, 1.0);
                        gc.setFill(PEDAL_TINTS[Math.floorMod(region.paletteIndex(), PEDAL_TINTS.length)]);
                        gc.fillRect(x0, tintTop, rw, tintHeight);
                    }
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
        playheadLine.setEndY(lanesCanvas.getHeight());
    }

    /** Map a canvas-local x to a tick and seek. */
    private void seekToCanvasX(double x) {
        long tick = Math.clamp(
                (long) ((x - PADDING_LEFT) / Math.max(pixelsPerTick, 1e-9)),
                0L, data.totalTicks());
        seekTo(tick);
    }

    // ── Hit-test + tooltip helpers ───────────────────────────────────

    /** Hit-test in the lane canvas's local coordinate space (y=0 is top of first lane). */
    private NoteRect hitTest(double x, double y) {
        if (data == null) return null;
        if (pixelsPerTick <= 0) return null;
        if (y < 0) return null;

        int trackCount = data.trackCount();
        int trackIdx = (int) (y / (LANE_HEIGHT + LANE_GAP));
        if (trackIdx < 0 || trackIdx >= trackCount) return null;

        double laneY = trackIdx * (LANE_HEIGHT + LANE_GAP);
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

    /** Look up the tempo segment under canvas-x, return formatted text or null. */
    private String tempoInfoAt(double x) {
        if (data == null || pixelsPerTick <= 0) return null;
        long tick = Math.round((x - PADDING_LEFT) / Math.max(pixelsPerTick, 1e-9));
        for (var seg : data.tempoSegments()) {
            if (tick >= seg.startTick() && tick < seg.endTick()) {
                long barWidth = data.barTickWidth();
                if (barWidth > 0) {
                    boolean hasPickup = data.pickupOffsetTicks() > 0;
                    long startBar = seg.startTick() / barWidth + (hasPickup ? 0 : 1);
                    long endBar = (seg.endTick() - 1) / barWidth + (hasPickup ? 0 : 1);
                    if (startBar == endBar) {
                        return String.format("♩ = %d  ·  bar %d", seg.bpm(), startBar);
                    }
                    return String.format("♩ = %d  ·  bars %d–%d", seg.bpm(), startBar, endBar);
                }
                return "♩ = " + seg.bpm();
            }
        }
        return null;
    }

    private void showTooltipAt(double screenX, double screenY, Node anchor) {
        if (hoverTooltip.isShowing()) {
            hoverTooltip.setX(screenX + 12);
            hoverTooltip.setY(screenY + 14);
        } else {
            hoverTooltip.show(anchor, screenX + 12, screenY + 14);
        }
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

    // ── Layout helpers ────────────────────────────────────────────────

    private static Pane makeBox(double w, double h, String bgColor, String extraStyle) {
        Pane p = new Pane();
        p.setMinSize(w, h);
        p.setPrefSize(w, h);
        p.setMaxSize(w, h);
        p.setStyle("-fx-background-color: " + bgColor + ";" + extraStyle);
        return p;
    }

    private static void clipToBounds(Pane p) {
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(p.widthProperty());
        clip.heightProperty().bind(p.heightProperty());
        p.setClip(clip);
    }
}
