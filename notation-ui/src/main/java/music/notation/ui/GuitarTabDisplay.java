package music.notation.ui;

import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.util.*;
import java.util.function.LongSupplier;

/**
 * A guitar fretboard visualization that highlights playable positions for active notes.
 *
 * <p>Shows 6 strings in standard tuning (E2–E4) with fret positions. When notes are
 * active during playback, all valid fret positions for those notes are highlighted.
 * Filters for string range and fret range let users narrow down positions.</p>
 *
 * <p>Standard tuning (high to low on screen):
 * <pre>
 *   1st string: E4 (MIDI 64)
 *   2nd string: B3 (MIDI 59)
 *   3rd string: G3 (MIDI 55)
 *   4th string: D3 (MIDI 50)
 *   5th string: A2 (MIDI 45)
 *   6th string: E2 (MIDI 40)
 * </pre>
 */
final class GuitarTabDisplay extends Canvas {

    // Standard tuning: string 0 (high E) to string 5 (low E)
    static final int[] TUNING = {64, 59, 55, 50, 45, 40};
    static final String[] STRING_NAMES = {"e", "B", "G", "D", "A", "E"};
    static final int STRING_COUNT = 6;
    static final int MAX_FRETS = 24;

    private static final double NUT_WIDTH = 4.0;
    private static final double STRING_LABEL_WIDTH = 24.0;
    private static final double FRET_NUMBER_HEIGHT = 18.0;

    private static final Color BG = Color.web("#1e1e2e");
    private static final Color STRING_COLOR = Color.web("#a6adc8");
    private static final Color FRET_COLOR = Color.web("#45475a");
    private static final Color NUT_COLOR = Color.web("#cdd6f4");
    private static final Color FRET_MARKER_COLOR = Color.web("#313244");
    private static final Color LABEL_COLOR = Color.web("#6c7086");
    private static final Color FRET_NUM_COLOR = Color.web("#585b70");
    private static final Color[] TRACK_COLORS = {
            Color.web("#f38ba8"), Color.web("#a6e3a1"), Color.web("#89b4fa"),
            Color.web("#fab387"), Color.web("#cba6f7"), Color.web("#f9e2af"),
            Color.web("#94e2d5"), Color.web("#f2cdcd")
    };

    // Frets with single dot markers (3,5,7,9,15,17,19,21) and double dots (12,24)
    private static final Set<Integer> SINGLE_DOT_FRETS = Set.of(3, 5, 7, 9, 15, 17, 19, 21);
    private static final Set<Integer> DOUBLE_DOT_FRETS = Set.of(12, 24);

    private PitchScrollData data;
    private LongSupplier tickSource;
    private final Set<String> disabledTracks = new HashSet<>();
    private final Map<String, Integer> trackColorIndex = new LinkedHashMap<>();

    // Filters
    private int minFret = 0;
    private int maxFret = MAX_FRETS;
    private final boolean[] stringEnabled = new boolean[STRING_COUNT];

    private final AnimationTimer timer = new AnimationTimer() {
        @Override
        public void handle(final long now) {
            final long tick = tickSource != null ? tickSource.getAsLong() : 0;
            render(tick);
        }
    };

