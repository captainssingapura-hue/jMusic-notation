package music.notation.ui;

import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.util.*;
import java.util.function.LongSupplier;

/**
 * A horizontal 88-key piano keyboard that highlights active notes during playback.
 *
 * <p>Always displays the full 88-key range (A0-C8, MIDI 21-108). The keyboard
 * stretches to fill the available width; key height is derived from width with
 * a maximum aspect ratio so keys maintain realistic proportions.</p>
 */
final class KeyboardDisplay extends Canvas {

    // 88-key piano: A0 (MIDI 21) to C8 (MIDI 108)
    private static final int RANGE_MIN = 21;
    private static final int RANGE_MAX = 108;
    private static final int WHITE_KEY_COUNT = countWhiteKeysInRange();

    // Max white-key height-to-width ratio (real piano keys are ~6:1)
    private static final double MAX_KEY_RATIO = 5.5;
    private static final double BLACK_KEY_HEIGHT_RATIO = 0.625;
    private static final double BLACK_KEY_WIDTH_RATIO = 0.6;
    private static final double LABEL_HEIGHT = 14.0;

    private static final Color BG = Color.web("#1e1e2e");
    private static final Color WHITE_KEY_COLOR = Color.web("#cdd6f4");
    private static final Color BLACK_KEY_COLOR = Color.web("#313244");
    private static final Color KEY_BORDER = Color.web("#45475a");
    private static final Color LABEL_COLOR = Color.web("#6c7086");
    static final Color[] TRACK_COLORS = {
            Color.web("#f38ba8"), Color.web("#a6e3a1"), Color.web("#89b4fa"),
            Color.web("#fab387"), Color.web("#cba6f7"), Color.web("#f9e2af"),
            Color.web("#94e2d5"), Color.web("#f2cdcd")
    };

    private static final Set<Integer> BLACK_KEY_SET = Set.of(1, 3, 6, 8, 10);

    private PitchScrollData data;
    private LongSupplier tickSource;
    private final Set<String> disabledTracks = new HashSet<>();
    private final Map<String, Integer> trackColorIndex = new LinkedHashMap<>();

    private final AnimationTimer timer = new AnimationTimer() {
        @Override
        public void handle(final long now) {
            final long tick = tickSource != null ? tickSource.getAsLong() : 0;
            render(tick);
        }
    };

    KeyboardDisplay() {
        widthProperty().addListener((obs, o, n) -> render(currentTick()));
        heightProperty().addListener((obs, o, n) -> render(currentTick()));
    }

    void load(final PitchScrollData data) {
        this.data = data;
        disabledTracks.clear();
        trackColorIndex.clear();
        if (data != null) {
            for (int i = 0; i < data.trackNames().size(); i++) {
                trackColorIndex.put(data.trackNames().get(i), i);
            }
        }
        render(0);
    }

    void setTrackEnabled(final String trackKey, final boolean enabled) {
        if (enabled) {
            disabledTracks.remove(trackKey);
        } else {
            disabledTracks.add(trackKey);
        }
        render(currentTick());
    }

    void startAnimation(final LongSupplier tickSource) {
        this.tickSource = tickSource;
        timer.start();
    }

    void stopAnimation() {
        timer.stop();
        this.tickSource = null;
        render(0);
    }

    private long currentTick() {
        return tickSource != null ? tickSource.getAsLong() : 0;
    }

    private static int countWhiteKeysInRange() {
        int count = 0;
        for (int note = RANGE_MIN; note <= RANGE_MAX; note++) {
            if (!Set.of(1, 3, 6, 8, 10).contains(note % 12)) count++;
        }
        return count;
    }

    private Color colorForTrack(final String trackKey) {
        Integer idx = trackColorIndex.get(trackKey);
        return TRACK_COLORS[(idx != null ? idx : 0) % TRACK_COLORS.length];
    }

    private void render(final long currentTick) {
        final double w = getWidth();
        final double h = getHeight();
        if (w <= 0 || h <= 0) return;

        final GraphicsContext gc = getGraphicsContext2D();
        gc.setFill(BG);
        gc.fillRect(0, 0, w, h);

        // Compute key dimensions from available space
        final double whiteKeyW = w / WHITE_KEY_COUNT;
        final double maxWhiteH = whiteKeyW * MAX_KEY_RATIO;
        final double whiteH = Math.min(maxWhiteH, h - LABEL_HEIGHT);
        if (whiteH <= 0) return;

        final double blackKeyW = whiteKeyW * BLACK_KEY_WIDTH_RATIO;
        final double blackH = whiteH * BLACK_KEY_HEIGHT_RATIO;

        // Build active notes map: midiNote -> list of track keys
        final Map<Integer, List<String>> activeNotes = new HashMap<>();
        if (data != null && currentTick > 0) {
            for (final NoteRect r : data.noteRects()) {
                if (!disabledTracks.contains(r.trackKey())
                        && currentTick >= r.startTick() && currentTick < r.endTick()) {
                    activeNotes.computeIfAbsent(r.midiNote(), k -> new ArrayList<>())
                            .add(r.trackKey());
                }
            }
        }

        // Pass 1: draw white keys
        int whiteIndex = 0;
        for (int note = RANGE_MIN; note <= RANGE_MAX; note++) {
            final int semitone = note % 12;
            if (BLACK_KEY_SET.contains(semitone)) continue;

            final double x = whiteIndex * whiteKeyW;
            final List<String> tracks = activeNotes.get(note);

            if (tracks != null && !tracks.isEmpty()) {
                final double stripeH = whiteH / tracks.size();
                for (int i = 0; i < tracks.size(); i++) {
                    gc.setFill(colorForTrack(tracks.get(i)));
                    gc.fillRect(x, i * stripeH, whiteKeyW - 1, stripeH);
                }
            } else {
                gc.setFill(WHITE_KEY_COLOR);
                gc.fillRect(x, 0, whiteKeyW - 1, whiteH);
            }

            gc.setStroke(KEY_BORDER);
            gc.setLineWidth(0.5);
            gc.strokeRect(x, 0, whiteKeyW - 1, whiteH);

            // Octave label on C keys
            if (semitone == 0) {
                gc.setFill(LABEL_COLOR);
                gc.setFont(Font.font(Math.min(10, whiteKeyW * 0.6)));
                gc.fillText("C" + (note / 12 - 1), x + 1, whiteH + LABEL_HEIGHT - 2);
            }

            whiteIndex++;
        }

        // Pass 2: draw black keys on top
        whiteIndex = 0;
        for (int note = RANGE_MIN; note <= RANGE_MAX; note++) {
            final int semitone = note % 12;
            if (BLACK_KEY_SET.contains(semitone)) {
                final double x = whiteIndex * whiteKeyW - blackKeyW / 2;
                final List<String> tracks = activeNotes.get(note);

                if (tracks != null && !tracks.isEmpty()) {
                    final double stripeH = blackH / tracks.size();
                    for (int i = 0; i < tracks.size(); i++) {
                        gc.setFill(colorForTrack(tracks.get(i)));
                        gc.fillRect(x, i * stripeH, blackKeyW, stripeH);
                    }
                } else {
                    gc.setFill(BLACK_KEY_COLOR);
                    gc.fillRect(x, 0, blackKeyW, blackH);
                }

                gc.setStroke(KEY_BORDER);
                gc.setLineWidth(0.5);
                gc.strokeRect(x, 0, blackKeyW, blackH);
            } else {
                whiteIndex++;
            }
        }
    }
}
