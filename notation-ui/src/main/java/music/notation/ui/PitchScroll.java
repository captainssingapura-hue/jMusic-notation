package music.notation.ui;

import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Tooltip;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.util.Duration;

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
    private static final double FIXED_LANE_HEIGHT = 120.0;
    private static final double PADDING_LEFT = 40.0;
    private static final double PADDING_RIGHT = 20.0;
    private static final double DEFAULT_MIN_QUARTER_PX = 4.0;
    private static final double LYRICS_HEIGHT = 32.0;

    private static final Color BG = Color.web("#1e1e2e");
    private static final Color LYRICS_BG = Color.web("#181825");
    private static final Color LYRICS_ACTIVE = Color.web("#f5c2e7");
    private static final Color LYRICS_PAST = Color.web("#6c7086");
    private static final Color LYRICS_FUTURE = Color.web("#a6adc8");
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

    private static final String[] NOTE_NAMES = {
            "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
    };

    private final LongConsumer onSeek;
    private final DoubleConsumer onCursorMove;
    private final AnimationTimer timer;
    private final Tooltip hoverTooltip = new Tooltip();

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

        hoverTooltip.setShowDelay(Duration.millis(100));
        hoverTooltip.setHideDelay(Duration.ZERO);
        hoverTooltip.setStyle("-fx-font-size: 12; -fx-background-color: #313244; -fx-text-fill: #cdd6f4;");

        // Click or drag to seek — render immediately for instant visual feedback
        final javafx.event.EventHandler<javafx.scene.input.MouseEvent> seekFromMouse = event -> {
            if (data == null) return;
            final long tick = Math.clamp(
                    (long) ((event.getX() - PADDING_LEFT) / pixelsPerTick()),
                    0, data.totalTicks());
            onSeek.accept(tick);
            render(tick);
            hoverTooltip.hide();
        };
        setOnMousePressed(seekFromMouse::handle);
        setOnMouseDragged(seekFromMouse::handle);

        // Hover: show a tooltip with the exact note name + bar/beat + track
        setOnMouseMoved(event -> {
            final NoteRect r = hitTest(event.getX(), event.getY());
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

    /** Total canvas height required to render all track lanes at fixed height. */
    double computeContentHeight() {
        if (data == null || data.trackCount() == 0) return 0;
        final boolean hasLyrics = !data.lyricRects().isEmpty();
        final double lyricsOffset = hasLyrics ? LYRICS_HEIGHT : 0;
        return lyricsOffset + data.trackCount() * FIXED_LANE_HEIGHT + (data.trackCount() - 1) * LANE_GAP;
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
        final boolean hasLyrics = !data.lyricRects().isEmpty();
        final double lyricsOffset = hasLyrics ? LYRICS_HEIGHT : 0;

        // ── Lyrics strip ──
        if (hasLyrics) {
            gc.setFill(LYRICS_BG);
            gc.fillRect(0, 0, w, LYRICS_HEIGHT);

            gc.setFont(Font.font("System", 16));
            for (final LyricRect lr : data.lyricRects()) {
                final double x = PADDING_LEFT + lr.startTick() * ppt;
                final boolean active = currentTick >= lr.startTick()
                        && currentTick < lr.endTick();
                final boolean past = currentTick >= lr.endTick();

                gc.setFill(active ? LYRICS_ACTIVE : past ? LYRICS_PAST : LYRICS_FUTURE);
                gc.fillText(lr.syllable(), x, LYRICS_HEIGHT - 10);
            }

            // Separator below lyrics
            gc.setStroke(LANE_SEPARATOR);
            gc.setLineWidth(1);
            gc.strokeLine(0, LYRICS_HEIGHT, w, LYRICS_HEIGHT);
        }

        // ── Track lanes ──
        final int trackCount = data.trackCount();
        final double laneHeight = FIXED_LANE_HEIGHT;
        final int noteRange = data.maxNote() - data.minNote() + 1;
        final List<NoteRect> noteRects = data.noteRects();

        for (int t = 0; t < trackCount; t++) {
            final double laneY = lyricsOffset + t * (laneHeight + LANE_GAP);
            final Color trackColor = TRACK_COLORS[t % TRACK_COLORS.length];
            final double notePixelHeight = (laneHeight - LANE_HEADER) / noteRange;
            final String trackKey = data.trackNames().get(t);

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

            // Bar lines — aligned to absolute bar boundaries (multiples of barWidth
            // from tick 0). When a piece has a pickup, the first bar [0, barWidth)
            // is the pickup/anacrusis bar; the first "true" music bar starts at
            // tick == barWidth. Drawing lines from tick 0 gives the exact staff
            // layout: the pickup audible content sits to the RIGHT of the first
            // bar line, and the downbeat of bar 1 lands precisely on the second.
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
            final Color auxColor = trackColor.deriveColor(30, 0.55, 1.15, 1.0);
            for (final NoteRect r : noteRects) {
                if (!r.trackKey().equals(trackKey)) continue;

                final double x = PADDING_LEFT + r.startTick() * ppt;
                final double rw = (r.endTick() - r.startTick()) * ppt;
                final double y = laneY + LANE_HEADER + (data.maxNote() - r.midiNote()) * notePixelHeight;

                final boolean active = currentTick >= r.startTick() && currentTick < r.endTick();
                final Color base = r.isAux() ? auxColor : trackColor;
                gc.setFill(active ? base : base.deriveColor(0, 1, 1, 0.6));
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

    /**
     * Find the {@link NoteRect} under the given canvas-local position, if any.
     * Returns the closest match within a small vertical tolerance (so thin
     * {@code NOTE_HEIGHT}=4 rectangles are still easy to target with a mouse).
     */
    private NoteRect hitTest(final double x, final double y) {
        if (data == null || data.trackCount() == 0) return null;
        final double ppt = pixelsPerTick();
        if (ppt <= 0) return null;

        final boolean hasLyrics = !data.lyricRects().isEmpty();
        final double lyricsOffset = hasLyrics ? LYRICS_HEIGHT : 0;
        final double laneHeight = FIXED_LANE_HEIGHT;
        final int trackCount = data.trackCount();
        final int noteRange = data.maxNote() - data.minNote() + 1;

        // Which lane is the cursor in?
        final double yInLanes = y - lyricsOffset;
        if (yInLanes < 0) return null;
        final int trackIdx = (int) (yInLanes / (laneHeight + LANE_GAP));
        if (trackIdx < 0 || trackIdx >= trackCount) return null;

        final double laneY = lyricsOffset + trackIdx * (laneHeight + LANE_GAP);
        final double notePixelHeight = (laneHeight - LANE_HEADER) / noteRange;
        final String trackKey = data.trackNames().get(trackIdx);

        // Vertical tolerance: note rects are only 4px tall — give the hit-test ±4px.
        final double tolerance = NOTE_HEIGHT;

        NoteRect best = null;
        double bestDy = Double.POSITIVE_INFINITY;
        for (final NoteRect r : data.noteRects()) {
            if (!r.trackKey().equals(trackKey)) continue;
            final double rx = PADDING_LEFT + r.startTick() * ppt;
            final double rw = Math.max((r.endTick() - r.startTick()) * ppt, 2);
            if (x < rx || x > rx + rw) continue;

            final double ry = laneY + LANE_HEADER + (data.maxNote() - r.midiNote()) * notePixelHeight;
            final double dy = Math.abs(y - ry);
            if (dy <= tolerance && dy < bestDy) {
                best = r;
                bestDy = dy;
            }
        }
        return best;
    }

    /** Format "C#5 · bar 3 beat 2 · Track Name" for the hover tooltip. */
    private String formatNoteInfo(final NoteRect r) {
        final StringBuilder sb = new StringBuilder();
        sb.append(noteName(r.midiNote()));

        // Bar/beat derived from absolute tick position. Bar boundaries are
        // multiples of barWidth from tick 0; when the piece has a pickup, the
        // first bar [0, barWidth) is the anacrusis ("pickup" / bar 0) and the
        // first full music bar is bar 1. Without a pickup, we shift to 1-index
        // the opening bar as bar 1.
        if (data != null && data.barTickWidth() > 0 && data.ticksPerQuarter() > 0) {
            final long barWidth = data.barTickWidth();
            final boolean hasPickup = data.pickupOffsetTicks() > 0;
            final long rawBar = r.startTick() / barWidth;
            final long tickInBar = r.startTick() % barWidth;
            final long beat = tickInBar / data.ticksPerQuarter() + 1;
            sb.append("  ·  ");
            if (hasPickup && rawBar == 0) {
                sb.append("pickup beat ").append(beat);
            } else {
                final long barIdx = hasPickup ? rawBar : rawBar + 1;
                sb.append("bar ").append(barIdx).append(" beat ").append(beat);
            }
        }

        sb.append("  ·  ").append(r.trackKey());
        if (r.isAux()) sb.append(" (voice ").append(r.voice()).append(')');
        return sb.toString();
    }

    /** Convert a MIDI note number (0-127) to scientific pitch notation (e.g. 60 → "C4"). */
    private static String noteName(final int midi) {
        final int octave = midi / 12 - 1;
        return NOTE_NAMES[((midi % 12) + 12) % 12] + octave;
    }
}
