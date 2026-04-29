package music.notation.ui;

import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;
import java.util.function.IntFunction;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

/**
 * Coordinator for a stack of {@link PitchScrollLane}s — one canvas per
 * track — with a single global playhead overlay spanning all lanes.
 *
 * <p>Replaces the previous all-tracks single-canvas design. Each lane
 * is self-contained: it renders only its own track's
 * {@link NoteRect}s, owns its hit-test, and owns its hover tooltip.
 * The playhead is a single overlay {@link Line} positioned in this
 * coordinator's coordinate space.</p>
 *
 * <p>The component itself is a {@link Region} (layered on a
 * {@link StackPane}) — wrap externally in a {@link javafx.scene.control.ScrollPane}
 * for vertical/horizontal scroll. Each per-track row is an
 * {@link HBox} of <i>(controlPanel, lane)</i>; the controlPanel is
 * supplied by the host via a {@link ControlPanelFactory}.</p>
 */
final class PitchScroll extends StackPane {

    private static final Color CURSOR_COLOR = Color.web("#f5c2e7");
    private static final Color[] TRACK_COLORS = {
            Color.web("#f38ba8"), Color.web("#a6e3a1"), Color.web("#89b4fa"),
            Color.web("#fab387"), Color.web("#cba6f7"), Color.web("#f9e2af"),
            Color.web("#94e2d5"), Color.web("#f2cdcd")
    };

    /** Provides the control-panel `Node` for each track row. */
    @FunctionalInterface
    interface ControlPanelFactory {
        Node create(int trackIndex, String trackName);
    }

    private static final double DEFAULT_MIN_QUARTER_PX = 4.0;

    private final LongConsumer onSeek;
    private final DoubleConsumer onCursorMove;
    private final ControlPanelFactory controlPanelFactory;
    private final double controlPanelWidth;

    private final VBox lanesBox;
    private final Pane playheadOverlay;
    private final Line playheadLine;
    private final AnimationTimer timer;

    private PitchScrollData data;
    private LongSupplier tickSource;
    private double minQuarterPx = DEFAULT_MIN_QUARTER_PX;
    private double pixelsPerTick = 1.0;

    private final List<PitchScrollLane> lanes = new ArrayList<>();
    private final List<Node> controlPanels = new ArrayList<>();