    GuitarTabDisplay() {
        Arrays.fill(stringEnabled, true);
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

    void setFretRange(final int min, final int max) {
        this.minFret = Math.max(0, min);
        this.maxFret = Math.min(MAX_FRETS, max);
        render(currentTick());
    }

    void setStringEnabled(final int stringIndex, final boolean enabled) {
        if (stringIndex >= 0 && stringIndex < STRING_COUNT) {
            stringEnabled[stringIndex] = enabled;
            render(currentTick());
        }
    }

    int getMinFret() { return minFret; }
    int getMaxFret() { return maxFret; }
    boolean isStringEnabled(int s) { return stringEnabled[s]; }

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

        final int fretCount = maxFret - minFret;
        if (fretCount <= 0) return;

        // Layout geometry
        final double fretboardLeft = STRING_LABEL_WIDTH;
        final double fretboardTop = FRET_NUMBER_HEIGHT;
        final double fretboardW = w - fretboardLeft;
        final double fretboardH = h - fretboardTop;
        if (fretboardW <= 0 || fretboardH <= 0) return;

        final double fretSpacing = fretboardW / (fretCount + 1); // +1 for open/nut position
        final double stringSpacing = fretboardH / (STRING_COUNT + 1);

        // Draw nut (if open position visible)
        if (minFret == 0) {
            gc.setFill(NUT_COLOR);
            gc.fillRect(fretboardLeft + fretSpacing - NUT_WIDTH / 2, fretboardTop,
                    NUT_WIDTH, fretboardH);
        }

        // Draw fret lines
        gc.setStroke(FRET_COLOR);
        gc.setLineWidth(1.0);
        for (int f = 1; f <= fretCount; f++) {
            final double x = fretboardLeft + (f + (minFret == 0 ? 1 : 0)) * fretSpacing;
            gc.strokeLine(x, fretboardTop, x, fretboardTop + fretboardH);
        }

        // Draw fret numbers
        gc.setFill(FRET_NUM_COLOR);
        gc.setFont(Font.font("System", 10));
        gc.setTextAlign(TextAlignment.CENTER);
        for (int f = 0; f <= fretCount; f++) {
            final int fretNum = minFret + f;
            if (fretNum == 0) continue; // no label for open
            final double x = fretCenterX(f, fretboardLeft, fretSpacing);
            gc.fillText(String.valueOf(fretNum), x, FRET_NUMBER_HEIGHT - 3);
        }

        // Draw fret markers (dots)
        for (int f = 0; f <= fretCount; f++) {
            final int fretNum = minFret + f;
            final double cx = fretCenterX(f, fretboardLeft, fretSpacing);
            final double dotR = Math.min(stringSpacing * 0.2, fretSpacing * 0.15);

            if (SINGLE_DOT_FRETS.contains(fretNum)) {
                gc.setFill(FRET_MARKER_COLOR);
                final double cy = fretboardTop + fretboardH / 2;
                gc.fillOval(cx - dotR, cy - dotR, dotR * 2, dotR * 2);
            } else if (DOUBLE_DOT_FRETS.contains(fretNum)) {
                gc.setFill(FRET_MARKER_COLOR);
                final double cy1 = fretboardTop + fretboardH * 0.3;
                final double cy2 = fretboardTop + fretboardH * 0.7;
                gc.fillOval(cx - dotR, cy1 - dotR, dotR * 2, dotR * 2);
                gc.fillOval(cx - dotR, cy2 - dotR, dotR * 2, dotR * 2);
            }
        }

        // Draw strings
        for (int s = 0; s < STRING_COUNT; s++) {
            final double y = fretboardTop + (s + 1) * stringSpacing;
            // Thicker strings for lower notes
            final double thickness = 0.5 + (s * 0.3);
            gc.setStroke(stringEnabled[s] ? STRING_COLOR : STRING_COLOR.deriveColor(0, 1, 1, 0.3));
            gc.setLineWidth(thickness);
            gc.strokeLine(fretboardLeft, y, w, y);

            // String name label
            gc.setFill(stringEnabled[s] ? LABEL_COLOR : LABEL_COLOR.deriveColor(0, 1, 1, 0.3));
            gc.setFont(Font.font("System", FontWeight.BOLD, 12));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText(STRING_NAMES[s], STRING_LABEL_WIDTH / 2, y + 4);
        }

        // Build active notes: collect all active MIDI notes with their track keys
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

        // Highlight fret positions for active notes
        if (!activeNotes.isEmpty()) {
            final double dotRadius = Math.min(stringSpacing * 0.35, fretSpacing * 0.3);

            for (int s = 0; s < STRING_COUNT; s++) {
                if (!stringEnabled[s]) continue;
                final double y = fretboardTop + (s + 1) * stringSpacing;

                for (int f = 0; f <= fretCount; f++) {
                    final int fretNum = minFret + f;
                    final int midiNote = TUNING[s] + fretNum;
                    final List<String> tracks = activeNotes.get(midiNote);
                    if (tracks == null || tracks.isEmpty()) continue;

                    final double cx = fretCenterX(f, fretboardLeft, fretSpacing);

                    // Draw colored circle — blend tracks as pie-like segments
                    if (tracks.size() == 1) {
                        gc.setFill(colorForTrack(tracks.getFirst()));
                        gc.fillOval(cx - dotRadius, y - dotRadius, dotRadius * 2, dotRadius * 2);
                    } else {
                        // Multiple tracks: draw segments
                        final double anglePerTrack = 360.0 / tracks.size();
                        for (int i = 0; i < tracks.size(); i++) {
                            gc.setFill(colorForTrack(tracks.get(i)));
                            gc.fillArc(cx - dotRadius, y - dotRadius,
                                    dotRadius * 2, dotRadius * 2,
                                    i * anglePerTrack, anglePerTrack,
                                    javafx.scene.shape.ArcType.ROUND);
                        }
                    }

                    // Fret number text inside dot
                    gc.setFill(Color.web("#1e1e2e"));
                    gc.setFont(Font.font("System", FontWeight.BOLD, Math.min(11, dotRadius * 1.2)));
                    gc.setTextAlign(TextAlignment.CENTER);
                    gc.fillText(String.valueOf(fretNum), cx, y + 4);
                }
            }
        }

        gc.setTextAlign(TextAlignment.LEFT); // reset
    }

    /** X center of a fret position (0 = open, 1 = 1st fret, etc. relative to minFret). */
    private double fretCenterX(final int relFret, final double left, final double spacing) {
        if (minFret == 0) {
            // Open position is at index 0, fret 1 at index 1, etc.
            if (relFret == 0) return left + spacing / 2; // center of open area
            return left + relFret * spacing + spacing / 2;
        }
        // No open position: fret positions start at index 0
        return left + relFret * spacing + spacing / 2;
    }
}