    PitchScroll(LongConsumer onSeek,
                DoubleConsumer onCursorMove,
                ControlPanelFactory controlPanelFactory,
                double controlPanelWidth) {
        this.onSeek = onSeek;
        this.onCursorMove = onCursorMove;
        this.controlPanelFactory = controlPanelFactory;
        this.controlPanelWidth = controlPanelWidth;

        setStyle("-fx-background-color: #1e1e2e;");

        lanesBox = new VBox(2);
        lanesBox.setStyle("-fx-background-color: #1e1e2e;");

        // Playhead overlay layer: a Pane with a Line, mouse-transparent so
        // clicks pass through to the lane canvases below.
        playheadLine = new Line();
        playheadLine.setStroke(CURSOR_COLOR);
        playheadLine.setStrokeWidth(1.5);
        playheadLine.setVisible(false);
        playheadOverlay = new Pane(playheadLine);
        playheadOverlay.setMouseTransparent(true);
        playheadOverlay.setStyle("-fx-background-color: transparent;");

        getChildren().setAll(lanesBox, playheadOverlay);

        this.timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                long tick = tickSource != null ? tickSource.getAsLong() : 0;
                for (PitchScrollLane lane : lanes) lane.setCurrentTick(tick);
                updatePlayhead(tick);
                if (onCursorMove != null) onCursorMove.accept(playheadLine.getStartX());
            }
        };

        // Re-position the playhead and re-layout overlay when sizes change.
        widthProperty().addListener((obs, o, n) -> updatePlayhead(currentTick()));
        heightProperty().addListener((obs, o, n) -> {
            playheadOverlay.setPrefHeight(n.doubleValue());
            updatePlayhead(currentTick());
        });
    }

    /** Load piece data and rebuild lanes. */
    void load(PitchScrollData data) {
        this.data = data;
        rebuildLanes();
        recomputePixelsPerTick();
        applyZoomToLanes();
        updatePlayhead(0);
    }

    /** Set the minimum pixels-per-quarter (zoom). Triggers re-layout. */
    void setMinQuarterPx(double value) {
        this.minQuarterPx = value;
        recomputePixelsPerTick();
        applyZoomToLanes();
        updatePlayhead(currentTick());
    }

    /** Total height required to render all lanes (for parent sizing). */
    double computeContentHeight() {
        if (data == null || data.trackCount() == 0) return 0;
        return data.trackCount() * (PitchScrollLane.LANE_HEIGHT + 2);
    }

    /** Minimum width so a quarter note is at least {@code minQuarterPx} wide. */
    double getMinContentWidth() {
        if (data == null || data.totalTicks() == 0) return 0;
        double minPpt = minQuarterPx / data.ticksPerQuarter();
        double laneW = data.totalTicks() * minPpt
                + PitchScrollLane.PADDING_LEFT + PitchScrollLane.PADDING_RIGHT;
        return controlPanelWidth + laneW;
    }

    /** Available width for the lane canvas at the current viewport size. */
    void resizeLanesToWidth(double totalWidth) {
        double laneWidth = Math.max(0, totalWidth - controlPanelWidth);
        for (PitchScrollLane lane : lanes) {
            lane.setWidth(laneWidth);
        }
        recomputePixelsPerTick(laneWidth);
        applyZoomToLanes();
    }

    void startAnimation(LongSupplier tickSource) {
        this.tickSource = tickSource;
        playheadLine.setVisible(true);
        timer.start();
    }

    void stopAnimation() {
        timer.stop();
        this.tickSource = null;
    }

    long currentTick() {
        return tickSource != null ? tickSource.getAsLong() : 0;
    }

    /** Toggle a lane's enabled visual state (greyed out when disabled). */
    void setTrackEnabled(String trackName, boolean enabled) {
        for (int i = 0; i < lanes.size(); i++) {
            if (lanes.get(i).trackKey().equals(trackName)) {
                lanes.get(i).setOpacity(enabled ? 1.0 : 0.3);
                return;
            }
        }
    }

    // ── Internals ──────────────────────────────────────────────────

    private void rebuildLanes() {
        lanesBox.getChildren().clear();
        lanes.clear();
        controlPanels.clear();
        if (data == null) return;

        for (int t = 0; t < data.trackCount(); t++) {
            String name = data.trackNames().get(t);
            Color color = TRACK_COLORS[t % TRACK_COLORS.length];

            PitchScrollLane lane = new PitchScrollLane(name, color, onSeek);
            lane.setHeight(PitchScrollLane.LANE_HEIGHT);
            lanes.add(lane);

            Node panel = controlPanelFactory != null
                    ? controlPanelFactory.create(t, name)
                    : new Pane();
            if (panel instanceof Region r) {
                r.setMinWidth(controlPanelWidth);
                r.setPrefWidth(controlPanelWidth);
                r.setMaxWidth(controlPanelWidth);
                r.setMinHeight(PitchScrollLane.LANE_HEIGHT);
                r.setPrefHeight(PitchScrollLane.LANE_HEIGHT);
            }
            controlPanels.add(panel);

            HBox row = new HBox(0, panel, lane);
            row.setAlignment(Pos.TOP_LEFT);
            row.setPadding(Insets.EMPTY);
            row.setStyle("-fx-background-color: #1e1e2e; -fx-border-color: #313244 transparent transparent transparent; -fx-border-width: 1;");
            lanesBox.getChildren().add(row);
        }

        for (PitchScrollLane lane : lanes) {
            lane.load(data, pixelsPerTick);
        }
    }

    private void recomputePixelsPerTick() {
        if (lanes.isEmpty()) return;
        recomputePixelsPerTick(lanes.get(0).getWidth());
    }

    private void recomputePixelsPerTick(double laneWidth) {
        if (data == null || data.totalTicks() == 0) {
            this.pixelsPerTick = 1.0;
            return;
        }
        double minPpt = minQuarterPx / data.ticksPerQuarter();
        double fitPpt = (laneWidth - PitchScrollLane.PADDING_LEFT - PitchScrollLane.PADDING_RIGHT)
                / data.totalTicks();
        this.pixelsPerTick = Math.max(minPpt, fitPpt);
    }

    private void applyZoomToLanes() {
        for (PitchScrollLane lane : lanes) {
            lane.setPixelsPerTick(pixelsPerTick);
        }
    }

    private void updatePlayhead(long tick) {
        if (data == null) {
            playheadLine.setVisible(false);
            return;
        }
        double x = controlPanelWidth + PitchScrollLane.PADDING_LEFT + tick * pixelsPerTick;
        double topY = 0;
        double bottomY = computeContentHeight();
        playheadLine.setStartX(x);
        playheadLine.setEndX(x);
        playheadLine.setStartY(topY);
        playheadLine.setEndY(bottomY);
    }

    /** Lookup function for the control panel of a given track index, exposed for tests. */
    IntFunction<Node> controlPanelLookup() {
        return i -> i >= 0 && i < controlPanels.size() ? controlPanels.get(i) : null;
    }
}
